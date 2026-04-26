-- =============================================================================
-- V1__initial_schema.sql
-- JRostering — baseline schema
-- All tables are created in dependency order (parents before children).
-- The self-referential FK on ROSTER_PERIOD is added via ALTER TABLE after the
-- table is created to avoid a forward-reference.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- ORGANISATION
-- -----------------------------------------------------------------------------
CREATE TABLE organisation (
    id         BIGSERIAL    PRIMARY KEY,
    name       VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL,
    updated_at TIMESTAMPTZ  NOT NULL
);

-- -----------------------------------------------------------------------------
-- SITE
-- -----------------------------------------------------------------------------
CREATE TABLE site (
    id              BIGSERIAL    PRIMARY KEY,
    organisation_id BIGINT       NOT NULL REFERENCES organisation(id),
    name            VARCHAR(255) NOT NULL,
    timezone        VARCHAR(100) NOT NULL,
    address         VARCHAR(500),
    active          BOOLEAN      NOT NULL DEFAULT true,
    created_at      TIMESTAMPTZ  NOT NULL,
    updated_at      TIMESTAMPTZ  NOT NULL
);

-- -----------------------------------------------------------------------------
-- QUALIFICATION
-- Reference data scoped to an organisation (e.g. First Aid, Fire Warden).
-- -----------------------------------------------------------------------------
CREATE TABLE qualification (
    id              BIGSERIAL    PRIMARY KEY,
    organisation_id BIGINT       NOT NULL REFERENCES organisation(id),
    name            VARCHAR(255) NOT NULL,
    description     TEXT,
    created_at      TIMESTAMPTZ  NOT NULL,
    updated_at      TIMESTAMPTZ  NOT NULL,

    CONSTRAINT uq_qualification_org_name UNIQUE (organisation_id, name)
);

-- -----------------------------------------------------------------------------
-- SHIFT_TYPE
-- Shift classification reference data scoped to a site (e.g. Morning, Night).
-- Provides a stable reference for PREFERRED_SHIFT_TYPE / AVOID_SHIFT_TYPE rules.
-- -----------------------------------------------------------------------------
CREATE TABLE shift_type (
    id         BIGSERIAL    PRIMARY KEY,
    site_id    BIGINT       NOT NULL REFERENCES site(id),
    name       VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL,
    updated_at TIMESTAMPTZ  NOT NULL,

    CONSTRAINT uq_shift_type_site_name UNIQUE (site_id, name)
);

-- -----------------------------------------------------------------------------
-- APP_USER
-- Roster managers authorised to access the application.
-- Passwords are stored as bcrypt hashes.
-- -----------------------------------------------------------------------------
CREATE TABLE app_user (
    id            BIGSERIAL    PRIMARY KEY,
    username      VARCHAR(100) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    active        BOOLEAN      NOT NULL DEFAULT true,
    created_at    TIMESTAMPTZ  NOT NULL,
    updated_at    TIMESTAMPTZ  NOT NULL,

    CONSTRAINT uq_app_user_username UNIQUE (username)
);

-- -----------------------------------------------------------------------------
-- STAFF
-- -----------------------------------------------------------------------------
CREATE TABLE staff (
    id                        BIGSERIAL      PRIMARY KEY,
    organisation_id           BIGINT         NOT NULL REFERENCES organisation(id),
    first_name                VARCHAR(100)   NOT NULL,
    last_name                 VARCHAR(100)   NOT NULL,
    email                     VARCHAR(255)   NOT NULL,
    phone                     VARCHAR(50),
    employment_type           VARCHAR(50)    NOT NULL,  -- FULL_TIME | PART_TIME | CASUAL
    contracted_hours_per_week DECIMAL(5,2),
    hourly_rate               DECIMAL(10,2),
    active                    BOOLEAN        NOT NULL DEFAULT true,
    created_at                TIMESTAMPTZ    NOT NULL,
    updated_at                TIMESTAMPTZ    NOT NULL,

    CONSTRAINT uq_staff_org_email UNIQUE (organisation_id, email)
);

