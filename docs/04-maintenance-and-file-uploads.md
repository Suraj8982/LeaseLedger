# Milestone 4: maintenance requests and photo upload to S3

A tenant can report a problem in the unit they rent and attach photos; the manager works the request through to resolution. Two topics carry this milestone: **uploading files to S3 without sending them through the server**, and **modelling a workflow as a state machine**. It is also the milestone where the project first ran on real PostgreSQL in Docker, which exposed a bug the in-memory database had hidden.

## What exists after this milestone

| Endpoint | Who | What it does |
|---|---|---|
| `POST /api/maintenance-requests` | Tenant | Report a problem on one of their active leases |
| `GET /api/maintenance-requests?status=OPEN` | Manager or tenant | Their requests, optionally filtered by status |
| `GET /api/maintenance-requests/{id}` | Manager or tenant | One request with its photos and links to view them |
| `POST /api/maintenance-requests/{id}/status` | Manager or tenant | Move the request to a new status |
| `POST /api/maintenance-requests/{id}/photos/upload-url` | Manager or tenant | Step 1 of an upload: get a URL to upload to |
| `POST /api/maintenance-requests/{id}/photos` | Manager or tenant | Step 3 of an upload: record the uploaded file |

## Uploading with presigned URLs

### The obvious design and why it is not used

The obvious design is: the browser sends the image to the API, and the API forwards it to S3. That makes every photo travel twice and pass through the application server, which then needs the memory, bandwidth and request time to handle 5 MB bodies. A few simultaneous uploads can starve ordinary API requests.

### What is built instead

```
Browser                         API                          S3
   │  1. POST .../photos/upload-url │                          │
   │ ─────────────────────────────► │  checks permissions,     │
   │ ◄───────────────────────────── │  signs a URL (no call    │
   │     { objectKey, uploadUrl }   │  to S3 needed)           │
   │                                                           │
   │  2. PUT the file to uploadUrl                             │
   │ ────────────────────────────────────────────────────────► │
   │                                                           │
   │  3. POST .../photos { objectKey }                         │
   │ ─────────────────────────────► │  HEAD object: exists?    │
   │                                │ ───────────────────────► │
   │ ◄───────────────────────────── │  size ok? then save row  │
```

The image bytes go from the browser straight to S3. The API only handles small JSON messages.

### What a presigned URL is

The bucket is private; normally only requests signed with AWS credentials are accepted. A presigned URL is an ordinary URL with a signature in its query string. The server computes it locally from its own credentials (`S3Presigner` makes no network call). The signature covers the bucket, the key, the HTTP method, the `Content-Type` and an expiry time. Whoever holds the URL can perform **that one operation on that one key until it expires** (10 minutes here), without having any credentials. Change the key or the content type and the signature no longer matches.

### Why step 3 exists: never trust the client

After step 2 the server has no idea whether an upload happened. `MaintenancePhotoService.attach` therefore checks three things itself before writing a database row:

1. **The key was issued for this request.** Keys look like `maintenance/{requestId}/{uuid}.jpg`. A key with any other prefix is refused, so a user cannot attach an arbitrary object from the bucket, or a file uploaded for a different request.
2. **The file exists.** The server sends S3 a `HEAD` request, which returns size and type without downloading the content. No file, no row.
3. **The file is not too large.** A presigned PUT URL cannot enforce a size limit by itself, so the 5 MB limit is checked here and an oversized object is deleted.

### Other security decisions

- **The server generates the object key** from a random UUID. The user's file name is never used, so it cannot contain `../`, overwrite another file, or be guessed.
- **Only JPEG, PNG and WebP** are accepted. Notably not SVG or HTML, which can carry scripts.
- **Viewing uses presigned GET URLs** generated fresh on each response and valid for 10 minutes. The bucket never needs to be public.
- **No AWS keys in the project.** `StorageConfig` uses the default credentials chain. On EC2 that resolves to the instance's IAM role, so there is nothing to leak in code, config or Git.
- **CORS.** A browser will not let a page on one origin PUT to another origin unless the target allows it, so the bucket has a CORS rule for the frontend's origin. Locally `localstack/create-bucket.sh` sets it.

## The storage interface

Business code depends on the `FileStorage` interface (four methods), not on the AWS SDK. That gives two implementations:

- `S3FileStorage`: the real one, about 50 lines.
- `FakeFileStorage` (test code): an in-memory map, used by the API flow tests so they are fast and need no network.

The real class is tested separately in `S3FileStorageTest`, which uses **Testcontainers** to start LocalStack (an AWS emulator) in Docker, uploads through a presigned URL with a plain HTTP client exactly as a browser would, and reads the file back. So the fake covers the business rules and the container test covers the AWS integration.

This is the dependency inversion principle in practice: the high-level code (maintenance) defines what it needs, and the low-level detail (S3) plugs in.

## The workflow as a state machine

```
OPEN ──────► IN_PROGRESS ──────► RESOLVED
  │ └──────────────────────────────▲
  └────────► CANCELLED
```

- The allowed moves are defined once, in `MaintenanceStatus.canMoveTo`. `RESOLVED` and `CANCELLED` are final.
- `MaintenanceRequest.moveTo` is the only way to change the status, so no code path can put a request into a state the workflow forbids.
- Changing status asks two separate questions, with two different answers:
  - **May this role set this status?** A manager may set `IN_PROGRESS` and `RESOLVED`; a tenant may only set `CANCELLED`. If not: **403**.
  - **Does the workflow allow it from the current status?** A tenant cancelling after work has started: **409**.

## Trusting the lease, not the request body

To create a request the tenant sends a `leaseId`. The service loads that lease only if it belongs to the caller and is active, and takes the unit and the manager from it. The body cannot name a unit or a manager, so a tenant cannot file a request against a unit they do not rent.

## What running on real PostgreSQL found

Until now everything ran on H2, an in-memory database in PostgreSQL-compatibility mode. With Docker available, the whole suite was run against a PostgreSQL 16 container. Results:

- **A real bug.** The application could not connect at all. The PostgreSQL driver sends the JVM's time zone name when it connects; Windows reports India as `Asia/Calcutta`, a legacy name that the PostgreSQL image rejects. H2 never looks at this. The fix is to run the JVM in UTC (`LeaseLedgerApplication.main`), which is also the correct practice for a server: timestamps mean the same thing wherever the code runs. Business dates already use the separately configured Dubai clock.
- **A test weakness.** Three login tests used fixed email addresses. They passed on H2, which starts empty every time, and failed on the second run against a database that keeps its data. They now use unique addresses.
- **Confirmation.** The row lock, the 8-thread concurrency test, the optimistic version check, the grouped query and all migrations behave the same on PostgreSQL.

The lesson: an in-memory substitute is convenient but is not the real database, and the differences show up exactly where you are not looking.

## Running it locally

`docker compose up -d` now starts two containers: PostgreSQL and LocalStack. LocalStack listens on `http://localhost:4566`, and its startup script creates the bucket. The `local` profile points the app at it.

## Known limitations

- **Never run against real AWS.** All S3 behaviour is verified against the emulator only.
- **Bucket privacy is not verified.** The emulator serves unsigned requests, so "objects cannot be read without a signed URL" can only be confirmed on a real private bucket.
- **The content type is whatever the uploader declared.** The server does not inspect the bytes to confirm the file is really an image.
- **Abandoned uploads are not cleaned up.** A file uploaded but never attached stays in the bucket. The standard fix is an S3 lifecycle rule that expires unattached objects.
- **Photos cannot be removed** once attached, and there is no virus scanning.
- **The tenant is not notified** when the status changes. That is milestone 5.
