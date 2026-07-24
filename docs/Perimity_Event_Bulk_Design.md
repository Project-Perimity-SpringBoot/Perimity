# Perimity — Event & Bulk Visitor Design

**Smart Campus Access & Gate Pass Management System**
Design decisions for events, bulk onboarding, and the gate scan logic.

---

## Guiding Principle

**Replace the paper register at the gate.** Entry-only. Passwordless (email + OTP) for visitors. Every design choice serves a faster, searchable, forgery-proof version of the guard's notebook.

---

## Core Model: A Pass Is Not a Person

Two concepts kept separate:

- **Identity** — who you are. One per person, forever, keyed by email.
- **Pass** — permission to enter, for a purpose, for a time window. A person can hold many at once.

A student can simultaneously hold:
- a `DAILY` pass (their standing student pass, no end date), and
- an `EVENT` pass (for a specific programme, valid only for the event dates).

Both active at the same time is normal — like holding a company ID badge and a separate conference lanyard.

---

## Login Model

| User | Login method |
|------|-------------|
| Visitor | Email + OTP only, no password |
| Event visitor | Email + OTP only, can re-view/download pass anytime |
| Student / Faculty / Admin | Email + OTP (passwordless everywhere, per project decision) |

A one-time event visitor never creates a password. If they lose the pass email, they go to the site, enter email, get OTP, and see their pass again.

---

## Bulk Onboarding — One Engine for Everything

There is a **single bulk engine**, not separate flows for students vs visitors. The only difference is a `type` column and where the dates come from.

| Type | Dates come from | Pass produced |
|------|----------------|---------------|
| `student` | none (permanent) | DAILY pass, no end date |
| `visitor` | the event's date range | EVENT pass, event dates |

### Excel columns (minimum)

For an event visitor batch: `name, email, phone, purpose`.
Visit dates are **not** per-row — the event's date range applies to the whole batch (e.g. Aug 10–12 set once for all 600).

### Validation and confirmation flow

```
Faculty/Admin uploads Excel (e.g. 600 rows for "AI Summit")
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
QR/PDF service processes jobs one by one:
   generate token → QR PNG → PDF → upload to S3 → email pass
   ↓
Each visitor gets: welcome email + QR pass PDF attached
```

Why async: generating 600 QRs + 600 PDFs + 600 emails synchronously would time out the browser and half would fail. RabbitMQ decouples this so the faculty never waits and one failure never blocks the rest.

### Handling bad rows

- Process all valid rows; never block the batch for a few bad ones.
- Return a downloadable error report: `row 34: invalid email`, `row 51: duplicate`.
- Faculty fixes only those rows and re-uploads them.

### Duplicate / blocklist checks during validation

- Same email twice in the sheet, or already has a pass for this event → skip, flag in error report.
- Email on the campus blocklist → skip automatically, flag in report.

---

## The Mixed-Attendee Problem (600 attendees, 102 already students)

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

So all 600 receive an **event pass**, but only the new people get a new identity. The 102 existing students are recognized by email and linked to their existing identity. No duplicates.

### Cross-campus attendees

- **From another campus in our system** (e.g. CDAC Pune student at CDAC Mumbai's event) → recognized by email, event pass scoped to Mumbai's event, their existing identity reused.
- **From totally outside** → brand new lightweight visitor identity + event pass. After the event the pass expires; the identity stays only for audit ("who attended AI Summit 2026").

The email is the universal key in every case.

---

## Which QR Matters During an Event (Option B)

During event days, the **event QR is the one that matters** — scanning it logs entry against the event, giving the organizer a clean attendance list. The student's daily pass still works for normal campus entry outside the event.

The welcome email tells the visitor: "use this QR for the programme."

### The two-QR problem and how it's handled (Behavior 2)

A student attending the event has two valid QRs (daily + event). If they scan the **daily** QR out of habit during event days, the system is smart about it:

> If this person has an event running today, auto-attribute the entry to that event — even though they scanned the daily QR.

Green light, one scan, no gate friction — and the organizer still gets accurate attendance because the system does the attribution, not the guard.

---

## Gate Scan Logic (Entry-Only)

```
Guard scans QR
   ↓
Decrypt token → identify person + which pass
   ↓
Pass valid + active + in date range?
   ├─ NO → RED + reason (expired / revoked / invalid), log denied attempt
   └─ YES
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
- Entry-only: no exit scan, no in/out toggle.
- Repeat entries in a day are all logged (a person may enter multiple times; each is a row), matching how a paper register would have multiple lines.
- For a multi-day event, each day's first scan is that day's attendance; a 3-day event = up to 3 entry logs per person, grouped by day in the organizer view.

---

## Organizer Attendance View (the payoff)

A paper register could never produce this — it is the strongest demo moment for the event feature:

```
AI Summit · Aug 10–12
─────────────────────────
Registered:     600
Attended Day 1: 543  (90%)
Attended Day 2: 478  (80%)
Never showed:    41

[Search attendee]   [Export attendance CSV]
```

---

## Welcome Email (pass delivery + welcome in one)

```
Subject: Your gate pass for AI Summit at CDAC Mumbai

Hi [Name],
Welcome to AI Summit on Aug 10–12.
Your entry QR pass is attached — show it at the gate.
Valid: Aug 10–12, Main Gate.

— CDAC Mumbai
```

One email, QR PDF attached. Welcome + pass together, not two separate emails.

---

## Schema Additions for Events

New `events` table:

| Column | Notes |
|--------|-------|
| id | PK |
| campus_id | FK — event belongs to a campus |
| name | e.g. "AI Summit" |
| valid_from | event start date |
| valid_to | event end date |
| created_by | faculty/admin user id |

Additions to `gate_passes`:

| Column | Notes |
|--------|-------|
| pass_type | `DAILY` or `EVENT` |
| event_id | null for daily passes, set for event passes |

So one person can hold:
- pass A: `type=DAILY, event_id=null` (normal student pass)
- pass B: `type=EVENT, event_id=17, valid Aug 10–12` (the programme)

Both active, no conflict.

---

## Decisions Locked

1. One identity per person, many passes — daily and event passes coexist.
2. Bulk engine is shared for students and event visitors; only `type` and date source differ.
3. Mixed attendee batches resolved per-row by email — existing users reused, new people get lightweight visitor identities.
4. Event QR is authoritative during events (Option B).
5. Daily QR scanned during an event auto-attributes to the running event (Behavior 2).
6. Entry-only; every entry logged; multi-day events log one attendance per day.
