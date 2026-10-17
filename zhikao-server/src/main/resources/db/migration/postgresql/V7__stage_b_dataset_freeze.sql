-- Sign-off freezes dataset membership. Editing the gold text requires new signatures,
-- but can never move a reviewed source from holdout into development or vice versa.
ALTER TABLE quality_annotation ADD COLUMN dataset_frozen INTEGER NOT NULL DEFAULT 0;
UPDATE quality_annotation SET dataset_frozen=1 WHERE status IN ('FIRST_REVIEW','VERIFIED');
