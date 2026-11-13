# Milestone 1: project foundation, users and login

Read this with the code open. After it you should be able to explain every file in `backend/` without notes.

## What exists after this milestone

A Spring Boot API where a person can register as a manager or tenant, log in, receive a token, and use that token to call a protected endpoint.

| Endpoint | Who can call it | What it does |
|---|---|---|
| `POST /api/auth/register` | Anyone | Creates an account, returns a token (201) |
| `POST /api/auth/login` | Anyone | Checks the password, returns a token (200) |
| `GET /api/users/me` | Logged-in users | Returns the caller's own profile |
| `GET /actuator/health` | Anyone | Says whether the app is up (used later by AWS) |

## Package layout

Packages are grouped by feature, not by layer. Everything about login is in `auth`, everything about users is in `user`. In later milestones `property`, `lease` and `invoice` are added the same way.

```
com.leaseledger
├── auth      AuthController, AuthService, request/response records
├── user      User entity, Role enum, UserRepository, UserController
├── security  JwtService, JwtAuthFilter, AuthenticatedUser
├── config    SecurityConfig
└── common    ApiExceptionHandler
```

Why by feature: when you change how leases work you touch one package. With `controller/`, `service/`, `repository/` packages, every change touches all of them.

## The life of a request

### Login: `POST /api/auth/login`

1. The request enters Spring Security's filter chain. `JwtAuthFilter` sees no `Authorization` header and does nothing.
2. `SecurityConfig` says `/api/auth/**` is `permitAll`, so the request reaches `AuthController.login`.
3. `@Valid` checks the body against the annotations on `LoginRequest`. A bad body never reaches the service; it becomes a 400.
4. `AuthService.login` lowercases the email, loads the user, and asks BCrypt whether the password matches the stored hash.
5. On a match, `JwtService.issue` builds a token containing the user id and role, signs it, and it is returned.

### A protected call: `GET /api/users/me` with `Authorization: Bearer <token>`

1. `JwtAuthFilter` takes the token from the header and calls `JwtService.verify`.
2. `verify` recomputes the signature with the server's secret and checks the expiry. If either is wrong it throws.
3. On success the filter puts an `AuthenticatedUser(id, role)` into the `SecurityContext`. From here on, Spring knows who the caller is.
4. `SecurityConfig` says every other URL needs authentication. The context is filled, so the request passes.
5. `UserController` receives the caller through `@AuthenticationPrincipal` and loads their profile.

Without a token, or with a bad one, step 3 never happens, step 4 fails, and the response is 401.

## Decisions and the reasons for them

### Why JWT instead of server-side sessions

With sessions the server remembers each logged-in user, so every request must reach a server that has that memory (or a shared store such as Redis). With a JWT the token itself carries the user id and role, and any instance can verify it using only the secret. That lets the API run as several identical instances behind a load balancer on AWS.

The cost: a JWT cannot be cancelled before it expires. If a token is stolen it works until expiry. That is why the lifetime is short (60 minutes). A refresh-token flow or a revocation list is the usual next step and is not built yet.

### How a JWT is protected

A JWT has three parts separated by dots: header, payload, signature. The header and payload are only Base64-encoded, so **anyone can read them; never put secrets in a token**. The signature is HMAC-SHA256 over header and payload using the server's secret. If an attacker edits the payload (say, `TENANT` to `ADMIN`), the signature no longer matches and `verify` rejects it. `JwtServiceTest.tokenWithEditedPayloadIsRejected` proves exactly this.

HMAC is a symmetric algorithm: the same secret signs and verifies. That fits a single backend. If several separate services had to verify tokens, an asymmetric algorithm (RS256) would be better, because they would only need the public key.

### Why the filter does not query the database

The filter trusts the id and role inside the verified token. That keeps authentication to zero database queries per request. The trade-off: if a user's role changes, their old token keeps the old role until it expires.

### Why BCrypt for passwords

Passwords are never stored, only a hash. BCrypt is used rather than SHA-256 for two reasons. It is deliberately slow, so guessing billions of passwords against a stolen database is impractical. And it generates a random salt per password and stores it inside the hash, so two users with the same password get different hashes and precomputed tables are useless.

### Why CSRF protection is disabled

A CSRF attack works because browsers attach cookies automatically, so a malicious site can make your browser send an authenticated request. This API uses no cookies. The token is added to a header by our own JavaScript, which a foreign site cannot do. So there is nothing for CSRF to exploit.

### 401 versus 403

401 means "I don't know who you are" (no token or a bad one). 403 means "I know who you are and you may not do this" (a tenant calling a manager-only endpoint). Spring Security's default for an anonymous request here would be 403, so `SecurityConfig` sets an entry point that returns 401.

### Why duplicate emails are caught by the database, not by a check

The obvious code is "if the email exists, reject; otherwise insert". Two requests arriving together can both pass the check and both insert. The unique constraint `uq_users_email` cannot be raced, so `AuthService.register` simply inserts and translates the constraint violation into a 409. `saveAndFlush` is used so the insert happens inside the `try` block rather than later.

### Why login gives the same error for a wrong email and a wrong password

If the errors differed, an attacker could submit a list of emails and learn which ones have accounts.

### Why Flyway, with `ddl-auto: validate`

Hibernate can create tables from entities, but then nobody controls or reviews what changes in production. With Flyway, every schema change is a numbered SQL file (`V1__create_users.sql`) that runs exactly once per database and is recorded in the `flyway_schema_history` table. `validate` makes the app fail at startup if an entity and the schema disagree.

### Why DTO records instead of returning the entity

Returning `User` as JSON would include `passwordHash`. Separate records (`RegisterRequest`, `AuthResponse`, `UserResponse`) mean the API shape is chosen deliberately and can change independently of the table. The test asserts `passwordHash` is absent from `/api/users/me`.

### Why `JwtAuthFilter` is not a `@Component`

Spring Boot automatically registers every `Filter` bean with the servlet container. The filter would then run twice: once there and once inside the security chain. Creating it with `new` in `SecurityConfig` avoids that.

### Why constructor injection everywhere

Dependencies are `final` and visible in the constructor. A class cannot exist half-initialised, and tests can build it with `new` (see `JwtServiceTest`, which uses no Spring at all).

## The two kinds of test

- `JwtServiceTest` is a **unit test**: one class, no Spring, a fixed clock so expiry can be tested without waiting. It runs in milliseconds.
- `AuthFlowTest` is an **integration test**: it starts the whole application with an in-memory database and sends real HTTP requests through the security filters. It proves the pieces work together.

## Limits you should state honestly if asked

- Tests and the `local` profile use H2 in PostgreSQL mode, not real PostgreSQL. The default profile targets PostgreSQL, but it has not been run yet because Docker is not installed on this machine.
- There is no refresh token, no logout, no rate limiting on login and no email verification yet.
- Anyone can register as a manager. In a real product, managers would be approved or invited.

## Questions to practise answering aloud

1. Walk me through what happens when a request with a token hits your API.
2. Someone steals a token. What can they do, and for how long? How would you reduce that?
3. Can a tenant edit their token to become an admin? Why not?
4. Why BCrypt and not SHA-256?
5. Two people register with the same email at the same millisecond. What happens?
6. Why did you disable CSRF? When would that be wrong?
7. How does your schema get from your laptop to production?
8. What is the difference between your two test classes, and why have both?
