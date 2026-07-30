# User Service

**Owner: Mukul** · port **8082** · owns `userdb` (PostgreSQL)

Student and faculty profiles, per-campus departments, and document records.
The login account itself lives in auth-service; this service holds the identity
information attached to it.

Complete through **Day 11** of the plan.

---

## Two rules that are easy to get wrong

**No `Semester` field anywhere in the UI**, even though a schema may have one.
Academic scheduling data is not needed for access control.

**Departments are campus data, not a fixed list.** Nothing here seeds department
names and nothing hardcodes them. A Campus Admin creates the list for their
campus after onboarding and this service serves it back. The dropdown is filled
from the API and allows no free-text entry (FR-PROF-9).

---

## Endpoints

All paths are under `/api/user`. Every one except `/ping` needs a bearer token
issued by auth-service, or — for `/internal/**` — the shared internal key.

### Departments — `/api/user/departments`

| Method | Path | Who |
|---|---|---|
| POST | `/` | Campus Admin, Super Admin |
| GET | `/`, `/{id}` | any signed-in user (a student needs the dropdown) |
| PUT | `/{id}` | Campus Admin, Super Admin |

### Student profiles — `/api/user/students`

| Method | Path | Who |
|---|---|---|
| POST | `/` | Campus Admin, Super Admin, Faculty |
| GET | `/me` | the signed-in student |
| GET | `/{id}`, `/by-user/{userId}` | self, or staff |
| GET | `/` (paged, `?departmentId=`), `/count` | Campus Admin, Super Admin, Faculty |
| PUT | `/{id}` | self, or staff |
| POST | `/{id}/photo` (multipart) | self, or staff — **pauses the pass** |
| GET | `/{id}/photo-url` | self, or staff |
| DELETE | `/{id}/photo` | self, or staff — **pauses the pass** |

### Faculty profiles — `/api/user/faculty`

Same shape. The **list is open to any signed-in user** — a visitor filling in a
request has to pick the host they are coming to see, so hiding it would make
visitor self-service impossible. Nothing sensitive is in the response.

### Documents — `/api/user/documents`

| Method | Path | Who |
|---|---|---|
| POST | `/` (multipart: `file`, `userId`, `docType`) | self, or staff |
| GET | `/{id}/url` | self, or staff — short-lived link |
| GET | `/me`, `/{id}`, `/user/{userId}` | self, or staff |
| GET | `/user/{userId}/pending` | Campus Admin, Super Admin |
| PATCH | `/{id}/verification` | Campus Admin, Super Admin |
| DELETE | `/{id}` | Campus Admin, Super Admin, unverified only |

### Internal — `/api/user/internal` (Day 8)

Service-to-service only. Guarded by `X-Internal-Api-Key`, never reachable from a
browser, and **read-only** — no service should be able to change another's data
through a shared key.

| Method | Path | Called by |
|---|---|---|
| GET | `/profiles/{userId}/summary` | gatepass when issuing a pass; guard for the scanner |
| POST | `/profiles/summaries` (`?withPhotoUrl=`) | the bulk engine — up to 1000 ids in one call |
| GET | `/profiles/{userId}/exists` | a caller that only needs yes or no |

**Day 10 — the batch lookup.** A bulk upload is up to a thousand rows.
Enriching each through the single endpoint is a thousand round trips for
something two queries answer. It is a POST because a thousand ids in a query
string is roughly 8 KB — Tomcat's default header limit — so a GET would fail on
exactly the large batches that matter. Accounts with no profile come back in
`missing` rather than as an error: the bulk engine creates lightweight VISITOR
identities for attendees nobody has seen before, so a mixed sheet is *supposed*
to return fewer than it asked about.

**Day 11 — the scanner photo.** `summary` returns a short-lived signed
`photoUrl` alongside `photoS3Key`. A key is not displayable, and guard-service
holds an internal API key rather than a user token, so it cannot reach the
JWT-guarded `/photo-url` endpoint — without this there is no path from a scan to
a face at all. It is on the summary rather than behind a second endpoint because
the scan happens with someone standing at a gate and the plan targets a result
in under two seconds.

Storage being unreachable degrades the face to `null`; it never fails the scan.
A scanner showing a name and no photo is degraded, one returning 500 because S3
was slow is broken — at a barrier, with a queue behind it.

There is deliberately **no name field**. A person's name lives in auth-service
and gatepass already copies it onto the pass as `holderName`. A second copy here
could disagree with the first, and the gate would have two answers to "who is
this".

**The summary shape is a fixed contract.** gatepass-service's
`InternalServiceClient` reads `userId`, `identifierCode` and `photoS3Key` out of
the `ApiResponse` envelope. Renaming or removing any of the three makes that
call return nulls and every printed pass silently loses its photo, with nothing
in a log to explain it. `ProfileLookupServiceTest` pins the three names down.

---

## Security model

`SecurityConfig`, `JwtAuthenticationFilter`, `JwtService`, `PerimityPrincipal`
and `CurrentUser` are the shared files auth-service publishes, copied into
`com.perimity.user.security`. Only the package line differs. **The JWT claim
names are a cross-service contract** — announce a change before making one.

Four paths stay public: `/api/user/ping`, `/swagger-ui.html`, `/swagger-ui/**`,
`/api-docs/**`. Leaving `/ping` locked is a real failure, not a nuisance —
Docker polls it as a healthcheck, gets 401, and restarts the container forever.

