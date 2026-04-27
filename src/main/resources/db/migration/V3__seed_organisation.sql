-- =============================================================================
-- V3__seed_organisation.sql
-- Seeds the single Organisation row required before any other data can be
-- entered.  All Staff, Qualifications, and Sites belong to this organisation.
--
-- The name can be changed by the roster manager via the UI after first login.
-- The id=1 row is referenced nowhere in application code; references flow
-- through the runtime-loaded Organisation entity returned by
-- OrganisationRepository.findAll().getFirst().
-- =============================================================================
INSERT INTO organisation (name, created_at, updated_at)
VALUES ('Default Organisation', NOW(), NOW());
