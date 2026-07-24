# QR Service

**Owner: Sanjay**

Handles QR tokens and PDF generation jobs. Owns QRDB (PostgreSQL, port 5436).
See `/docs` for the full schema and rules.

This is the most library-heavy backend: RabbitMQ consumer, AES-256 token
encryption, ZXing QR generation, iText PDF generation, and AWS S3 uploads.
Only one frontend screen, to compensate.

## Frontend screens owned

| # | Screen |
|---|---|
| 14 | Pass Download |

Folder: `frontend/src/qr/`

⚠️ Arham's Bulk Progress screen (10) calls this service's
`/api/qr/jobs/batch/{batchId}/progress` endpoint. Have it ready by Day 16.

## Next steps

Once you have Spring Boot code here, add a `Dockerfile` in this folder and
uncomment this service's block in the root `docker-compose.yml`.
