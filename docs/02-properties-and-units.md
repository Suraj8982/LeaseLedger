# Milestone 2: properties and units

A manager can now record the buildings they manage and the rentable units inside each one. Two problems drive most of the design here: **who is allowed to touch which data**, and **how to query related data efficiently**.

## What exists after this milestone

All endpoints require a token whose role is `MANAGER`.

| Endpoint | What it does |
|---|---|
| `POST /api/properties` | Create a property |
| `GET /api/properties?page=0&size=20` | The caller's properties, with unit counts, one page at a time |
| `GET /api/properties/{id}` | One property |
| `PUT /api/properties/{id}` | Replace its name, address and city |
| `DELETE /api/properties/{id}` | Delete it (only when it has no units) |
| `POST /api/properties/{id}/units` | Add a unit |
| `GET /api/properties/{id}/units` | List its units |
| `PUT /api/properties/{id}/units/{unitId}` | Change a unit |
| `DELETE /api/properties/{id}/units/{unitId}` | Delete a unit |

## The data model

```
users (1) ──── (many) properties (1) ──── (many) units
        manager_id                  property_id
```

- A property belongs to exactly one manager.
- A unit belongs to exactly one property.
- A unit number is unique inside its property (`uq_units_property_number`), so two buildings can both have a "101".

## Authentication versus authorization

Milestone 1 answered "who are you?" (authentication). This milestone answers "are you allowed to do this?" (authorization), and it does so at two levels.

### Level 1: role check

`@PreAuthorize("hasRole('MANAGER')")` on the controller class. Spring runs this before the method. A tenant's token is valid, so they are authenticated, but they lack the role: **403**.

### Level 2: ownership check

A role check alone is not enough. Manager A and manager B both have the `MANAGER` role, and without more, B could read `GET /api/properties/7` even though property 7 is A's. This bug is common enough to have a name: **IDOR (insecure direct object reference)**, or broken object-level authorization. It is the first item in the OWASP API Security Top 10.

The fix here has three parts:

1. **The caller's id comes only from the token.** Controllers pass `caller.id()` into the service. No endpoint accepts a `managerId` in the URL or body.
2. **The lookup and the ownership check are one query.** `findByIdAndManagerId(propertyId, managerId)` returns nothing if the property is someone else's. There is no code path that loads a property first and "remembers" to check the owner afterwards.
3. **Units are reached through their property.** `UnitService` first calls `requireOwned`, then `findByIdAndPropertyId`. A unit id from another building does not resolve, even for the same manager.

### Why another manager's property returns 404 and not 403

403 would confirm "this id exists, it just isn't yours", which lets someone map out valid ids. 404 says the same thing for "not yours" and "does not exist".

`PropertyFlowTest.managerCannotSeeOrChangeAnotherManagersProperty` tries every endpoint as the wrong manager and then confirms nothing changed.

## The N+1 problem and how the list avoids it

The property list shows each property's total and vacant unit counts. The obvious implementation:

1. Query the properties: 1 query.
2. For each property, query its units and count them: N more queries.

With 20 properties per page that is 21 queries per request, and it grows with page size. This is the **N+1 problem**, and it is the most common performance bug in JPA applications.

`PropertyRepository.findSummariesByManagerId` asks the database to do the counting instead:

```sql
select p.id, p.name, p.city,
       count(u.id),
       sum(case when u.status = 'VACANT' then 1 else 0 end)
from properties p
left join units u on u.property_id = p.id
where p.manager_id = ?
group by p.id, p.name, p.city
order by p.name, p.id
```

- **LEFT JOIN**, so a property with zero units still appears (an inner join would drop it). The test includes an empty building for this reason.
- **GROUP BY** collapses the joined rows back to one row per property.
- The result goes straight into the `PropertySummary` record; no entities are loaded.

Other ways to solve N+1, for when you need the related entities themselves and not just counts: `JOIN FETCH`, `@EntityGraph`, or batch fetching.

## Other decisions

### Lazy loading on `Unit.property`

`@ManyToOne` is eager by default: every time a unit is loaded, Hibernate also loads its property. `FetchType.LAZY` loads the property only if the code touches it. Defaulting everything to lazy and fetching explicitly when needed is the standard advice.

### `Property.managerId` is a plain `Long`, not a `User` reference

The property module never needs the manager's name or email, only to compare ids. Keeping a plain id means the `property` package does not depend on the `user` package's entity, which is what makes this a *modular* monolith. The foreign key in the database still guarantees the manager exists.

### `BigDecimal` and `NUMERIC(12,2)` for rent

Floating-point numbers cannot represent most decimal fractions exactly (`0.1 + 0.2` is `0.30000000000000004` in a `double`). For money that produces rounding errors that add up. `BigDecimal` in Java and `NUMERIC` in SQL are exact. `@Digits(fraction = 2)` rejects `100.999` at the API boundary.

### Pagination

A list endpoint without a limit works in a demo and fails with real data. The API takes `page` and `size`, clamps `size` to at most 100 so a client cannot request a million rows, and returns `totalItems` and `totalPages` so the UI can draw page controls. The response is our own `PageResponse` record, not Spring's `Page`, so the JSON shape is ours to keep stable.

A fixed `order by p.name, p.id` matters: without a deterministic order, the database may return rows in a different order per query and items can repeat or vanish between pages. The `p.id` tiebreaker handles two properties with the same name.

### `@Transactional` and dirty checking

Service classes are `@Transactional`: each public method runs in one database transaction that commits if the method returns and rolls back if it throws. Inside a transaction, a loaded entity is "managed". `PropertyService.update` changes fields and never calls `save`; at commit, Hibernate compares the entity with its original state and issues the `UPDATE` itself. Read methods are marked `readOnly = true`, which skips that comparison.

### Rules that live in the service, not the controller

- A property with units cannot be deleted (409). Deleting it would either fail on the foreign key or silently remove units that may have lease history later.
- A manager cannot set a unit to `OCCUPIED` (400). Only a lease can do that, in milestone 3. Until then nothing can occupy a unit, which is why the "occupied" branches in `UnitService` are not exercised by a test yet.

### 400 versus 404 versus 409

- **400**: the request itself is wrong (negative rent, three decimal places).
- **404**: the thing is not there, or not yours.
- **409**: the request is fine but clashes with current data (duplicate unit number, deleting a property that has units).

## Known limitations

- Still verified only on H2 in PostgreSQL mode, not real PostgreSQL.
- No search or filtering on the property list yet.
- No optimistic locking: if two browser tabs edit the same unit, the last save wins silently. `@Version` is the fix and is planned where it matters most, on invoices.
- Deletes are permanent. Once leases exist, units with history should be archived instead.