-- -----------------------------------------------------------------------------
-- STAFF_QUALIFICATION
-- -----------------------------------------------------------------------------
CREATE TABLE staff_qualification (
    id               BIGSERIAL   PRIMARY KEY,
    staff_id         BIGINT      NOT NULL REFERENCES staff(id),
    qualification_id BIGINT      NOT NULL REFERENCES qualification(id),
    awarded_date     DATE,
    created_at       TIMESTAMPTZ NOT NULL,

    CONSTRAINT uq_staff_qualification UNIQUE (staff_id, qualification_id)
);

-- -----------------------------------------------------------------------------
-- STAFF_SITE_ASSIGNMENT
-- Links a staff member to one or more sites. primary_site = true for their
-- primary location.
-- -----------------------------------------------------------------------------
CREATE TABLE staff_site_assignment (
    id           BIGSERIAL   PRIMARY KEY,
    staff_id     BIGINT      NOT NULL REFERENCES staff(id),
    site_id      BIGINT      NOT NULL REFERENCES site(id),
    primary_site BOOLEAN     NOT NULL DEFAULT false,
    created_at   TIMESTAMPTZ NOT NULL,

    CONSTRAINT uq_staff_site UNIQUE (staff_id, site_id)
);

-- -----------------------------------------------------------------------------
-- STAFF_INCOMPATIBILITY
-- Two staff members who must never share a shift.
-- Canonical ordering (staff_a_id < staff_b_id) prevents duplicate pairs.
-- -----------------------------------------------------------------------------
CREATE TABLE staff_incompatibility (
    id          BIGSERIAL   PRIMARY KEY,
    staff_a_id  BIGINT      NOT NULL REFERENCES staff(id),
    staff_b_id  BIGINT      NOT NULL REFERENCES staff(id),
    reason      TEXT,
    created_at  TIMESTAMPTZ NOT NULL,

    CONSTRAINT uq_staff_incompatibility UNIQUE (staff_a_id, staff_b_id),
    CONSTRAINT chk_staff_incompatibility_order CHECK (staff_a_id < staff_b_id)
);

-- -----------------------------------------------------------------------------
-- STAFF_PAIRING
-- Two staff members who must always share a shift.
-- Canonical ordering (staff_a_id < staff_b_id) prevents duplicate pairs.
-- -----------------------------------------------------------------------------
CREATE TABLE staff_pairing (
    id          BIGSERIAL   PRIMARY KEY,
    staff_a_id  BIGINT      NOT NULL REFERENCES staff(id),
    staff_b_id  BIGINT      NOT NULL REFERENCES staff(id),
    reason      TEXT,
    created_at  TIMESTAMPTZ NOT NULL,

    CONSTRAINT uq_staff_pairing UNIQUE (staff_a_id, staff_b_id),
    CONSTRAINT chk_staff_pairing_order CHECK (staff_a_id < staff_b_id)
);

-- -----------------------------------------------------------------------------
-- STAFF_AVAILABILITY
-- Recurring weekly availability windows. day_of_week stores Java DayOfWeek
-- names: MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY, SUNDAY.
-- -----------------------------------------------------------------------------
CREATE TABLE staff_availability (
    id          BIGSERIAL   PRIMARY KEY,
    staff_id    BIGINT      NOT NULL REFERENCES staff(id),
    day_of_week VARCHAR(20) NOT NULL,
    start_time  TIME        NOT NULL,
    end_time    TIME        NOT NULL,
    available   BOOLEAN     NOT NULL DEFAULT true,
    created_at  TIMESTAMPTZ NOT NULL
);

