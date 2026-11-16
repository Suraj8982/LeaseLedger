# Milestone 3: leases, rent schedules and invoices

This is the core of the product. A manager leases a vacant unit to a tenant; the system works out the payment schedule and creates an invoice for each payment; payments are recorded against invoices; a lease can be ended early. It is also where the hardest parts of the project are: **transactions, concurrency, locking and money arithmetic**.

## What exists after this milestone

| Endpoint | Who | What it does |
|---|---|---|
| `POST /api/leases` | Manager | Creates a lease, occupies the unit, generates all invoices |
| `GET /api/leases` | Manager or tenant | Manager: leases they manage. Tenant: their own |
| `GET /api/leases/{id}` | Manager or tenant | One lease with its invoices |
| `POST /api/leases/{id}/terminate` | Manager | Ends the lease early |
| `GET /api/invoices?filter=OVERDUE` | Manager or tenant | Invoices, filtered by `ALL`, `PENDING`, `OVERDUE`, `PAID` or `CANCELLED` |
| `POST /api/invoices/{id}/mark-paid` | Manager | Records a payment received by cheque, cash or transfer |

A create request:

```json
{
  "unitId": 1,
  "tenantEmail": "tenant@example.com",
  "startDate": "2027-01-01",
  "installments": 4,
  "securityDeposit": "5000.00"
}
```

`termMonths` defaults to 12 and `totalRent` defaults to the unit's monthly rent times the term. In the UAE annual rent is traditionally paid in 1, 2, 4, 6 or 12 cheques; `installments` is that number.

## The data model

```
units (1) ──── (many) leases (1) ──── (many) invoices
users (1) ──── (many) leases          (tenant_id, and manager_id)
```

- The **agreed rent is stored on the lease** (`total_rent`), not looked up from the unit. If the manager raises the unit's asking rent next year, existing tenants' leases must not change.
- `manager_id` is copied onto the lease. It could be reached through unit → property, but every "my leases" and "my invoices" query needs it, so storing it avoids two joins on the hottest queries. It is safe to copy because a property's manager never changes.

## One transaction for the whole lease

`LeaseService.create` does three writes: insert the lease, set the unit to `OCCUPIED`, insert up to 61 invoices. The class is `@Transactional`, so they are one database transaction: **all of it is saved or none of it is**. If the tenth invoice failed, there would be no lease, no occupied unit and no first nine invoices.

This is the A (atomicity) in ACID, and it is the main reason this project is one service. Split across a lease service and a billing service, this would need a saga with compensation steps to undo partial work.

The last assertion of `LeaseFlowTest.invalidLeaseRequestsAreRejected` checks this: after several failed attempts, the unit is still free.

## Two requests for the same unit at once: pessimistic locking

The rule is "a unit has at most one active lease". The naive code is:

1. Read the unit. Is it `VACANT`?
2. If yes, create the lease and set it to `OCCUPIED`.

This is a **check-then-act race**. Two requests can both do step 1 before either does step 2, both see `VACANT`, and both create a lease.

The fix is in `UnitRepository.findByIdForUpdate`, annotated `@Lock(PESSIMISTIC_WRITE)`. It makes Hibernate run `SELECT ... FOR UPDATE`, which locks the unit's row until the transaction ends. The second request's `SELECT` waits at that line. When the first transaction commits, the second one proceeds, reads the row as it is now (`OCCUPIED`), and returns 409.

**This is proven, not assumed.** `LeaseConcurrencyTest` fires 8 lease requests for one unit at the same instant from 8 threads and asserts exactly one 201 and seven 409s. With the `@Lock` line removed, the same test fails with all 8 requests succeeding: 8 active leases on one apartment. I ran that experiment before committing the lock.

## Paying an invoice twice: optimistic locking

A different concurrency problem with a different solution. Two requests (a double-click, or two staff members) both mark the same invoice paid.

`Invoice` has a `@Version` field. Hibernate adds the version to every update:

```sql
update invoices set status = 'PAID', version = 4 where id = 17 and version = 3
```

Both requests loaded version 3. The first update succeeds and makes it version 4. The second matches zero rows; Hibernate throws `ObjectOptimisticLockingFailureException`, which `ApiExceptionHandler` turns into a 409.

