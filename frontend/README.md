# Perimity — frontend, Days 13 to 19

Copy `frontend/` into the repo root.

```
cd frontend
cp .env.example .env
npm install
npm run dev          # http://localhost:5173
```

**Also copy `backend-fix/QrCorsConfig.java` into qr-service.** Two screens do
not work without it and the failure is silent server-side — see
`backend-fix/README.md`.

---

## What is here

| Folder | Screens | Day | Data |
|---|---|---|---|
| `src/shared/ui/` | 20 components, 2 stylesheets | 13 | — |
| `src/api/` | one module per service + `useApi` | 17 | — |
| `src/auth/` | login, OTP, change password, guard login, gate start, session ended | 13–14 | live |
| `src/student/` | dashboard, pass, history, entries, profile, documents | 14 | mock |
| `src/visitor/` | dashboard, apply, submitted, rejected | 14 | mock |
| `src/gatepass/` | approval queue | 15 | mock |
| `src/campus/` | overview, users, departments, gates, blocklist, policy, audit | 15 | mock |
| `src/platform/` | overview, campuses, campus admins | 15 | mock |
| `src/qr/` | pass download | 16 | mock |
| **`src/bulk/`** | **upload, review, progress, history** | **17** | **live** |
| **`src/events/`** | **list, create/edit, detail** | **17** | **live** |
| **`src/guard/`** | **scanner, verdict, manual lookup, gate switch, entry log** | **18** | **live** |
| **`src/dashboard/`** | **campus today, platform, event attendance** | **19** | **live** |

Everything in bold calls the real backend. The Day 14–16 screens still run on
`src/mock/data.js`; converting one is a two-line change and there are now
sixteen live screens to copy the pattern from.

## The API layer

Screens import functions, never URL strings:

```js
import { gatepass, useApi } from '../api';
const { data, loading, error, reload } = useApi(() => gatepass.myPasses(), []);
```

`useApi` gives loading, error and reload to every screen, which is what makes
Day 19's "loading, empty and error states everywhere" one hook instead of
thirty hand-rolled try/catches — and thirty chances to forget the catch.

`client.js` already unwraps `ApiResponse<T>`, so no screen ever writes
`res.data.data`.

---

## Backend contract check

I checked every call this frontend makes against the controllers in the 31 July
`main` snapshot. **113 of 113 paths resolve to a real endpoint.**

I could not run against your live stack — no network to your localhost from
here — so this is a static check of paths, verbs and DTO field names, not a
round trip. Five mismatches turned up and are fixed in this zip. Four of the
five would have failed *quietly*, which is why they are worth reading:

**1. `PATCH /passes/{id}/status` — the field is `targetStatus`, not `status`.**
Sending `status` gives a 400 whose message names a field you never sent.

**2. `PATCH /visitor-requests/{id}/decision` — the body is
`{ decision: 'APPROVED' | 'REJECTED', rejectReason }`,** not
`{ approved: true, reason }`. An enum, not a boolean, and `rejectReason`, not
`reason`.

**3. `GET /passes/count` returns `{ "ACTIVE": 1284 }`** — a map keyed by the
status name. Reading `.count` gives `undefined`, which renders as a blank stat
card, not an error.

**4. `GET /visitor-requests/pending-count` returns `{ pending: n }`** — same
trap, same silent blank.

**5. Event attendance needs BOTH services, and fails plausibly if you forget.**
`GET /guard/entry-logs/events/{id}/attendance` takes `from`, `to` and
`registeredCount` as query parameters. guard-service holds entry logs but has
no idea how many passes were issued, so it cannot compute "never showed" alone
— and `registeredCount` **defaults to 0**. Call it without, and you get a
clean, well-formed, entirely wrong answer in which nobody ever failed to show
up. `api/attendance.js` does the join so both screens that need it cannot do it
differently.

