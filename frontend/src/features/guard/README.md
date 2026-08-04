# guard — Phase 6 — Palash

This folder is yours. 8 screens.

    routes/       one file per screen, default-exported
    components/   anything used by more than one of your screens
    schemas/      Zod, mirroring the server regex from lib/validation/patterns.ts

## Rules that are not negotiable

- Import from `@ui/index` and `@components/*`. Never edit either — if a shared
  component needs a change, post the exact change in the group. Omkar pushes it.
- Never import from another feature folder. ESLint enforces this.
- `ApiResponse` and `PageResponse` never leave `src/lib/api/`. Consume `Paged<T>`.
- Never send `campusId`, `createdBy`, `reviewedBy` or `changedBy` — all four are
  `@JsonIgnore` server-side and set from the token.
- Never send `sort` to /students, /faculty, /visitor-requests, /bulk or /events.
  Spring Data emits two ORDER BY clauses and the query fails.
- No hardcoded colour, size, radius or shadow. `tokens.css` or nothing.
- Verdict colours (--allow-*, --deny-*, --review-*) are the guard scan screen's
  alone. Pass statuses are all neutral, told apart by their word.

## Adding your routes

`src/app/router.tsx` is Omkar's. Post two lines in the group:

    const MyScreen = lazy(() => import('@features/guard/routes/MyScreen'));
    { path: '/your/path', element: <MyScreen /> },

He replaces the `<PhasePending />` entry for your area. Ten minutes.