-- -----------------------------------------------------------------------------
-- STAFF_PREFERENCE
-- Scheduling preferences used by PREFERRED_DAYS_OFF and PREFERRED_SHIFT_TYPE
-- soft constraint rules. Exactly one of day_of_week or shift_type_id is
-- populated, enforced by the CHECK constraint below.
-- -----------------------------------------------------------------------------
CREATE TABLE staff_preference (
    id              BIGSERIAL   PRIMARY KEY,
    staff_id        BIGINT      NOT NULL REFERENCES staff(id),
    preference_type VARCHAR(50) NOT NULL,  -- PREFERRED_DAY_OFF | PREFERRED_SHIFT_TYPE | AVOID_SHIFT_TYPE
    day_of_week     VARCHAR(20),           -- populated for PREFERRED_DAY_OFF
    shift_type_id   BIGINT      REFERENCES shift_type(id),  -- populated for shift type preferences
    created_at      TIMESTAMPTZ NOT NULL,

    CONSTRAINT chk_staff_preference_fields CHECK (
        (preference_type = 'PREFERRED_DAY_OFF'
            AND day_of_week IS NOT NULL
            AND shift_type_id IS NULL)
        OR
        (preference_type IN ('PREFERRED_SHIFT_TYPE', 'AVOID_SHIFT_TYPE')
            AND shift_type_id IS NOT NULL
            AND day_of_week IS NULL)
    )
);

-- -----------------------------------------------------------------------------
-- LEAVE
-- end_date must not be before start_date.
-- -----------------------------------------------------------------------------
CREATE TABLE leave (
    id          BIGSERIAL   PRIMARY KEY,
    staff_id    BIGINT      NOT NULL REFERENCES staff(id),
    start_date  DATE        NOT NULL,
    end_date    DATE        NOT NULL,
    leave_type  VARCHAR(50) NOT NULL,  -- ANNUAL | SICK | PUBLIC_HOLIDAY | OTHER
    status      VARCHAR(50) NOT NULL,  -- REQUESTED | APPROVED | REJECTED
    notes       TEXT,
    created_at  TIMESTAMPTZ NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL,

    CONSTRAINT chk_leave_dates CHECK (end_date >= start_date)
);

-- -----------------------------------------------------------------------------
-- RULE_CONFIGURATION
-- One row per rule type per site.
-- constraint_level replaces the original is_hard BOOLEAN to support
-- HARD / MEDIUM / SOFT classification.
-- enabled is the sole activation switch; parameter_json stores {} for
-- parameterless rules.
-- -----------------------------------------------------------------------------
CREATE TABLE rule_configuration (
    id               BIGSERIAL    PRIMARY KEY,
    site_id          BIGINT       NOT NULL REFERENCES site(id),
    rule_type        VARCHAR(100) NOT NULL,
    enabled          BOOLEAN      NOT NULL DEFAULT true,
    constraint_level VARCHAR(10)  NOT NULL,
    weight           INTEGER,
    parameter_json   JSONB        NOT NULL DEFAULT '{}',
    created_at       TIMESTAMPTZ  NOT NULL,
    updated_at       TIMESTAMPTZ  NOT NULL,

    CONSTRAINT uq_rule_configuration_site_type UNIQUE (site_id, rule_type),
    CONSTRAINT chk_rule_configuration_level    CHECK (constraint_level IN ('HARD', 'MEDIUM', 'SOFT'))
);

-- -----------------------------------------------------------------------------
-- ROSTER_PERIOD
-- A 14-day planning period. previous_period_id is the authoritative link
-- between sequential periods; sequence_number is a display convenience only.
-- The self-referential FK is added below via ALTER TABLE.
-- -----------------------------------------------------------------------------
CREATE TABLE roster_period (
    id                 BIGSERIAL   PRIMARY KEY,
    site_id            BIGINT      NOT NULL REFERENCES site(id),
    previous_period_id BIGINT,     -- FK added below after table creation
    start_date         DATE        NOT NULL,
    end_date           DATE        NOT NULL,
    status             VARCHAR(50) NOT NULL DEFAULT 'DRAFT',
    sequence_number    INTEGER     NOT NULL,
    published_at       TIMESTAMPTZ,
    created_at         TIMESTAMPTZ NOT NULL,
    updated_at         TIMESTAMPTZ NOT NULL,

    CONSTRAINT chk_roster_period_dates CHECK (end_date = start_date + INTERVAL '13 days')
);

