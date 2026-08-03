# guard-service — security review, 2026-08-02

Review of the whole service, not only the diff. Seven findings, six fixed.

The three most serious are **the same bug three times**, and that is the useful
thing to take from this review: an authority value — who you are, which campus
you belong to — arriving in a request body instead of from the verified token.

---

## 1. Stored XSS in the scanner → guard token theft — **CRITICAL, fixed**

`scanner.html` rendered the holder's name with `innerHTML`:

```js
$('who').innerHTML = bits.join('<br>');   // bits[0] is d.holderName
```

`holderName` is a person's name. It originates in auth-service at registration
or in a bulk-upload cell, is copied onto the pass, and arrives here unescaped.

**The attack.** Register — or get bulk-uploaded — with the name
`<img src=x onerror="...">`. Obtain any pass. The payload executes the moment a
guard scans you, in the guard's browser, on a page where the **GUARD JWT is
sitting in an input field**. Steal it, and you can post scans as that guard:
the one role that can write the entry log.

**Fix.** `textContent`, with the `<br>` built as an element. It was the only
`innerHTML` in the file — every other sink already used `textContent`.

**Note on CSP:** a Content-Security-Policy was added, but it would **not** have
prevented this. The page has an inline `<script>`, so `script-src` must allow
`'unsafe-inline'`, and an `onerror` handler is inline script. Escaping is the
fix; CSP is defence in depth. What CSP does buy is the *second half* of the
attack — `connect-src 'self'`, `img-src 'self' data: blob:`, `form-action
'none'` and `base-uri 'none'` mean a payload that runs can no longer send the
stolen token anywhere. Drop `'unsafe-inline'` when the React shell replaces this
page; at that point CSP becomes a real XSS control.

---

## 2. Cross-campus reads of the entry register — **HIGH, fixed**

`EntryLogFilterDto.campusId` was `@NotNull`, supplied by the caller. Three more
endpoints took a path-variable id with no campus term at all.

Any authenticated GUARD or CAMPUS_ADMIN could read another campus's entire gate
register:

```
POST /api/guard/entry-logs/search   { "campusId": <another campus>, ... }
GET  /api/guard/entry-logs/holder/{anyUserId}
GET  /api/guard/entry-logs/pass/{anyPassId}
GET  /api/guard/entry-logs/session/{anySessionId}
```

On a multi-tenant product this is a tenancy breach, not merely an IDOR.

**Options considered, and why the chosen one won.**

| Approach | Rejected because |
|---|---|
| Validate in the controller (`requireSameCampus`) | A check you must remember on endpoint #7 — the exact failure mode that produced the bug. And the three path-variable reads had nothing to validate against, so you would fetch first and check after: the unscoped read has already happened. |
| Read `SecurityContextHolder` inside the service | Works, but drags Spring Security into a class that only reads Mongo, and forces every unit test to build an auth context. |
| `@PreAuthorize` with SpEL | Fails identically when someone forgets the annotation, and the expression is not compile-checked. |

**Chosen: campus is a required parameter on every service method, AND every
repository query carries it.** Neither half suffices alone — with the parameter
only, `findByPassId...` still returns other tenants' rows unless filtered after
the fetch, and filtering after the fetch means the documents were already read.
With scoped queries only, the controller can still pass a client-supplied value.

`campusId` was **deleted** from the DTO rather than validated, and
`EntryLogController.resolveCampus` **ignores** any campus the caller asks for
rather than comparing it. A comparison can be written wrong; an input that does
not exist cannot be trusted by accident.

SUPER_ADMIN has `campusId: null`, so they are the one role that must name a
campus and the only one permitted to. They get a 403 if they omit it — silently
picking one would give a platform-wide admin confidently wrong figures.

Locked in by four tests that assert the campus reaches the **repository**, not
merely that a list comes back. A service that accepts a campus and then calls an
unscoped finder is precisely this bug, and would pass a weaker test.

---

## 3. Cross-campus **writes** to the register — **HIGH, fixed**

Worse than finding 2, and found because of it.

`ScanSessionStartDto.campusId` was `@NotNull`, client-supplied. A guard could
open a shift naming **any** campus. Every scan inherits campus from the open
session — by design, so a guard cannot name a gate per scan — so that single
field propagated everywhere downstream: the campus check
(`session.getCampusId().equals(pass.campusId())`) compared a forged campus
against a matching one and passed, and every resulting `EntryLog` was written
with another institution's `campusId`.

