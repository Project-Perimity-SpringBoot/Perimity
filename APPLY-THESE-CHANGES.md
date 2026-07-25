# How to apply this update

Read this whole file before you start. It takes about 10 minutes.

**Delete this file after you finish.** It is instructions, not part of the project.

---

## What is in this ZIP

20 files. 14 replace existing files; 6 are new.

| File | Status |
|---|---|
| `README.md` | replaced |
| `docker-compose.yml` | replaced |
| `.env.example` | replaced |
| `docker/postgres/init-databases.sql` | **NEW** |
| `.github/PULL_REQUEST_TEMPLATE.md` | replaced |
| `.github/workflows/docker-build.yml` | replaced |
| `auth-service/README.md` | replaced |
| `user-service/README.md` | replaced |
| `gatepass-service/README.md` | replaced |
| `campus-service/README.md` | replaced |
| `guard-service/README.md` | replaced |
| `qr-service/README.md` | replaced |
| `frontend/README.md` | replaced |
| `docs/README.md` | replaced |
| `docs/Perimity_SRS_v1.1_Amendments.md` | **NEW** |
| `docs/Perimity_Team_Guide.md` | **NEW** — was `Perimity_SRS_v1.1.pdf` |
| `docs/Perimity_Database_Design.md` | **NEW** — replaces the `.pdf` |
| `docs/Perimity_Event_Bulk_Design.md` | **NEW** — replaces the `.pdf` |
| `docs/Perimity-Complete-Roadmap.md` | **NEW** — replaces the `.pdf` |

All four documents in `docs/` have been rewritten as markdown and updated to the
new structure: six roles, six services, role-based login, campus-agnostic, the
`PAUSED` pass state, campus config keys, and screens 17–20. Markdown is diffable
and reviewable in a PR; a PDF in Git is a binary blob nobody can review.

Nothing else is touched. Your `.gitignore`, your `docs/*.pdf` files, and any
code you have already written are left exactly as they are.

All six owner names (Omkar, Mukul, Tushar, Arham, Palash, Sanjay) and all
roadmap day references (Day 11, Day 13, Day 16, Day 23) are preserved.

---

## Step 1 — Make a branch first

Do not do this on `main`.

```bash
git checkout main
git pull
git checkout -b feature/infra-srs-v1.1-update
```

---

## Step 2 — Copy the files in

Unzip this archive and copy its contents over your repo root — the folder that
contains `docker-compose.yml`. Say yes when it asks to overwrite.

Your repo should now look like this:

```
Perimity-main/
├── docker-compose.yml
├── README.md
├── .env                  (yours, not committed)
├── .env.example
├── .gitignore
├── APPLY-THESE-CHANGES.md   ← delete this at the end
├── .github/
├── auth-service/
├── user-service/
├── gatepass-service/
├── campus-service/
├── guard-service/
├── qr-service/
├── frontend/
├── docs/
└── docker/               ← NEW
    └── postgres/         ← NEW
        └── init-databases.sql   ← NEW
```

**Check the new file actually landed:**

```bash
ls -l docker/postgres/init-databases.sql
```

You should see a file of roughly 900 bytes. If it says "No such file", the copy
did not work and nothing below will function.

---

## Step 3 — Create your .env

The new `docker-compose.yml` reads its values from a `.env` file. The old one had
them hardcoded. Without `.env`, Postgres will not start.

```bash
cp .env.example .env
```

Now open `.env` and change these four at minimum:

| Variable | Rule |
|---|---|
| `POSTGRES_PASSWORD` | anything you like |
| `JWT_SECRET` | at least 32 characters, same value for every service |
| `INTERNAL_API_KEY` | anything long and random |
| `QR_AES_KEY` | **exactly** 32 characters — not 31, not 33 |

If you change `POSTGRES_USER` away from `perimity`, you must also change the five
`GRANT` lines at the bottom of `docker/postgres/init-databases.sql` to match, or
Postgres will fail with "role does not exist".

---

## Step 4 — Reset and start

The database creation script runs **only once**, when the Postgres data volume is
empty. If you have ever run `docker compose up` before, the volume already exists
and the script will be skipped.

So reset it once. **This deletes your local database data** — that is fine right
now, because there is no real data yet.

```bash
docker compose down -v
docker compose up -d
```

Wait about 30 seconds for the health checks to pass.

---

## Step 5 — Verify it worked

This is the check that matters. Run:

```bash
docker exec -it perimity-postgres psql -U perimity -d postgres -c "\l"
```

You should see all five in the list:

