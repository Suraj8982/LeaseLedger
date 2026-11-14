# LeaseLedger

A rent and property management platform. Property managers list units, create leases and track rent; tenants see what they owe and raise maintenance requests.

**Status: in progress.** Milestones 1 to 4 of phase 1 are done. This README is updated as each milestone lands.

## Stack

Java 17, Spring Boot 3, Spring Security (JWT), Spring Data JPA, Flyway, PostgreSQL, AWS S3 (SDK v2, presigned URLs), Docker, Testcontainers. React and AWS SQS, SNS and RDS arrive in later milestones.

## Roadmap (phase 1)

- [x] 1. Project foundation, users, roles and JWT login
- [x] 2. Properties and units
- [x] 3. Leases, rent schedules and invoices
- [x] 4. Maintenance requests with photo upload (S3)
- [ ] 5. Rent reminders (SQS worker, email)
- [ ] 6. React app: manager dashboard and tenant portal
- [ ] 7. CI pipeline and AWS deployment

Phase 2 adds card payments for invoices.

## Run it

Requires Java 17 and Maven. Docker is optional for a first look.

**Quickest: no Docker.** Uses an in-memory database; everything works except photo upload.

```bash
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

**With photo upload.** Start the local S3 emulator (and PostgreSQL) first, then run the same command:

```bash
docker compose up -d
```

**Against PostgreSQL.** With the containers running, set `LEASELEDGER_JWT_SECRET` to a Base64 key of at least 32 bytes and `LEASELEDGER_S3_ENDPOINT` to `http://localhost:4566`, then run without the `local` profile.

Try it:

```bash
curl -X POST localhost:8080/api/auth/register -H "Content-Type: application/json" -d '{"email":"manager@example.com","password":"correct-horse","fullName":"Demo Manager","role":"MANAGER"}'
```

Use the returned `accessToken` as `Authorization: Bearer <token>` on `GET /api/users/me`.

## Tests

```bash
cd backend
mvn test
```

The S3 integration test starts an AWS emulator in Docker and is skipped automatically when Docker is not running.

## Design notes

Each milestone has a write-up of what was built and why: see [docs/](docs/).