**`campusId` comes from the token, not from a request parameter.** Before Day 7
it was required on every read, which meant
`GET /api/user/departments?campusId=2` handed another campus's data to anyone
who typed a different number. It survives as *optional* for one reason: a Super
Admin has no campus of their own and must name one.

Three layers, because none can do another's job:

* `InternalApiKeyFilter` — is this one of our own six services? Runs first and
  skips itself on every non-internal path.
* `@PreAuthorize` — may this *kind* of user call this endpoint?
* `CurrentUser.requireSelfOrStaff` — whose record may they touch? Without it any
  student could read another student's profile by changing the number in the
  URL, and every role check would still pass.

A record on another campus returns **404, not 403**. A 403 confirms the row
exists, which is enough to count another campus's students by walking the IDs.

`JWT_SECRET` and `INTERNAL_API_KEY` must be identical across all six services.

---

## Storage (Day 9)

The `storage` package is copied from campus-service, whose own comment names it
the reference implementation for the other five services. One property picks the
implementation:

```
perimity.storage.type=local   a folder on disk — THE DEFAULT
perimity.storage.type=s3      the real thing
```

Local is the default so nobody needs an AWS account, a credential file or a
network connection to run this service. On Day 22 the property flips and no code
changes. It also keeps the demo safe: if AWS is unreachable on the day, the whole
system still runs from a laptop.

**Keys are generated here, never accepted from a client.** The old upload
endpoint took an `s3Key` in the request body, which let a caller name a path in
somebody else's folder — a student could register a row pointing at another
student's ID proof and then read it back through a perfectly legitimate
download. `StorageKeys` builds every key from the profile row just loaded, so an
upload can only land under that person's own prefix. `DocumentCreateDto` was
deleted rather than deprecated, because a DTO whose whole purpose is a
client-supplied path should not be one import away from being used again.

```
profiles/campus-{campusId}/students/{userId}/photo-{uuid}.jpg
profiles/campus-{campusId}/faculty/{userId}/photo-{uuid}.png
profiles/campus-{campusId}/users/{userId}/documents/{uuid}-{filename}
```

campus-service prefixes with the campus *code*, which reads better in a bucket.
This service cannot: the code lives in `campusdb` and reading it would make
every upload fail whenever campus-service restarts. `campusId` is already in
hand on the profile row.

**Reads are short-lived presigned URLs.** The bucket is private and stays
private. A permanent public link cannot be un-shared once it leaks, and what
leaks here is somebody's photograph or identity document. Links last 15 minutes
by default and are never persisted.

**The content type a browser sends is a claim, not a fact.** `UploadValidator`
checks the declared type first (cheap, catches honest mistakes) and then the
file's leading bytes. SVG is refused outright: it is XML, it can carry script,
and serving one from our own origin would be stored XSS. Photos cap at 2 MB,
documents at 5 MB.

---

## Things worth knowing before you change something

**Government IDs are returned masked.** `StudentProfileResponse` shows only the
last four characters. If the masking ever stops working nothing errors — twelve
real digits simply start appearing in every directory page.

**On update, `null` means "leave alone" and `""` means "clear".** Treating a
missing field as "set to null" would let a form that posts only an address wipe
the roll number and photo — and pause the holder's pass while doing it.

**Sensitive edits pause the pass** (SRS v1.1). Roll number, government ID,
employee ID and **photo**, including uploading, replacing or removing one. The
photo is what a guard checks a face against, so changing it leaves a QR
vouching for a different picture. Set `perimity.gatepass.base-url` to enable the
call — left empty, it is a logged no-op so this service still runs standalone.

**Photo upload order is deliberate**: validate, store the new object, save the
row, delete the old object, pause. Deleting first would be tidier and would
leave the person with no photo at all if the upload then failed.

**Nobody verifies their own document**, and `verifiedBy` is taken from the token
with the request body ignored. **A verified document cannot be deleted** — it is
the evidence somebody checked this person's identity.

**Cross-service references are by convention, not foreign keys.** `userId` and
`campusId` point at rows in auth-service and campus-service. This service never
reads their databases.

---

## Running it

```
mvn spring-boot:run
```

Then `http://localhost:8082/swagger-ui.html`. Click **Authorize** and paste a
token from `POST /api/auth/login` on auth-service (port 8081) — without one
every endpoint except `/ping` answers 401, which is correct, not broken.

Uploads land in `./storage-dev/`, which is gitignored. Tests use in-memory H2
and `./target/storage-test/`, so `mvn test` needs no container:

```
mvn test
```

---

## Not done yet

| Item | Day |
|---|---|
| Profile view/edit screen with the sensitive-field warning | 14 |
| Department management and account-to-profile linking screen | 15 |
| Student profile and document upload screens | 16 |
| `Dockerfile`, then uncomment this service's block in the root compose file | — |

**Day 10 note.** The plan assigns "per-row identity resolution by email" to this
service, but identity is keyed by email and email lives in `authdb`. Neither
profile table here has an email column, so user-service structurally cannot own
that lookup without breaking database-per-service. auth-service ships
`POST /api/internal/auth/users` (resolve-or-create, idempotent) and gatepass's
bulk engine calls it directly. What was left for this service was the batch
profile lookup above, so a 600-row batch is one call rather than 600.

**Known drift:** `docs/Perimity_Team_Guide.md` §4.2 lists these paths as
`/api/users/...` (plural). The code uses `/api/user/...` (singular), matching
`/api/user/ping` and the security permit list — and matching what
gatepass-service already calls. Someone should fix the guide before the frontend
is written against it.
