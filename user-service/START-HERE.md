# user-service — START HERE (read before you commit)

Hi Mukul. This is **scaffolding, not a finished service.** Tushar had Claude
build the Day 1 + Day 2 layer for you so you're not starting from an empty
folder — but the real work (profile endpoints, S3 upload, bulk import hooks) is
still yours to write. Read this whole file before you push anything.

## What's already here (compiled + tested — a Department, StudentProfile and
## Document were persisted against an in-memory DB and all four repositories
## returned rows before this was handed over)

- `pom.xml` — Spring Boot **3.3.5** (pinned on purpose — do not let STS bump it),
  Java 17, web + JPA + validation + Postgres + springdoc. Security/S3/RabbitMQ
  deps are present but **commented out** — adding them now breaks ping + Swagger.
- `application.properties` — port **8082**, DB **userdb** on 5433. Password comes
  from `SPRING_DATASOURCE_PASSWORD` env var, never hard-coded. Fallbacks after the
  colon are for IDE-only local runs.
- `UserServiceApplication.java` — sets `Asia/Kolkata` before Spring starts.
- `dto/ApiResponse.java` — the shared response shape. Same in all six services.
- `controller/PingController.java` — `GET /api/user/ping`.
- `validation/ValidationPatterns.java` — campus-agnostic regexes. **Rule: regex
  validates SHAPE, the service layer validates MEANING** (does this dept exist,
  is this roll number taken — those are DB checks, not regex).
- `exception/GlobalExceptionHandler.java` — renders every validation failure as
  `ApiResponse`, maps a DB unique-violation to 409.
- **enums:** `ProfileType`, `DocumentType`.
- **entities:** `Department`, `StudentProfile`, `FacultyProfile`, `Document`.
- **repositories:** one per entity, with the lookups you'll actually need.

## Three things the design docs FORCE — do not "fix" them

1. **No `semester` field. Anywhere.** The SRS is explicit: semester is not needed
   for access control and must never appear in any form or entity. If you feel the
   urge to add it, don't.
2. **Departments are per-campus seeded data**, never a hard-coded list. `Department`
   carries a `campus_id`; two campuses can have totally different departments.
   Never write an enum of department names.
3. **Files live on S3, only the key lives in the DB.** `photoS3Key`, `Document.s3Key`
   — these are strings, never the bytes. The `OBJECT_KEY` regex blocks `..` path
   traversal; leave it in.

## Cross-service rule

`userId` and `campusId` are **references by convention**, not database foreign keys.
user-service never reads auth-service's or campus-service's database directly — you
call their APIs. Database-per-service is strictly enforced and the reviewer will
check for it.

## Your first real steps (Day 3+)

1. Boot it: `mvn spring-boot:run`, then open `http://localhost:8082/swagger-ui.html`
   and hit `/api/user/ping`. Confirm the four tables appear in `userdb`.
2. Write the profile DTOs (with `@Valid` + the ValidationPatterns regexes) and the
   `POST /api/user/students` / `POST /api/user/faculty` endpoints.
3. Wire S3 for photo upload — uncomment the S3 dep, store only the returned key.

## Before you commit

- Branch: `feature/user-entities`.
- Delete this file and the README stub once you've read them.
- `mvn -q compile` must pass.
- No institution name anywhere (CI guard-rail fails the build otherwise).
