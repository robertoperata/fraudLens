--liquibase formatted sql

--changeset fraudlens:002-add-event-created-at
ALTER TABLE events
    ADD COLUMN created_at TIMESTAMPTZ NOT NULL DEFAULT NOW();