```
authdb
campusdb
gatepassdb
qrdb
userdb
```

If you only see `postgres` and the template databases, the script did not run.
Go back to Step 2 and confirm the file path, then repeat Step 4.

Also check all four containers are healthy:

```bash
docker compose ps
```

Look for `(healthy)` next to `perimity-postgres`, `perimity-mongo`,
`perimity-rabbitmq`, and `perimity-redis`. Plain `Up` is not enough — the service
blocks in `docker-compose.yml` wait on the health status.

---

## Step 6 — Remove the superseded PDFs

All four documents in `docs/` are now markdown and are already in this ZIP. Delete
the old PDFs so nobody reads a stale one:

```bash
git rm docs/Perimity_SRS_v1.1.pdf
git rm docs/Perimity_Database_Design.pdf
git rm docs/Perimity_Event_Bulk_Design.pdf
git rm docs/Perimity-Complete-Roadmap.pdf
```

Keep `Perimity_SRS.pdf` — the 25-page formal spec — since that is a submission
artefact rather than a working document. If it is not in `docs/` yet, add it.

The old `Perimity_SRS_v1.1.pdf` was a 9-page internal team document, **older**
than and different from the formal SRS. Two files called "v1.1" would send six
people to two different specs. Its content now lives in
`docs/Perimity_Team_Guide.md`, which is what it always was.

If you need PDFs for submission, generate them from the markdown at the very end,
so the markdown stays the single source of truth.

## Step 7 — Make sure .env is not tracked

```bash
git status --short
```

If `.env` appears in that list, git is tracking it and your secrets will be
committed. Fix it:

```bash
git rm --cached .env
```

---

## Step 8 — Delete this file and commit

```bash
rm APPLY-THESE-CHANGES.md
git add -A
git commit -m "Apply SRS v1.1: six services, de-branding, Postgres init, CI guard rails"
git push -u origin feature/infra-srs-v1.1-update
```

Open a PR into `main` and get one review, per the branch workflow.

**Heads up on CI:** the workflow now has a `guard-rails` job that fails the build
if it finds an institution name anywhere in the repo outside `docs/`, or if a
`.env` file is committed. If your PR goes red, read the error — it is telling you
exactly which file has the problem.

---

## What everyone needs to know after this merges

Send this to the group. These are behaviour changes, not just file changes.

### 1. Login is no longer passwordless for everyone

The old README said "passwordless everywhere". That was wrong and contradicted
the SRS.

| Role | Password | OTP |
|---|---|---|
| Super Admin | yes | no |
| Campus Admin | yes | no |
| Guard | yes | no |
| Faculty | yes | yes — user chooses |
| Student | yes | yes — user chooses |
| Visitor | never | yes, only |

**Omkar** — the login page needs a password field plus a "log in with OTP
instead" option for Faculty and Student. This affects the shared shell, which
everyone is blocked on for Day 13.

### 2. No institution names anywhere

Perimity is campus-agnostic. No campus name, no department list, no email domain
in code, seed data, config, or UI text. Departments are created by a Campus Admin
after onboarding and served from the API. The CI job enforces this.

### 3. Four new screens

| # | Screen | Built by | Calls |
|---|---|---|---|
| 17 | Blocklist | Mukul | auth-service |
| 18 | Audit Log | Palash | auth-service |
| 19 | Campus Settings | Arham | campus-service |
| 20 | Super Admin Console | Sanjay | campus-service |

Screen 20 fills a real hole — Super Admin has been a role since SRS v1.0 but had
no screen.

### 4. New backend work per owner

Read your own service README — each one lists what changed. Summary:

- **Omkar (auth)** — blocklist, audit log, logout, forgot password, account
  lockout, first-login password change. Heaviest load.
- **Mukul (user)** — file upload limits and server-side type validation.
  Lightest load.
- **Tushar (gatepass)** — pass state machine with a new `PAUSED` state, plus a
  scheduled job that expires passes daily. Nothing does this today.
- **Arham (campus)** — campus policy config as key-value pairs. His
  `repeat_entry_result` key unblocks Palash's scanner.
- **Palash (guard)** — Redis cache for the sub-second scan target, and the amber
  result now driven by campus config.
- **Sanjay (qr)** — token invalidation on pass re-issue, bounded email retry.

### 5. Still true, do not change

- Entry only. No exit scan, no in/out toggle, ever.
- No `Semester` field in any UI form.
- Files to object storage, only the key in the database.
- bcrypt for passwords, SHA-256 for OTPs, AES-256 for QR tokens.
- No service reads another service's database — call its API.
