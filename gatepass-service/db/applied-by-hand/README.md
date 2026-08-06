# Migrations that predate Flyway

These three files were written before this service had Flyway, and they never
ran automatically. They sat in `gatepass-service/db/migration`, which is
**outside `src/main/resources`** — so Flyway would not have found them even
after it was added. Whoever needed them applied them by hand, on each machine
separately, and `V3__mask_stored_id_numbers.sql` is the one that had to be run
manually on more than one laptop before visitor requests would stop failing.

They are kept here rather than deleted because they are the only written record
of *why* three of those columns look the way they do.

## They are not migrations any more

Everything in them is already present in
`src/main/resources/db/migration/V1__baseline.sql`, which was captured with
`pg_dump` from a database that had all three applied. Running them again would
fail — they are `ALTER TABLE` statements against columns that already exist in
the shape they describe.

The version numbers also collide. `V1__baseline.sql` is the real V1 now.

## Where new migrations go

`gatepass-service/src/main/resources/db/migration/`, starting at `V2`.

Nothing in this folder should ever be moved back there.