In the ordinary one-after-another case, the status check in `InvoiceService.markPaid` returns 409 first. The version is the backstop for the truly simultaneous case.

### Pessimistic versus optimistic: when to use which

| | Pessimistic (`FOR UPDATE`) | Optimistic (`@Version`) |
|---|---|---|
| How | Lock the row first; others wait | Don't lock; detect a clash at write time |
| Cost | Holds a database lock; waiting requests queue | No locks; the loser must retry or fail |
| Good when | Clashes are likely, or the loser must not do wasted work | Clashes are rare |
| Used here for | Creating a lease on a unit | Updating an invoice |

A third option, the strongest, is a database constraint. PostgreSQL can enforce "one active lease per unit" with a partial unique index (`UNIQUE (unit_id) WHERE status = 'ACTIVE'`). It is not used yet because the local H2 database does not support partial indexes; it is worth adding once the project runs on real PostgreSQL.

## Splitting rent without losing a fil

`RentSchedule.generate` is a pure function: no Spring, no database. Two subtle rules live there.

**Rounding.** 100,000 over 6 installments is 16,666.666… Rounding each to the nearest fil gives 6 × 16,666.67 = 100,000.02: the tenant is billed 2 fils that nobody agreed to. The method rounds each share **down** and adds the leftover to the last installment: 5 × 16,666.66 + 16,666.70 = 100,000.00 exactly. A parameterized test checks that the invoices always sum to the total across awkward amounts.

**Month ends.** A lease starting 31 January: adding one month gives 28 February. If the next date were computed from 28 February, every later date would be the 28th. Each due date is computed from the original start date, so March goes back to the 31st.

## "Overdue" is not stored

There is no `OVERDUE` status. An invoice is overdue when it is `PENDING` and its due date is before today. That is **derived state**: it depends on today's date, so storing it would require a nightly job to update it, and it would be wrong whenever the job failed or ran late. It is computed in the query (`status = 'PENDING' AND due_date < ?`, served by the `ix_invoices_status_due` index) and in `Invoice.isOverdue`.

## What "today" means

`LocalDate.now()` uses the server's time zone, which on AWS is usually UTC. At 01:00 in Dubai it is still the previous day in UTC, so an invoice due "yesterday" would not show as overdue for four hours. `TimeConfig` defines a `Clock` in the business time zone (`Asia/Dubai`, configurable), and every rule that needs today's date asks that clock.

## Reading related data: JOIN FETCH

A lease response includes the unit number, property name and tenant name, from three other tables. Those associations are lazy, so reading them for a list of 20 leases would trigger up to 60 extra queries. `LeaseRepository` uses `JOIN FETCH` to load them in the same `SELECT`. Milestone 2 solved N+1 for *counts* with `GROUP BY`; this is the solution when you need the related *rows*.

## Dynamic filtering: Specifications

The invoice list varies by who is asking (manager or tenant) and by filter (five values): ten combinations. `InvoiceService.visibleTo` builds the `WHERE` clause from parts with a JPA `Specification`, adding only the conditions that apply. The ownership condition is always added first, so no filter can widen access.

## One endpoint, different data per role

`GET /api/leases` and `GET /api/invoices` serve both roles. The service picks the query from the caller's role in the token. The tests check that a different tenant and a different manager both see zero items and get 404 on the lease.

## Terminating a lease

- Status becomes `TERMINATED`; the lease row is kept. It is `POST .../terminate`, not `DELETE`, because nothing is removed.
- Invoices due **after** today are cancelled. Invoices already due stay `PENDING`: the tenant lived there and still owes them.
- The unit becomes `VACANT` and can be leased again.
- A unit that has ever had a lease cannot be deleted (409); the foreign key protects the history.

## Known limitations

- A lease that reaches its end date is not closed automatically, so its unit stays occupied. That needs a scheduled job, planned with the reminder worker in milestone 5.
- Paying is manual (`mark-paid`). There are no partial payments and no way to undo a payment recorded by mistake. Phase 2 adds card payments.
- Terminating does not handle the security deposit refund or pro-rata rent for a part-used period.
- Overlapping future leases are not possible: a unit must be vacant now to be leased, even if the new lease would start after the current one ends.
- The locking has been exercised on H2 only. `SELECT ... FOR UPDATE` is standard and behaves the same way on PostgreSQL, but that has not been run here yet.
