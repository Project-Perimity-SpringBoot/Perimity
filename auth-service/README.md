# Auth Service

**Owner: Omkar**

Handles login, users, OTP, and audit logs. Owns AuthDB (PostgreSQL, port 5432).
See `/docs` for the full schema and rules.

## Frontend screens owned

| # | Screen |
|---|---|
| 1 | Email Entry |
| 2 | OTP Verify |
| — | **Shared shell** — routing, AuthContext, ProtectedRoute, Navbar, Sidebar, Toast |

⚠️ The shared shell is a **hard Day 13 deadline** — all five other members build
their pages inside it.

Folder: `frontend/src/auth/` and `frontend/src/shared/`

## Next steps

Once you have Spring Boot code here, add a `Dockerfile` in this folder and
uncomment this service's block in the root `docker-compose.yml`.
