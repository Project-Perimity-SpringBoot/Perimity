# Testing Super Admin and Campus Admin — local run

Owner of this pass: Palash. Screens belong to Arham (Phase 2).

Record every row as **works** / **degrades as documented** / **broken**. "Broken"
means it does something other than what the screen says it will — a blocked
feature that says so on screen is working correctly.

---

## 0. Bring it up

```bash
cd "C:\Users\Palash shende\Perimity"
docker compose up -d --build
./check-services.sh
```

Do not start until `check-services.sh` reports **6 of 6 services reachable**.
Testing against a half-up stack produces findings that are about the stack.

```bash
cd frontend
npm install
npm run dev
```

### Getting an account — read this before hunting for credentials

There is **no seed data**. `docker/postgres/init-databases.sql` creates five empty
databases and nothing else. The only account that exists is the one
`BootstrapSuperAdmin` creates on auth-service startup, from `SUPER_ADMIN_EMAIL`
and `SUPER_ADMIN_PASSWORD` in the repo-root `.env`.

It runs only when **no** Super Admin exists, so it is safe to restart, and it
never overwrites an account.

If you cannot sign in, check auth-service's log first:

```bash
docker compose logs auth-service | grep -i "super admin"
```

Three messages are possible and each means something different:

| Log line | Meaning |
|---|---|
| `Created the first Super Admin: ...` | Working. Use the `.env` credentials. |
| `No SUPER_ADMIN_EMAIL / SUPER_ADMIN_PASSWORD in .env - skipping` | The vars did not reach the container. |
| `already belongs to a non-admin account` | Someone registered as a visitor on that address. Change the address in `.env`. |

**Expect a forced password change on first sign-in.** The bootstrap sets
`mustChangePassword`, so the seeded credential is dead on first use. That is
correct behaviour and is itself the first test.

---

## 1. Super Admin — `/platform/*`

Sign in with the `.env` credentials.

| # | Check | Expected |
|---|---|---|
| 1.1 | First sign-in | Forced to `/change-password`. You cannot reach `/platform` until the password is changed. |
| 1.2 | After change | Lands on `/platform`, sidebar shows only Platform overview, Campuses, Campus admins. |
| 1.3 | `/platform` overview | Renders. Some counts may be blank — see B5 below. |
| 1.4 | `/platform/campuses` | Empty state on a fresh database, not an error and not a spinner. |
| 1.5 | **Create a campus** | Succeeds. This is the single most important step — nothing else in the platform can be tested until one campus exists. |
| 1.6 | Edit that campus | Changes persist after a reload. |
| 1.7 | Suspend / reactivate | Status changes and is reflected in the list. |
| 1.8 | **Create the first Campus Admin** for that campus | Succeeds, and note the email and temporary password — you need them for section 2. |
| 1.9 | `/platform/admins` | May be empty or restricted. See B5. |
| 1.10 | Try `/admin` in the URL bar | Should land on `/forbidden`, **not** `/login`. Sending a signed-in user to a sign-in form is the bug that makes an app feel broken. |
| 1.11 | Try `/guard` in the URL bar | `/forbidden`. An admin must never reach the scanner. |

### Known: B5 — a Super Admin has no campus

`campusId` is `null` for SUPER_ADMIN, by design. Any endpoint that calls
`CurrentUser.campusId()` therefore returns 403 for them. Blank counts or an
empty admin roster on `/platform` are **expected**, not bugs — as long as the
screen says so rather than showing a spinner or an error toast.

Flag it as broken only if it hangs, throws, or silently shows zero as though it
were a real number.

---

## 2. Campus Admin — `/admin/*`

Sign out. Sign in as the Campus Admin created in 1.8. Expect another forced
password change.

| # | Check | Expected |
|---|---|---|
| 2.1 | Lands on `/admin` | Overview renders, sidebar shows the eight admin items. |
| 2.2 | `/admin/departments` | Create one. **Departments live in user-service, not campus-service** — if this 404s, that is the likely cause. |
| 2.3 | `/admin/gates` | Create a gate. Needed before any guard can start a shift. |
| 2.4 | `/admin/users` | Create one user of each role: STUDENT, FACULTY, GUARD. Note the credentials. |
| 2.5 | Deactivate a user with a reason | Reason is mandatory. Check the audit log records it (2.8). |
| 2.6 | `/admin/blocklist` | Add an entry, then remove it. Reason mandatory on add. |
| 2.7 | `/admin/policy` | The six real config keys render and save. See B7. |
| 2.8 | `/admin/audit` | Shows the deactivation from 2.5 and the blocklist changes from 2.6. |
| 2.9 | `/admin/entry-logs` | **Watch this one.** See the note below. |
| 2.10 | `/admin/queue` | Visitor queue. Empty until a visitor request exists. |
| 2.11 | Try `/platform` in the URL bar | `/forbidden`. |
| 2.12 | Sign out, then paste `/admin/users` into a fresh tab | Should redirect to `/login?next=/admin/users`, **not** hang on a spinner. |

### 2.9 is the one I changed today — check it carefully

`EntryLogFilterDto` no longer accepts `campusId`; guard-service now takes the
campus from the token. The frontend still sends `campusId` in the body and Spring
ignores unknown properties, so nothing should 400.

Expected: the register loads, scoped to your campus, and searching works.

If it 403s, the cause is `resolveCampus` — tell me and I will fix it. This is the
only screen in either role that today's guard-service change touched.

### Known: B7 — policy keys

The six campus config keys save and read back, but nothing consumes most of them
yet. Saving a value and seeing it persist is a pass; expecting it to change
behaviour elsewhere is not part of this test.

---

## 3. The two cross-role checks worth doing

**3.1 — Direct-URL guarding.** For each of `/admin`, `/platform`, `/guard`,
`/student`, `/faculty`, `/visitor`: signed in as Campus Admin, paste it into the
address bar. Every one you are not entitled to must land on `/forbidden`.
Signed out, every one must redirect to `/login?next=...`.

That second half is a regression test. Until today it hung on a spinner forever
for every role — `authStore` initialised `status` to `'unknown'` with no token
and nothing ever moved it on.

**3.2 — DevTools console, the whole way through.** Keep F12 open. Any red error
is a finding even if the screen looks right. Note the screen and the message.

---

## 4. What to hand back

For anything broken, capture: the screen, what you did, what you expected, what
happened, and the console/network error if there was one.

Distinguish these three, because they go to different people:

- **A screen bug** → Arham, Phase 2
- **A backend 4xx/5xx** → the service owner (check which port in the network tab)
- **A blocked feature that says so on screen** → nobody, that is working

The most valuable finding is a screen that looks fine and is wrong — a count that
reads zero because a request 403'd silently, or a save that appears to work and
does not survive a reload. Reload after every write.
