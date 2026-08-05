# Bulk student onboarding from a Google Form

Faculty shares one form with a student group. Students fill it in, signed into
Google, and upload a passport photo. Faculty exports the responses, uploads the
sheet, and the system creates every account, profile and pass — photo included.

**Status: planned, not built.** This document is the plan.

## The decision that shapes everything else

Rows import as **VERIFIED**, and `verifiedBy` records the **faculty member who
uploaded the file**.

That is deliberate and it is honest. The uploading faculty chooses the file,
sees the parsed rows, and clicks confirm — so a named person did take
responsibility for the batch. It is weaker than reading every row, which is the
trade being made knowingly, but the Verified badge still points at a human.

`verifiedBy` must never be null or a system id here. A verification record that
names nobody is the false attestation the whole feature exists to prevent.

**The form link being forwarded does not matter.** Nothing enters the system
when a student submits. It enters when faculty uploads the sheet. That is the
trust boundary, and it is a person.

Requiring Google sign-in on the form adds a second layer: every response carries
the responder's Google address, so a submission can always be traced back.

## Flow

```
faculty                students              faculty                system
   |                      |                     |                      |
   +-- share form link -->|                     |                      |
   |                      +-- fill + photo ---->|                      |
   |                                            |                      |
   |<----------------- export .xlsx from Forms -+                      |
   |                                                                   |
   +-- upload sheet ------------------------------------------------->|
   |                                                    parse + validate
   |<-------------------------------------------- preview, errors named
   |                                                                   |
   +-- confirm ------------------------------------------------------>|
                                                        create accounts
                                                        create profiles
                                                        fetch photos (Drive)
                                                        mark VERIFIED
                                                        issue passes
                                                        email each student
```

## Form questions

The sheet's columns must match these. Faculty pastes this list into a Google
Form once; the app stores the resulting URL and offers Copy link / Share.

| Question | Type | Required | Maps to |
|---|---|---|---|
| Email address | auto, from Google sign-in | yes | account email |
| Full name | short text | yes | `User.name` |
| First name | short text | yes | `firstName` |
| Middle name | short text | no | `middleName` |
| Last name | short text | yes | `lastName` |
| Date of birth | date | yes | `dateOfBirth` |
| Gender | multiple choice | yes | `gender` |
| Address | paragraph | yes | `address` |
| Phone country code | short text, default +91 | yes | `phoneCountryCode` |
| Phone number | short text | yes | `phoneNumber` |
| Roll number | short text | yes | `rollNo` |
| Department | dropdown, per campus | yes | `departmentId` |
| Passport photo | file upload, images only, 1 file | yes | Drive file id |

No semester. It does not exist in this product and must not be added.

Gender options must be exactly `MALE`, `FEMALE`, `OTHER`, `PREFER_NOT_TO_SAY`
or the import maps them by label — decide one and hold it.

## Why Google Drive API is needed

A file-upload question does not put the image in the .xlsx. It puts a Drive
link. Fetching those bytes needs an authenticated Google client, which is why
this feature has a prerequisite no code can remove.

The alternative considered and rejected: faculty downloads the photo folder from
Drive, zips it, and uploads it alongside the sheet. Fewer moving parts and no
credentials, but an extra manual step every intake.

## Prerequisite — Google Cloud setup

**This is yours to do. Do not send me the credentials, and do not commit them.**

1. Create a Google Cloud project.
2. Enable the **Google Drive API**.
3. Create a **service account**, download its JSON key.
4. Share the Drive folder holding the form's file-upload responses with the
   service account's email, read-only.
5. Put the key somewhere the container can read it and reference it by path:

```
GOOGLE_APPLICATION_CREDENTIALS=/run/secrets/google-drive.json
GOOGLE_DRIVE_ENABLED=true
```

Mount the file as a Docker secret or a bind mount. `.gitignore` must cover it.
The standing rule holds: every secret from the environment, nothing committed.

A service account is right rather than OAuth because nobody is signing in — the
server reads files on its own behalf, unattended.

**Until this exists, build and test everything else with
`GOOGLE_DRIVE_ENABLED=false`**, which imports every row and leaves the photo
blank. Those students then get the normal in-app prompt to add one, and no pass
issues until they do. The pipeline must work in that mode anyway, because a
Drive outage must not strand a whole intake.

## Which service owns this

**user-service.** It owns student profiles, the verification state machine, and
the storage abstraction the photos will land in. It calls auth-service to create
accounts and gatepass-service to issue passes, over the existing internal API.

The alternative was gatepass-service, where visitor bulk upload already lives.
Rejected because that pipeline creates VISITOR accounts and passes and never
touches a student profile — the overlap is the spreadsheet parsing, not the
domain. Reuse `SheetParser` by copying it; do not make one service depend on the
other's bulk engine.

## Progress

**Done:**

- `ImportBatchStatus`, `ImportRowOutcome` enums
- `StudentImportBatch`, `StudentImportRow` entities + repositories
- `FormColumn` — fuzzy header matching, so reordering a form question does not
  break the import
- `ResponseSheetParser` — POI, every cell read as text, Drive ids extracted
- POI 5.3.0 added to user-service, version-locked to gatepass-service

**Next, in order:**

- `ImportRowValidator` — same rules as `StudentSelfDetailsDto`, plus roll-number
  uniqueness and department resolution
- `StudentImportService` — validate pass, then confirm pass
- Controller: upload, preview, confirm, progress
- Drive photo fetch behind `GOOGLE_DRIVE_ENABLED`
- Passes and email
- Faculty screens

**Not compiled yet.** POI is newly added to user-service, so the first
`docker compose build user-service` after this is the real test.

## Build order

Each step is testable on its own. Do not start the next until the current one
runs against the real stack.

1. **Batch model** — `StudentImportBatch` entity, status enum
   (`VALIDATING`, `VALIDATED`, `PROCESSING`, `COMPLETED`, `FAILED`), row-level
   results. Mirrors the existing bulk batch so the progress screen feels the
   same.
2. **Parse and validate** — accept .xlsx, map columns, validate every row
   against `ValidationPatterns` and the same rules as
   `StudentSelfDetailsDto`. Return a preview naming every bad row and why.
   Nothing is written yet.
3. **Confirm** — create accounts through auth-service, let the `user.created`
   event provision the profiles, then fill in the details and set
   `verificationStatus = VERIFIED` with `verifiedBy` = the uploader.
4. **Drive photos** — fetch each file id, validate it really is an image by
   magic bytes, store it, set `photoS3Key`. Behind `GOOGLE_DRIVE_ENABLED`.
5. **Passes and email** — issue a pass per student and email it with the photo
   on it. Reuses the existing QR pipeline.
6. **Faculty screens** — form question list and URL config, upload, preview
   with per-row errors, progress.

## Things that will bite

**One bad row must not fail the batch.** Same rule the visitor bulk engine and
the expiry sweep already follow. Report the row, keep going.

**Re-uploading the same sheet.** Students resubmit forms. Match on email and
update rather than creating a second account — and never a second pass.

**Roll number collisions.** Unique per campus. Two students typing the same one
is a row error, not a batch failure.

**Departments are per-campus data.** A dropdown value has to resolve to a
department id on *this* campus, or the row fails with something readable.

**Photos are not optional at the gate.** A student imported with
`GOOGLE_DRIVE_ENABLED=false` has no photo, so no pass. Say so on the progress
screen rather than issuing a pass with no face.

**The email is self-declared.** Google sign-in proves they control that
address, which is exactly why the form should require it.