Not a read leak. A **write** into another tenant's register. The entry log is
meant to be evidence, and evidence a guard can file under someone else's
institution is not evidence.

**Fix.** Field deleted; `start()` takes `campusId` as a parameter supplied by
`currentUser.campusId()`. No test was added on purpose — the field no longer
exists, so "a guard supplies their own campus" is not expressible in Java. A
test would be testing the compiler. Finding 2 needed tests because its scoping
lives in query strings the compiler cannot check.

The controller already argued this rule correctly on `GET /open` — *"Campus
comes from the token, never a parameter"* — it simply had not been applied to
the write everything else inherits from.

---

## 4. Unauthenticated dependency map via `/actuator/health` — **MEDIUM, fixed**

`show-details=always` plus `permitAll()` meant anyone who could reach port 8085
could enumerate, with no token, which services were up, which were down, and
that the store is MongoDB. "Which peer is currently down" is exactly what you
want to know before attacking one.

The endpoint must stay public — Docker and the load balancer poll it without
credentials, and a health check that needs a token restarts the container
forever. So the endpoint stays open and the **detail** is what closes:
`HEALTH_DETAIL=never` in any deployment, `always` locally where it is the whole
value of the Day 12 work.

---

## 5. Error detail returned in every environment — **MEDIUM, fixed**

`server.error.include-message=always` and `include-binding-errors=always` were
labelled "DEVELOPMENT ONLY" in a comment. A comment is not a control. These put
exception messages and bean-validation failures in the response body, which is
how an attacker learns field names, constraints and occasionally a stack frame.
Now `${ERROR_DETAIL:always}` — one env var switches it off.

---

## 6. Missing response headers — **MEDIUM, fixed**

No CSP, no Referrer-Policy. Added, with the honest scoping in finding 1.
`frame-ancestors 'none'` also closes clickjacking: without it the scanner page
can be framed invisibly and a guard tricked into ending a shift.

---

## 7. CORS — **LOW, fixed**

Origins were hardcoded to two localhost ports, so the first deployment would
have had to edit Java to let its own frontend reach it — and the usual fix under
time pressure is a wildcard. Now `CORS_ORIGINS`, defaulting to the same two.

`allowCredentials` was `true` and is now `false`. This API authenticates with a
Bearer token in a header; the browser never sends a cookie here, so credentialed
CORS bought nothing and only raised the cost of a wrong entry in the list.
`allowedHeaders` is now a named list rather than `*`.

---

## What was already right

Worth recording, because a review that only lists faults misrepresents the code.

- **JWT is verify-only.** No `issue()` method. Five services that can only read
  means one place to audit if a token is ever forged.
- **No fallback secret.** Missing `JWT_SECRET` refuses to start, rather than
  falling back to a value in Git that would validate forged tokens.
- **Key length enforced** — HS256 needs 256 bits and the service checks.
- **Issuer required** on every parse.
- **Constant-time comparison** of the internal API key, with an accurate comment
  about why `String.equals` leaks the key one character at a time.
- **`ScanRequestDto` carries no authority fields** — no `guardUserId`, `gateId`,
  `campusId`, `scannedAt`, or result. The token and the open session supply all
  of them.
- **Token input is bounded and pattern-restricted** — 2048 chars, charset-limited.
- **Tokens are never logged raw** — a 12-hex-character SHA-256 fingerprint,
  enough to correlate, useless if leaked.
- **Shift ownership is checked** — one guard cannot end another's shift by
  guessing an id, and the code says why it belts-and-braces the token check.

---

## Still open

**Rate limiting on `POST /api/guard/scan`.** Each scan makes two downstream
calls. Not fixed here deliberately: it needs a new dependency, and adding one on
top of an unverified refactor makes the next red build ambiguous. The circuit
breaker added on 2026-08-02 already blunts the resource-exhaustion angle, since
a failing peer no longer costs 400 ms per request.

**qr-service has no Spring Security at all** — not this service's fix, but it
undermines this one. guard-service sends `X-Internal-Api-Key` on every decrypt
call and qr-service does not check it. If `/api/qr/internal/decrypt` is reachable
and unauthenticated, anyone can decrypt any pass token, and no amount of
correctness here compensates. This is the single highest-value security item
left on the platform.
