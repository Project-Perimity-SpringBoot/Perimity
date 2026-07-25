## Which service does this touch?

- [ ] Auth Service
- [ ] User Service
- [ ] Gate Pass Service
- [ ] Campus Service
- [ ] Guard Service
- [ ] QR Service
- [ ] Frontend
- [ ] Docs / infra only

## What does this PR do?

<!-- 1-3 sentences -->

## Checklist

- [ ] No institution name, department list, or email domain hardcoded anywhere
      (campus data comes from the API, not from code)
- [ ] No `Semester` field added to any UI form
- [ ] Entry-only — no exit scan, no in/out toggle added
- [ ] No service reads another service's database directly
- [ ] No secret, password, key, or token committed (all read from env vars)
- [ ] Runs locally with `docker compose up --build`
- [ ] I tested this manually / added tests

## Screenshots (if UI change)

