ALTER TABLE file_event ALTER COLUMN payload TYPE text USING payload::text;