Also corrected: `ApprovalQueue` documented a `GET /visitor-requests/pending`
endpoint that does not exist — status is a query parameter and the response is
a `PageResponse`, so screens read `.content`, not the body itself.

### One thing I could not fix from the frontend

**qr-service has no CORS configuration.** The other five configure it in their
`SecurityConfig`; qr-service has no Spring Security at all, so there was
nowhere for it to live, and nothing called it from a browser until now.

Bulk Progress and Pass Download both fail, and both fail the same misleading
way: the request never reaches the server, **the qr-service log stays
completely silent**, and only the browser console says anything. `backend-fix/`
has the file and where it goes.

---

## Three rules that hold the design together

**1. No hex outside `tokens.css`.** 79 tokens, 0 stray hex across 94 files.
Worth a CI check:

```bash
grep -rn "#[0-9a-fA-F]\{3,8\}" src --include=*.jsx --include=*.css \
  | grep -v tokens.css && exit 1
```

**2. Green and red belong to the guard's verdict, and nowhere else.**
`StatusBadge` is deliberately colourless — five pass states, one neutral badge,
told apart by the word. Roughly 1 in 12 men cannot separate red from green
reliably; more to the point, if `ACTIVE` is green in a table somewhere then
green stops meaning *let this person through* at the one place a colour has to
carry a decision alone, outdoors, in under a second. Every verdict combines
icon + word + colour.

**3. Routes are declared per folder.** `App.jsx` and `Layout.jsx` are finished
and nobody edits them to add a screen. Each folder exports an array; the
sidebar is derived from the same array, so a screen and its nav item cannot
drift. A duplicate-path guard logs an error in dev.

## Product rules the UI encodes

Not styling choices — changing these changes the product.

- **Entry only.** No exit scan, no duration, no direction, no "exits today".
- **Deactivate, never delete** — users, campuses, events. A cancelled event's
  passes still resolve at the gate, and a guard is told *why*, which is more
  useful than "pass not found".
- **A rejection reason is required and the applicant sees it.** A blocklist
  reason is required and the blocked person never does.
- **Validation and confirmation are separate steps in bulk upload.** Issuing
  580 passes emails 580 people and cannot be undone in bulk. Nobody should be
  one click from it.
- **Bulk arithmetic reconciles on screen.** total = valid + invalid, shown as
  three numbers you can add up. If they do not reconcile, the screen says so
  and blocks confirmation.
- **Two progress bars, not one.** Generated and emailed are different facts. A
  batch can be 100% generated with 300 people who were never told, and one bar
  reading "580 of 580" is lying by omission.
- **One gate per shift.** Switching ends the shift and starts another. A silent
  switch would make yesterday's gate report wrong with nothing to reveal it.
- **Manual lookup does not log an entry**, and says so. Otherwise every curious
  search becomes a recorded arrival.
- **Email, role, campus and campus code are permanent**, and the form says so
  before creation rather than in an error after.

## Scanner

No npm dependency. Uses the browser's built-in `BarcodeDetector` — Chrome on
Android has it, which is what a guard holds; Safari and Firefox do not, which
is why manual lookup is a first-class screen and not an untested fallback.

There is a 2.5 second cooldown per code. Without it one held-up pass fires the
same scan twenty times a second and the log fills with duplicates that look
like a person entering twenty times.

Swapping in `html5-qrcode` later is a drop-in: keep `useScanner`'s shape, change
its body.

## Known gaps

- **`passCode` does not exist in the backend.** Live screens show pass ids;
  mock screens still show `PM-4192`-style codes from the design pack. One of
  the two has to give.
- **No platform-wide user count.** `CampusStatsResponse` carries three numbers.
  The gate figure is summed from the campus list rather than invented, and
  there is no Users card — a card reading "—" forever is worse than no card.
- **Day 14–16 screens are still on mock data.** Deliberate: they were built
  before the API layer existed, and converting them is mechanical work better
  done against a running stack than blind.
