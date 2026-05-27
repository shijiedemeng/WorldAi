ALTER TABLE requirement_info
    ADD COLUMN review_required_flag BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN review_approved_flag BOOLEAN NOT NULL DEFAULT FALSE;
