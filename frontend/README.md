# Frontend

**No single owner — every member builds their own screens.**

One shared React app. Each member has their own folder under `src/` and only
touches `src/shared/` via PR.

## Folder ownership

| Folder | Owner |
|---|---|
| `src/shared/` | **Omkar** — routing, AuthContext, ProtectedRoute, Navbar, Sidebar, Toast (PR-only for everyone else) |
| `src/auth/` | Omkar |
| `src/users/` | Mukul |
| `src/gatepass/` | Tushar (screens 6, 7, 8, 12) + Arham (screens 9, 10, 11) |
| `src/campus/` | Arham |
| `src/guard/` | Palash |
| `src/qr/` | Sanjay |

## Full screen assignment

| # | Screen | Built by |
|---|---|---|
| 1 | Email Entry | Omkar |
| 2 | OTP Verify | Omkar |
| 3 | Student Profile | Mukul |
| 4 | Faculty Profile | Mukul |
| 5 | Student Directory | Mukul |
| 6 | Visitor Registration | Tushar |
| 7 | Approval Queue | Tushar |
| 8 | My Pass | Tushar |
| 9 | Bulk Upload | Arham |
| 10 | Bulk Progress | Arham |
| 11 | Event Management | Arham |
| 12 | Attendance Dashboard | Tushar |
| 13 | Guard Scanner | Palash |
| 14 | Pass Download | Sanjay |
| 15 | Guard Log | Palash |
| 16 | Campus Admin | Arham |

## Key dates

- **Day 13** — Omkar's shared shell must be merged. Everyone else is blocked
  until it is.
- **Day 13** — everyone else creates their own folder with one placeholder
  page wired into routing.
- **Days 14–16** — real screens built against real backends.

## Next steps

Once React code exists here, add a `Dockerfile` in this folder and uncomment
the frontend block in the root `docker-compose.yml`.
