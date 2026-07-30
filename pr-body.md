## Which service does this touch?

- [x] Guard Service

## What does this PR do?

Replaces the development stubs with real service-to-service calls for pass
verification. A scan now makes two hops: `POST {qr}/api/internal/qr/decrypt`
for token authenticity, then `GET {gatepass}/api/internal/gatepass/passes/{id}`
for lifecycle status. Also wires Behavior 2 — a daily QR scanned during a
running event auto-attributes to that event.

**Neither endpoint exists yet.** Sanjay and Tushar have the exact contracts.
Until they ship, run with `GUARD_CLIENTS=stub`. The default is `http`, which is
correct for production and will return 503 on every scan today.

### Notable decisions

- **An outage is not a denial.** A transport failure throws
  `PassVerificationUnavailableException` → 503, and writes **no** EntryLog.
  FR-SCAN-10 requires the guard to tell "pass invalid" from "scanner broken";
  the scanner renders its red card off a 200 body, so the status code is what
  keeps an outage off that screen. Logging it as a denial would put a refusal
  against the name of someone who may hold a valid pass.
- **A Behavior 2 failure is swallowed**, deliberately the opposite. It cannot
  deny entry, only decide which column an entry is counted in — a green light
  with imperfect attendance beats a queue at the gate.
- **`status` and `passType` deserialise as `String`, not enums.** An unknown
  `PassStatus` becomes a logged refusal instead of a 500 that stops the gate,
  and the six services can deploy in any order.
- **Stub condition changed** from `@ConditionalOnMissingBean` to an explicit
  `perimity.guard.clients` property. The old annotation is built for `@Bean`
  methods in auto-configuration; on a plain `@Component` it is evaluated during
  scanning in no defined order, so adding a second implementation could have
  produced `NoUniqueBeanDefinitionException` on `ScanService`'s constructor
  rather than retiring the stub.
- **Timeout is 400ms per hop**, not gatepass-service's 3000ms. Two hops inside
  the one-second FR-SCAN-3 budget. This stops mattering when Redis lands on
  Day 11.
- **`.factorypath` untracked.** Eclipse annotation-processor config, machine-
  local, already in `.gitignore` — it was committed before the rule existed.

## Checklist

- [x] No institution name, department list, or email domain hardcoded anywhere
- [x] No `Semester` field added to any UI form
- [x] Entry-only — no exit scan, no in/out toggle added
- [x] No service reads another service's database directly — REST only
- [x] No secret, password, key, or token committed (all read from env vars)
- [ ] Runs locally with `docker compose up --build` — **guard-service has no
      Dockerfile yet (Day 21) and its compose block is still commented out**
- [ ] I tested this manually / added tests — **cannot integration-test until
      the two endpoints above exist. Compiles and boots clean; the call paths
      are unexercised**