-- Self-referential FK added after table creation to avoid forward-reference error
ALTER TABLE roster_period
    ADD CONSTRAINT fk_roster_period_previous
        FOREIGN KEY (previous_period_id) REFERENCES roster_period(id);

-- -----------------------------------------------------------------------------
-- SHIFT
-- shift_type_id is optional; required for PREFERRED_SHIFT_TYPE constraint.
-- -----------------------------------------------------------------------------
CREATE TABLE shift (
    id               BIGSERIAL    PRIMARY KEY,
    roster_period_id BIGINT       NOT NULL REFERENCES roster_period(id),
    shift_type_id    BIGINT       REFERENCES shift_type(id),
    name             VARCHAR(255),
    start_datetime   TIMESTAMPTZ  NOT NULL,
    end_datetime     TIMESTAMPTZ  NOT NULL,
    minimum_staff    INTEGER      NOT NULL DEFAULT 1,
    notes            TEXT,
    created_at       TIMESTAMPTZ  NOT NULL,
    updated_at       TIMESTAMPTZ  NOT NULL,

    CONSTRAINT chk_shift_datetimes CHECK (end_datetime > start_datetime)
);

-- -----------------------------------------------------------------------------
-- SHIFT_QUALIFICATION_REQUIREMENT
-- minimum_count must be at least 1.
-- -----------------------------------------------------------------------------
CREATE TABLE shift_qualification_requirement (
    id               BIGSERIAL PRIMARY KEY,
    shift_id         BIGINT    NOT NULL REFERENCES shift(id),
    qualification_id BIGINT    NOT NULL REFERENCES qualification(id),
    minimum_count    INTEGER   NOT NULL DEFAULT 1,

    CONSTRAINT uq_shift_qualification_req UNIQUE (shift_id, qualification_id),
    CONSTRAINT chk_sqr_minimum_count      CHECK (minimum_count >= 1)
);

-- -----------------------------------------------------------------------------
-- SHIFT_ASSIGNMENT
-- The Timefold planning entity. staff_id is NULL before solving or on
-- unresolvable slots in an INFEASIBLE result.
-- On CANCELLED solves, the best solution found is written and preserved.
-- -----------------------------------------------------------------------------
CREATE TABLE shift_assignment (
    id         BIGSERIAL   PRIMARY KEY,
    shift_id   BIGINT      NOT NULL REFERENCES shift(id),
    staff_id   BIGINT      REFERENCES staff(id),  -- NULL = unassigned
    pinned     BOOLEAN     NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

-- -----------------------------------------------------------------------------
-- SOLVER_JOB
-- Tracks the lifecycle of each asynchronous solve run.
-- final_score is populated on COMPLETED and INFEASIBLE; may contain negative
-- hard values on INFEASIBLE (e.g. '-2hard/0medium/-10soft').
-- -----------------------------------------------------------------------------
CREATE TABLE solver_job (
    id                   BIGSERIAL    PRIMARY KEY,
    roster_period_id     BIGINT       NOT NULL REFERENCES roster_period(id),
    status               VARCHAR(50)  NOT NULL DEFAULT 'QUEUED',
    started_at           TIMESTAMPTZ,
    completed_at         TIMESTAMPTZ,
    time_limit_seconds   INTEGER      NOT NULL,
    final_score          VARCHAR(100),
    infeasible_reason    TEXT,
    error_message        TEXT,
    created_at           TIMESTAMPTZ  NOT NULL,
    updated_at           TIMESTAMPTZ  NOT NULL
);
