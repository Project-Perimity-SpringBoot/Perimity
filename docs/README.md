# Docs

Governing documents. Read the ones relevant to your service before writing code.

| File | What it is | Authority |
|---|---|---|
| `Perimity_SRS.pdf` | Formal IEEE-830 requirements spec, 25 pages, FR-xxx codes | **Authoritative** |
| `Perimity_SRS_v1.1_Amendments.md` | Corrections and additions to apply to the above | **Authoritative** |
| `Perimity_Team_Guide.md` | Per-service data, APIs, screens, testing method | Build companion |
| `Perimity_Database_Design.md` | Full schema, columns, object storage layout | Companion |
| `Perimity_Event_Bulk_Design.md` | Identity vs pass model, bulk engine, gate scan logic | Companion |
| `Perimity-Complete-Roadmap.md` | 25-day sprint plan, milestones, demo script, viva guide | Planning |

## Two housekeeping actions

**1. Delete the old PDFs.** The four `.pdf` files previously in this folder are
superseded by the `.md` files above. Markdown is diffable, reviewable in a PR,
and editable by anyone — a PDF in Git is a binary blob nobody can review.

```
git rm docs/Perimity_SRS_v1.1.pdf
git rm docs/Perimity_Database_Design.pdf
git rm docs/Perimity_Event_Bulk_Design.pdf
git rm docs/Perimity-Complete-Roadmap.pdf
```

Keep `Perimity_SRS.pdf` — the 25-page formal spec — since that is a submission
artefact rather than a working document. If you need PDFs of the others for
submission, generate them from the markdown at the end, so the markdown stays
the single source of truth.

**2. Note the version collision that caused this.** The file previously named
`Perimity_SRS_v1.1.pdf` was a 9-page internal team document — older than, and
different from, the 25-page formal SRS. Two files called "v1.1" would send six
people to two different specs. It is now `Perimity_Team_Guide.md`, which is what
it always was.

## Known contradictions in the older PDFs

Where documents disagree, the formal SRS plus the amendment pack wins. Everything
in the `.md` files above is already corrected.

| Topic | Older PDFs say | Correct |
|---|---|---|
| Login | "Passwordless everywhere (email + OTP), including admins" | Password for Super Admin, Campus Admin, Guard. Password or OTP for Faculty, Student. OTP only for Visitor |
| Roles | Five, with a single "Admin" | Six, with Super Admin and Campus Admin split |
| Guard scanning | "select entry/exit" | Entry only. No exit scan, no toggle |
| Departments | Fixed institutional course list | Campus-supplied data, created by each Campus Admin |
| Semester rationale | An institution convention | Not needed for access control |
| Service count | Five backend services | Six backend services |
| Pass states | Pending, Active, Expired, Revoked | Adds `PAUSED` for sensitive profile edits |
| Amber | Undefined, "as configured" | Campus config key `repeat_entry_result` |
| Screens | 16 | 20 — adds Blocklist, Audit Log, Campus Settings, Super Admin Console |
| Java package | Institution-derived | `com.perimity.*` |
