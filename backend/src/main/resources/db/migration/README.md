# Flyway migration policy

`B40__baseline.sql` is the complete schema baseline for a new, empty database.
Flyway applies it instead of `V1` through `V40`, then applies every later
versioned migration normally.

The `V*.sql` files are intentionally retained and must never be edited or
removed: deployed Neon, Cloud Run, local, and test databases record their
checksums in `flyway_schema_history`.

When a schema change is needed, add a new `V41__...sql` migration. When a new
baseline is needed in the future, generate it from the complete ordered V-file
history and name it with the latest version, for example `B41__baseline.sql`.
