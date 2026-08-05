# Student profile verification

Backend only. Frontend screens are not built yet.

## What it does

A student fills in their own name, date of birth, gender, address and phone
numbers, then submits them. Faculty check them and either accept or send them
back with a reason.

Faculty create the account (`AddStudentPage`), so the profile row already exists
by the time the student first signs in. They are filling in a row, not creating
one.

## States

```
DRAFT ──submit──► SUBMITTED ──approve──► VERIFIED
  ▲                   │                     │
  │                   └──reject──► REJECTED │
  │                                   │     │
  └───────────────edit────────────────┴─────┘
```

`SUBMITTED` is the only state the student cannot edit in. Faculty are reading
the details; letting them change underneath is how a reviewer ends up approving
something they never saw.

Editing a `VERIFIED` profile drops it back to `DRAFT` and wipes `verifiedBy`,
`verifiedAt` and the remarks. A verified profile that can be edited in place is
worse than no verification at all — the row would go on claiming a named member
of staff checked details they never saw, with their user id attached to it.

Editing a `REJECTED` one **keeps** the remarks, because that is the instruction
the student is working from. Those clear on the next submit.

## Endpoints

| Method | Path | Who |
|---|---|---|
| PUT | `/api/user/students/me/details` | STUDENT, self only |
| POST | `/api/user/students/me/details/submit` | STUDENT, self only |
| GET | `/api/user/students/pending` | FACULTY, CAMPUS_ADMIN, SUPER_ADMIN |
| GET | `/api/user/students/pending/count` | FACULTY, CAMPUS_ADMIN, SUPER_ADMIN |
| PATCH | `/api/user/students/{id}/verification` | FACULTY, CAMPUS_ADMIN, SUPER_ADMIN |

The student endpoints take **no id anywhere** — not in the path, not in the
body. The account comes from the token, so there is nothing to tamper with.
`PUT /students/{id}/details` would need an ownership check on every call and
would be one forgotten check away from letting any student rewrite another's
record.

`StudentVerificationDecisionDto` has **no `verifiedBy` field**. The reviewer
comes from the security context. `DocumentVerificationDto` does have one, with
a comment telling the service to ignore it — that is a trap, since the only
thing keeping it honest is a comment nobody has to read. Do not copy it.

Reviewing uses `isStaff()`, not `isAdministrative()`. Faculty know their own
students and can spot a wrong date of birth. Verifying a government ID proof
stays admin-only and is unchanged.

The queue is ordered by `submittedAt` **ascending** — oldest first. Id-descending
would bury whoever has waited longest at the bottom of the last page, which is
how a queue becomes a backlog nobody clears. Submitting twice is refused so a
double click cannot send a student to the back of it.

## Two things that changed outside this feature

### The directory no longer returns contact details

`GET /api/user/students` now uses `StudentProfileResponse.forDirectory`, which
blanks address, date of birth and both phone numbers. The single-profile read
(`/{id}`, `/me`, `/by-user/{id}`) and the verification queue still return
everything, because those are the places somebody actually needs it.

Without the split, any faculty account could page through the campus twenty rows
at a time and walk off with every student's contact details through a screen
that looks entirely routine. One profile at a time is a lookup; all of them is a
dump.

This is **not** a guard concern — guards never receive this record. They read
`ProfileSummaryResponse` over the internal endpoints, and `requireSelfOrStaff`
refuses them this one.

### `\s` removed from name and title patterns in four more services

`ValidationPatterns` in **auth-service, gatepass-service, campus-service and
qr-service** had `\s` inside a character class. `\s` also matches `\n`, `\r` and
`\t`, so a name or title could carry a line break. All four now use a literal
space. guard-service and user-service were already fixed.

auth-service's `PERSON_NAME` was the one that mattered — that is the
authoritative name, the one a pass carries and an entry log records. A name
containing a newline splits one log field into two lines with the second under
the writer's control:

```
Ravi\n2026-08-05 09:14:22 INFO Entry GRANTED gate=MAIN
```

and the audit trail now contains an entry that never happened.

campus-service's `DISPLAY_NAME` covers gate names, which appear in every scan
line guard-service writes — more log lines than any other field in the system.

**Not a regression for bulk upload.** Leading whitespace always failed (the first
character class was never `\s`). Only tabs and newlines newly fail, which is the
point.

## Not done

- Frontend screens for all five endpoints
- No tests — `updateOwnDetails`, `submitOwnDetails` and `decideVerification` each
  enforce state transitions that nothing currently checks
- `DocumentVerificationDto.verifiedBy` still sits in a request body across the
  other services (the parked DTO sweep)
