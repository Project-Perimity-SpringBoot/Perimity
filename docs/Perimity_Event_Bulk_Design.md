# Perimity — Event & Bulk Visitor Design

**Smart Campus Access & Gate Pass Management System**
Design decisions for events, bulk onboarding, and the gate scan logic.

Version 1.1 · aligned with `Perimity_SRS.pdf` and `Perimity_SRS_v1.1_Amendments.md`

---

## Guiding Principle

**Replace the paper register at the gate.** Entry-only. Passwordless (email + OTP) for visitors. Every design choice serves a faster, searchable, forgery-proof version of the guard's notebook.

---

## Core Model: A Pass Is Not a Person

Two concepts kept separate:

- **Identity** — who you are. One per person, forever, keyed by email.
- **Pass** — permission to enter, for a purpose, for a time window. A person can hold many at once.

A student can simultaneously hold:

- a `DAILY` pass (their standing member pass, no end date), and
- an `EVENT` pass (for a specific programme, valid only for the event dates).

Both active at the same time is normal — like holding a company ID badge and a separate conference lanyard.

---

## Login Model

Login is **not** the same for everyone. An earlier draft of this document said "passwordless everywhere, per project decision"; that was superseded by the SRS and is no longer correct.

| User | Password | OTP |
|------|----------|-----|
| Super Admin | yes | no |
| Campus Admin | yes | no |
| Guard | yes | no |
| Faculty | yes | yes — user chooses |
| Student | yes | yes — user chooses |
| Visitor | never | yes, only |
| Event visitor | never | yes, only |

A one-time event visitor never creates a password. If they lose the pass email, they go to the site, enter their email, get an OTP, and see their pass again. Staff roles use bcrypt passwords with a forgot-password flow, first-login password change for administrator-created accounts, and lockout after repeated failures.

---

## Bulk Onboarding — One Engine for Everything

There is a **single bulk engine**, not separate flows for students and visitors. The only difference is a `type` column and where the dates come from.

| Type | Dates come from | Pass produced |
|------|----------------|---------------|
| `student` | none (permanent) | DAILY pass, no end date |
| `visitor` | the event's date range | EVENT pass, event dates |

### Excel columns (minimum)

For an event visitor batch: `name, email, phone, purpose`.

Visit dates are **not** per-row — the event's date range applies to the whole batch (for example, three days set once for all 600 attendees).

A downloadable template with the correct column headings must be provided (FR-BULK-7). Uploads above the configured row limit are rejected with the limit stated in the message (FR-BULK-8).

### Validation and confirmation flow

```
Faculty/Admin uploads Excel (e.g. 600 rows for a summit)
   ↓  (fast path — faculty waits ~2 seconds)
Parse + validate every row
   ↓
Show summary: "580 valid, 20 errors"
   ↓
Faculty clicks Confirm
   ↓
Create identities + drop QR/PDF jobs into RabbitMQ
   ↓
Faculty sees "Done — passes are being generated"  (faculty can leave)
   ↓  (slow path — background, faculty not waiting)
QR Service processes jobs one by one:
   generate token → QR PNG → PDF → upload to object storage → email pass
   ↓
Each visitor gets: welcome email + QR pass PDF attached
```

Why async: generating 600 QRs, 600 PDFs, and 600 emails synchronously would time out the browser and half would fail. RabbitMQ decouples this so the faculty never waits and one failure never blocks the rest.

### Handling bad rows

- Process all valid rows; never block the batch for a few bad ones.
- Return a downloadable error report: `row 34: invalid email`, `row 51: duplicate`.
- Faculty fixes only those rows and re-uploads them. The progress view allows retrying **only** the failed rows, not the whole batch (FR-BULK-9).

### Duplicate and blocklist checks during validation

- Same email twice in the sheet, or already has a pass for this event → skip, flag in the error report.
- Email or phone on the campus blocklist → skip automatically, flag in the report (FR-BLK-3).

### Cancelling an event

Cancelling an event revokes every pass issued for it and notifies the holders (FR-BULK-10).

---

## The Mixed-Attendee Problem (600 attendees, 102 already members)

The faculty uploading 600 rows should **not** have to know who is already in the system. The engine decides per row, matched by email:

```
For each row (matched by email):
  ├─ email exists as a student/faculty of ANY campus?
  │     → reuse their identity, issue only an EVENT pass
  │       (no duplicate account created)
  │
  └─ email is brand new (totally outside)?
        → create a lightweight VISITOR identity
          (name, email, phone — no roll number, no department)
          + issue an EVENT pass
```

So all 600 receive an **event pass**, but only the new people get a new identity. The 102 existing members are recognised by email and linked to their existing identity. No duplicates.

### Cross-campus attendees

- **From another campus already in the system** — recognised by email, event pass scoped to the hosting campus's event, existing identity reused.
- **From totally outside** — brand new lightweight visitor identity plus an event pass. After the event the pass expires; the identity remains for audit ("who attended this programme").

The email is the universal key in every case.

---

## Which QR Matters During an Event (Option B)

During event days, the **event QR is the one that matters** — scanning it logs entry against the event, giving the organiser a clean attendance list. The member's daily pass still works for normal campus entry outside the event.

The welcome email tells the visitor: use this QR for the programme.

### The two-QR problem and how it's handled (Behavior 2)

