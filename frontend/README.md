# Frontend

**No single owner — every member builds their own screens.**

One shared React app. Each member has their own folder under `src/` and only
touches `src/shared/` via PR.

## Rules

- **No institution name in any component, constant, page title, or string.**
  Campus name and logo render from the API response for the logged-in user's
  campus.
- **No `Semester` field in any form.**
- **No exit scan or in/out toggle** in the guard scanner.
- Department dropdowns are populated from the API, never hardcoded, and allow no
  free-text entry.

## Folder ownership

| Folder | Owner |
|---|---|
| `src/shared/` | **Omkar** — routing, AuthContext, ProtectedRoute, Navbar, Sidebar, Toast (PR-only for everyone else) |
| `src/auth/` | Omkar (screens 1, 2) + Mukul (17) + Palash (18) |
| `src/users/` | Mukul |
| `src/gatepass/` | Tushar (6, 7, 8, 12) + Arham (9, 10, 11) |
| `src/campus/` | Arham (16, 19) + Sanjay (20) |
| `src/guard/` | Palash |
| `src/qr/` | Sanjay |

## Full screen assignment

Screens 17–20 are new in SRS v1.1.

| # | Screen | Built by | Calls |
|---|---|---|---|
| 1 | Email Entry | Omkar | auth |
| 2 | OTP Verify | Omkar | auth |
| 3 | Student Profile | Mukul | user |
| 4 | Faculty Profile | Mukul | user |
| 5 | Student Directory | Mukul | user |
| 6 | Visitor Registration | Tushar | gatepass |
| 7 | Approval Queue | Tushar | gatepass |
| 8 | My Pass | Tushar | gatepass |
| 9 | Bulk Upload | Arham | gatepass |
| 10 | Bulk Progress | Arham | qr |
| 11 | Event Management | Arham | gatepass |
| 12 | Attendance Dashboard | Tushar | gatepass |
| 13 | Guard Scanner | Palash | guard |
| 14 | Pass Download | Sanjay | qr |
| 15 | Guard Log | Palash | guard |
| 16 | Campus Admin | Arham | campus |
| **17** | **Blocklist** | Mukul | auth |
| **18** | **Audit Log** | Palash | auth |
| **19** | **Campus Settings** | Arham | campus |
| **20** | **Super Admin Console** | Sanjay | campus |

Screen count by member: Omkar 2 + shared shell, Mukul 4, Tushar 4, Arham 5,
Palash 3, Sanjay 2. Arham carries the most screens because campus-service is the
lightest backend; Sanjay carries the fewest because qr-service is the heaviest.

## Login page — changed in v1.1

The login page is no longer OTP-only. It needs a password field, plus a "log in
with OTP instead" option shown only to Faculty and Student. Super Admin, Campus
Admin, and Guard are password-only. Visitors use the email + OTP flow and never
see a password field.

## Key dates

- **Day 13** — Omkar's shared shell must be merged. Everyone else is blocked
  until it is.
- **Day 13** — everyone else creates their own folder with one placeholder
  page wired into routing.
- **Days 14–16** — real screens built against real backends.

## Next steps

Once React code exists here, add a `Dockerfile` in this folder and uncomment
the frontend block in the root `docker-compose.yml`.
