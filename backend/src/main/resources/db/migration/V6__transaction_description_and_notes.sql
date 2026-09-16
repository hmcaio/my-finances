-- Adds a mandatory `description` field to `transactions` and narrows the existing `note` column
-- into `additional_notes` (optional, bounded), per the shared DescriptionConstraints convention
-- (MAX_DESCRIPTION_LENGTH = 150, MAX_ADDITIONAL_NOTES_LENGTH = 500) - same convention landing on
-- Transfer (F005) and RecurringTemplate's description (F007) once those are built.
--
-- description is backfilled with '' for any pre-existing row (local single-user dev DB, no real
-- data expected at this point) so the NOT NULL constraint can be added directly, then the default
-- is dropped so future inserts must supply a real value.

ALTER TABLE transactions ADD COLUMN description varchar(150) NOT NULL DEFAULT '';
ALTER TABLE transactions ALTER COLUMN description DROP DEFAULT;

ALTER TABLE transactions RENAME COLUMN note TO additional_notes;
ALTER TABLE transactions ALTER COLUMN additional_notes TYPE varchar(500);