A student attending the event has two valid QRs (daily and event). If they scan the **daily** QR out of habit during event days, the system is smart about it:

> If this person has an event running today, auto-attribute the entry to that event — even though they scanned the daily QR.

Green light, one scan, no gate friction — and the organiser still gets accurate attendance because the system does the attribution, not the guard.

---

## Gate Scan Logic (Entry-Only)

```
Guard scans QR
   ↓
Decrypt token → identify person + which pass
   ↓
Token decrypts and is authentic?
   ├─ NO → RED "invalid pass", log INVALID_TOKEN
   └─ YES
        ↓
     Pass ACTIVE and in date range?
     ├─ NO → RED + reason (pending / paused / expired / revoked), log denied attempt
     └─ YES
          ↓
       Already scanned today?
       ├─ YES → result from campus config `repeat_entry_result`
       │         (GREEN or AMBER — either way, still logged)
       └─ NO
            ↓
         Is this an EVENT pass?
         ├─ YES → log entry against event_id → GREEN "Welcome to [Event]"
         └─ NO (daily pass)
               ↓
            Does this person have an event running today?   (Behavior 2)
            ├─ YES → log entry against that event → GREEN "Welcome to [Event]"
            └─ NO  → log normal campus entry → GREEN "Welcome"
```

- One scan, instant green — the branching is invisible to the guard.
- **Entry-only: no exit scan, no in/out toggle.** An earlier draft table mentioned "select entry/exit"; that was wrong and must not be built.
- Repeat entries in a day are all logged (a person may enter multiple times; each is a row), matching how a paper register would have multiple lines.
- For a multi-day event, each day's first scan is that day's attendance; a 3-day event means up to 3 entry logs per person, grouped by day in the organiser view.

### Amber is now defined

v1.0 left amber ambiguous — FR-SCAN-2 called it a repeat entry "as configured", with nothing configuring it. In v1.1 it is driven by the campus config key `repeat_entry_result` (`GREEN` or `AMBER`, default `AMBER`), read from Campus Service. The entry is logged either way.

### Result card contents

Alongside the colour, the scanner shows the holder's name, photo, pass type, and validity dates (FR-SCAN-9), so the guard can eyeball the person against the pass.

Camera unavailable, network unreachable, and unreadable QR are **distinct error states**, not red denials (FR-SCAN-10). A guard must be able to tell "this pass is invalid" apart from "the scanner is broken".

---

## Performance

A scan must return in under one second (FR-SCAN-3). This is achieved with a Redis cache of active-pass lookups plus an asynchronous write to the entry log — the guard is not made to wait on the MongoDB insert. The cache entry is invalidated whenever a pass changes state.

---

## Organiser Attendance View (the payoff)

A paper register could never produce this — it is the strongest demo moment for the event feature:

```
[Event name] · [date range]
─────────────────────────
Registered:     600
Attended Day 1: 543  (90%)
Attended Day 2: 478  (80%)
Never showed:    41

[Search attendee]   [Export attendance CSV]
```

---

## Welcome Email (pass delivery and welcome in one)

Every campus-facing value is a substitution variable resolved from the campus and event records at send time. No institution name is ever written into a template literal.

```
Subject: Your gate pass for {event_name} at {campus_name}

Hi {name},
Welcome to {event_name} on {event_dates}.
Your entry QR pass is attached — show it at the gate.
Valid: {event_dates}, {gate_name}.

— {campus_name}
```

One email, QR PDF attached. Welcome and pass together, not two separate emails. Failed sends are retried a bounded number of times and then flagged (FR-NOT-5).

---

## Visitor Self-Service

A holder who loses the pass email retrieves it themselves without contacting the campus (FR-VIS):

1. Enter email on the public pass-retrieval page.
2. Receive an OTP, verify it.
3. See all currently `ACTIVE` passes, download the PDF, or have it re-sent.

Passes that are pending, paused, expired, or revoked are shown **with their status but without the QR code**. Every retrieval is written to the audit log.

---

## Schema Additions for Events

`events` table:

| Column | Notes |
|--------|-------|
| `id` | PK |
| `campus_id` | FK — event belongs to a campus |
| `name` | e.g. "Annual Technical Summit" |
| `valid_from` | event start date |
| `valid_to` | event end date |
| `created_by` | faculty/admin user id |
| `is_cancelled` | cancelling revokes all its passes |

Additions to `gate_passes`:

| Column | Notes |
|--------|-------|
| `pass_type` | `DAILY` or `EVENT` |
| `event_id` | NULL for daily passes, set for event passes |

So one person can hold:

- pass A: `type=DAILY, event_id=null` (normal member pass)
- pass B: `type=EVENT, event_id=17, valid for the event dates` (the programme)

Both active, no conflict.

---

## Decisions Locked

1. One identity per person, many passes — daily and event passes coexist.
2. Bulk engine is shared for students and event visitors; only `type` and date source differ.
3. Mixed attendee batches resolved per-row by email — existing users reused, new people get lightweight visitor identities.
4. Event QR is authoritative during events (Option B).
5. Daily QR scanned during an event auto-attributes to the running event (Behavior 2).
6. Entry-only; every entry logged; multi-day events log one attendance per day.
7. Amber is a per-campus configuration value, not a hardcoded rule.
8. Login is role-based, not passwordless for everyone.
9. No institution name, department list, or email domain appears in any template, seed, or literal.
