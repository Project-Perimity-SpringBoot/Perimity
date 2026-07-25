# Auth Service

**Owner: Omkar**

Handles login, users, OTP, blocklist, and audit logs. Owns `authdb`
(PostgreSQL). Runs on port 8081.

See `/docs` for the full schema and rules.

## Login model — read this carefully

Login is **not** the same for every role. This changed in SRS v1.1; earlier
drafts said "passwordless everywhere", which is no longer correct.

| Role | Password | OTP |
|---|---|---|
| Super Admin | yes | no |
| Campus Admin | yes | no |
| Guard | yes | no |
| Faculty | yes | yes — user chooses |
| Student | yes | yes — user chooses |
| Visitor | never | yes, only |

Passwords are bcrypt. OTPs are SHA-256, six digits, expire in 10 minutes, lock
after 5 failed attempts, and are rate-limited to 3 requests per email per
15 minutes.

## What this service owns

| Area | Requirements |
|---|---|
| Registration + OTP | FR-REG-1 … FR-REG-9 |
| Authentication | FR-AUTH-1 … FR-AUTH-7 |
| Blocklist | FR-BLK-1 … FR-BLK-6 |
| Audit log | FR-AUD-1 … FR-AUD-5 |
| Sessions and credentials | FR-SESS-1 … FR-SESS-7 |

New since v1.0: **blocklist**, **audit log**, **logout**, **forgot password**,
**first-login password change**, **account lockout**, **guard-to-gate binding at
login**.

Blocklisting a person must also revoke any active pass they hold (FR-BLK-6) —
that means calling gatepass-service, not writing to its database.

## Frontend screens owned

| # | Screen |
|---|---|
| 1 | Email Entry |
| 2 | OTP Verify |
| — | **Shared shell** — routing, AuthContext, ProtectedRoute, Navbar, Sidebar, Toast |

The login page needs a password field **and** a "log in with OTP instead"
option for Faculty and Student. Visitors see the email + OTP flow only.

Screens 17 (Blocklist) and 18 (Audit Log) call this service's APIs but are
built by Mukul and Palash. Have those endpoints ready before they start.

⚠️ The shared shell is a **hard Day 13 deadline** — all five other members build
their pages inside it.

Folder: `frontend/src/auth/` and `frontend/src/shared/`

## Next steps

Once you have Spring Boot code here, add a `Dockerfile` in this folder and
uncomment this service's block in the root `docker-compose.yml`.
