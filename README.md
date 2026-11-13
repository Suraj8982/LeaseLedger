# LeaseLedger

A rent and property management platform. Property managers list units, create leases and track rent; tenants see what they owe and raise maintenance requests.

**Status: in progress.** Milestones 1 and 2 of phase 1 are done. This README is updated as each milestone lands.

## Stack

Java 17, Spring Boot 3, Spring Security (JWT), Spring Data JPA, Flyway, PostgreSQL. React and AWS (S3, SQS, SNS, RDS) arrive in later milestones.

## Roadmap (phase 1)

- [x] 1. Project foundation, users, roles and JWT login
- [x] 2. Properties and units
- [ ] 3. Leases, rent schedules and invoices
- [ ] 4. Maintenance requests with photo upload (S3)
- [ ] 5. Rent reminders (SQS worker, email)
- [ ] 6. React app: manager dashboard and tenant portal
- [ ] 7. CI pipeline and AWS deployment

Phase 2 adds card payments for invoices.

## Run it

Requires Java 17 and Maven. No database install needed for the `local` profile (in-memory H2).

```bash
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

Then:

```bash
curl -X POST localhost:8080/api/auth/register -H "Content-Type: application/json" -d '{"email":"manager@example.com","password":"correct-horse","fullName":"Demo Manager","role":"MANAGER"}'
```

Use the returned `accessToken` as `Authorization: Bearer <token>` on `GET /api/users/me`.

To run against PostgreSQL instead, start `docker compose up -d`, set `LEASELEDGER_JWT_SECRET` to a Base64 key of at least 32 bytes, and run without the `local` profile.

## Tests

```bash
cd backend
mvn test
```

## Design notes

Each milestone has a write-up of what was built and why: see [docs/](docs/).
