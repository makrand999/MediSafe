-- Medac initial schema — Phase 1-2
-- Covers §6.1-6.11: all tables, FKs, unique constraints, indexes.
-- UUID: app generates UUIDv7 via `uuid` npm (v7 when available); DB default is gen_random_uuid() (v4) as fallback.
-- Timestamps: all are timestamptz; created_at/updated_at maintained by trigger where appropriate.

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- helper: generic updated_at trigger
CREATE OR REPLACE FUNCTION set_updated_at() RETURNS TRIGGER AS $$
BEGIN
  NEW.updated_at = now();
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- =========================================================
-- ENUMS
-- =========================================================
DO $$ BEGIN CREATE TYPE user_status AS ENUM ('pending','active','locked','disabled','deletion_pending'); EXCEPTION WHEN duplicate_object THEN NULL; END $$;
DO $$ BEGIN CREATE TYPE patient_role AS ENUM ('owner','manager','contributor','viewer'); EXCEPTION WHEN duplicate_object THEN NULL; END $$;
DO $$ BEGIN CREATE TYPE membership_status AS ENUM ('active','revoked'); EXCEPTION WHEN duplicate_object THEN NULL; END $$;
DO $$ BEGIN CREATE TYPE notification_privacy_mode AS ENUM ('private','generic','detailed'); EXCEPTION WHEN duplicate_object THEN NULL; END $$;
DO $$ BEGIN CREATE TYPE normalization_status AS ENUM ('unresolved','pending','resolved','ambiguous','failed','manually_confirmed'); EXCEPTION WHEN duplicate_object THEN NULL; END $$;
DO $$ BEGIN CREATE TYPE medication_status AS ENUM ('draft','active','paused','discontinued','archived'); EXCEPTION WHEN duplicate_object THEN NULL; END $$;
DO $$ BEGIN CREATE TYPE schedule_type AS ENUM ('fixed_times','elapsed_interval','prn','cyclic','taper'); EXCEPTION WHEN duplicate_object THEN NULL; END $$;
DO $$ BEGIN CREATE TYPE timing_mode AS ENUM ('local_clock','elapsed_interval'); EXCEPTION WHEN duplicate_object THEN NULL; END $$;
DO $$ BEGIN CREATE TYPE ambiguous_time_policy AS ENUM ('earlier','later'); EXCEPTION WHEN duplicate_object THEN NULL; END $$;
DO $$ BEGIN CREATE TYPE pause_reason AS ENUM ('hospital','temporary','other'); EXCEPTION WHEN duplicate_object THEN NULL; END $$;
DO $$ BEGIN CREATE TYPE occurrence_state AS ENUM ('scheduled','due','taken','skipped','missed','cancelled','corrected'); EXCEPTION WHEN duplicate_object THEN NULL; END $$;
DO $$ BEGIN CREATE TYPE dose_event_type AS ENUM ('taken','skipped','snoozed','corrected','cancelled'); EXCEPTION WHEN duplicate_object THEN NULL; END $$;
DO $$ BEGIN CREATE TYPE transaction_type AS ENUM ('fill','dose_consumed','dose_consumption_reversed','manual_adjustment','lost_or_damaged','disposed','transferred'); EXCEPTION WHEN duplicate_object THEN NULL; END $$;
DO $$ BEGIN CREATE TYPE alert_severity AS ENUM ('info','attention','urgent_review','potential_emergency'); EXCEPTION WHEN duplicate_object THEN NULL; END $$;
DO $$ BEGIN CREATE TYPE alert_status AS ENUM ('open','acknowledged','resolved','cancelled'); EXCEPTION WHEN duplicate_object THEN NULL; END $$;
DO $$ BEGIN CREATE TYPE notification_status AS ENUM ('queued','sent','failed','invalid_token','acknowledged'); EXCEPTION WHEN duplicate_object THEN NULL; END $$;
DO $$ BEGIN CREATE TYPE safety_check_result AS ENUM ('clear_in_checked_source','finding','unknown','failed','not_supported'); EXCEPTION WHEN duplicate_object THEN NULL; END $$;
DO $$ BEGIN CREATE TYPE ai_purpose AS ENUM ('label_interpretation','instruction_parse','assistant_turn','schedule_draft','summary','timeline_pattern'); EXCEPTION WHEN duplicate_object THEN NULL; END $$;
DO $$ BEGIN CREATE TYPE ai_provider AS ENUM ('muse_spark'); EXCEPTION WHEN duplicate_object THEN NULL; END $$;
DO $$ BEGIN CREATE TYPE ai_run_status AS ENUM ('started','completed','failed','blocked','cancelled'); EXCEPTION WHEN duplicate_object THEN NULL; END $$;
DO $$ BEGIN CREATE TYPE ai_tool_mode AS ENUM ('read','proposal'); EXCEPTION WHEN duplicate_object THEN NULL; END $$;
DO $$ BEGIN CREATE TYPE ai_tool_call_status AS ENUM ('requested','authorized','executed','rejected','failed'); EXCEPTION WHEN duplicate_object THEN NULL; END $$;
DO $$ BEGIN CREATE TYPE ai_proposal_status AS ENUM ('pending','confirmed','executed','rejected','expired','superseded','failed'); EXCEPTION WHEN duplicate_object THEN NULL; END $$;
DO $$ BEGIN CREATE TYPE device_platform AS ENUM ('ios','android','web','unknown'); EXCEPTION WHEN duplicate_object THEN NULL; END $$;

-- =========================================================
-- 6.1 Identity & Authentication
-- =========================================================
CREATE TABLE users (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  email_normalized text NOT NULL,
  email_display text NOT NULL,
  password_hash text NOT NULL,
  email_verified_at timestamptz,
  status user_status NOT NULL DEFAULT 'pending',
  preferred_locale text NOT NULL DEFAULT 'en',
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  last_login_at timestamptz,
  CONSTRAINT users_email_normalized_check CHECK (char_length(email_normalized) BETWEEN 3 AND 320),
  CONSTRAINT users_email_display_check CHECK (char_length(email_display) BETWEEN 3 AND 320)
);
CREATE UNIQUE INDEX users_email_normalized_unique ON users (email_normalized);
CREATE TRIGGER trg_users_updated_at BEFORE UPDATE ON users FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE user_security (
  user_id uuid PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
  failed_login_count integer NOT NULL DEFAULT 0 CHECK (failed_login_count >= 0),
  locked_until timestamptz,
  password_changed_at timestamptz,
  mfa_required boolean NOT NULL DEFAULT false,
  token_version integer NOT NULL DEFAULT 1 CHECK (token_version >= 1)
);

CREATE TABLE email_verification_tokens (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  token_hash text NOT NULL,
  expires_at timestamptz NOT NULL,
  used_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_email_verification_tokens_user_id ON email_verification_tokens (user_id);
CREATE INDEX idx_email_verification_tokens_token_hash ON email_verification_tokens (token_hash);
CREATE INDEX idx_email_verification_tokens_expires_at ON email_verification_tokens (expires_at);

CREATE TABLE password_reset_tokens (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  token_hash text NOT NULL,
  expires_at timestamptz NOT NULL,
  used_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_password_reset_tokens_user_id ON password_reset_tokens (user_id);
CREATE INDEX idx_password_reset_tokens_token_hash ON password_reset_tokens (token_hash);
CREATE INDEX idx_password_reset_tokens_expires_at ON password_reset_tokens (expires_at);

CREATE TABLE mfa_totp_credentials (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  encrypted_secret text NOT NULL,
  enabled_at timestamptz,
  last_used_step bigint,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX mfa_totp_credentials_user_id_unique ON mfa_totp_credentials (user_id);
CREATE TRIGGER trg_mfa_totp_updated_at BEFORE UPDATE ON mfa_totp_credentials FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE mfa_recovery_codes (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  code_hash text NOT NULL,
  used_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_mfa_recovery_codes_user_id ON mfa_recovery_codes (user_id);

CREATE TABLE devices (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  platform device_platform NOT NULL DEFAULT 'unknown',
  display_name text,
  app_version text,
  last_seen_at timestamptz,
  timezone text,
  notification_permission text,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  revoked_at timestamptz
);
CREATE INDEX idx_devices_user_id ON devices (user_id);
CREATE TRIGGER trg_devices_updated_at BEFORE UPDATE ON devices FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE auth_sessions (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  device_id uuid REFERENCES devices(id) ON DELETE SET NULL,
  refresh_token_hash text NOT NULL,
  refresh_family_id uuid NOT NULL,
  previous_token_hash text,
  created_at timestamptz NOT NULL DEFAULT now(),
  last_used_at timestamptz,
  expires_at timestamptz NOT NULL,
  revoked_at timestamptz,
  created_ip_prefix text,
  user_agent_summary text
);
CREATE INDEX idx_auth_sessions_user_id ON auth_sessions (user_id);
CREATE INDEX idx_auth_sessions_device_id ON auth_sessions (device_id);
CREATE INDEX idx_auth_sessions_refresh_family_id ON auth_sessions (refresh_family_id);
CREATE INDEX idx_auth_sessions_expires_at ON auth_sessions (expires_at);
CREATE UNIQUE INDEX auth_sessions_refresh_token_hash_unique ON auth_sessions (refresh_token_hash);

CREATE TABLE push_tokens (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  device_id uuid NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
  provider text NOT NULL DEFAULT 'fcm',
  token_ciphertext text NOT NULL,
  token_fingerprint text NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  last_success_at timestamptz,
  invalidated_at timestamptz
);
CREATE INDEX idx_push_tokens_device_id ON push_tokens (device_id);
CREATE INDEX idx_push_tokens_fingerprint ON push_tokens (token_fingerprint);
CREATE TRIGGER trg_push_tokens_updated_at BEFORE UPDATE ON push_tokens FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- =========================================================
-- 6.2 Patients & Sharing
-- =========================================================
CREATE TABLE patients (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  display_name text NOT NULL CHECK (char_length(display_name) BETWEEN 1 AND 200),
  date_of_birth date,
  sex_at_birth text CHECK (sex_at_birth IN ('female','male','intersex','unknown') OR sex_at_birth IS NULL),
  preferred_timezone text NOT NULL DEFAULT 'UTC' CHECK (char_length(preferred_timezone) BETWEEN 1 AND 64),
  notification_privacy_mode notification_privacy_mode NOT NULL DEFAULT 'generic',
  created_by_user_id uuid REFERENCES users(id) ON DELETE SET NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  archived_at timestamptz,
  deletion_requested_at timestamptz
);
CREATE INDEX idx_patients_created_by ON patients (created_by_user_id);
CREATE TRIGGER trg_patients_updated_at BEFORE UPDATE ON patients FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE patient_memberships (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  patient_id uuid NOT NULL REFERENCES patients(id) ON DELETE CASCADE,
  user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  role patient_role NOT NULL,
  status membership_status NOT NULL DEFAULT 'active',
  granted_by_user_id uuid REFERENCES users(id) ON DELETE SET NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  revoked_at timestamptz
);
CREATE INDEX idx_patient_memberships_patient_id ON patient_memberships (patient_id);
CREATE INDEX idx_patient_memberships_user_id ON patient_memberships (user_id);
-- Unique active membership per patient/user (partial index)
CREATE UNIQUE INDEX patient_memberships_active_unique ON patient_memberships (patient_id, user_id) WHERE status = 'active';
CREATE TRIGGER trg_patient_memberships_updated_at BEFORE UPDATE ON patient_memberships FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE caregiver_invites (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  patient_id uuid NOT NULL REFERENCES patients(id) ON DELETE CASCADE,
  invited_email_normalized text NOT NULL,
  role patient_role NOT NULL,
  token_hash text NOT NULL,
  invited_by_user_id uuid REFERENCES users(id) ON DELETE SET NULL,
  expires_at timestamptz NOT NULL,
  accepted_at timestamptz,
  revoked_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_caregiver_invites_patient_id ON caregiver_invites (patient_id);
CREATE INDEX idx_caregiver_invites_token_hash ON caregiver_invites (token_hash);
CREATE INDEX idx_caregiver_invites_email ON caregiver_invites (invited_email_normalized);

CREATE TABLE patient_consents (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  patient_id uuid NOT NULL REFERENCES patients(id) ON DELETE CASCADE,
  consent_type text NOT NULL,
  granted_by_user_id uuid REFERENCES users(id) ON DELETE SET NULL,
  scope_json jsonb NOT NULL DEFAULT '{}'::jsonb,
  policy_version text NOT NULL,
  granted_at timestamptz NOT NULL DEFAULT now(),
  revoked_at timestamptz
);
CREATE INDEX idx_patient_consents_patient_id ON patient_consents (patient_id);
CREATE INDEX idx_patient_consents_type ON patient_consents (consent_type);

-- =========================================================
-- 6.3 Medication identity & instructions
-- =========================================================
CREATE TABLE drug_concepts (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  rxnorm_rxcui text,
  ndc text,
  concept_name text NOT NULL,
  term_type text,
  source text,
  source_version text,
  raw_snapshot_json jsonb,
  last_verified_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_drug_concepts_rxcui ON drug_concepts (rxnorm_rxcui);
CREATE INDEX idx_drug_concepts_ndc ON drug_concepts (ndc);
CREATE INDEX idx_drug_concepts_name ON drug_concepts (concept_name);
CREATE TRIGGER trg_drug_concepts_updated_at BEFORE UPDATE ON drug_concepts FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE drug_ingredients (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  rxnorm_rxcui text,
  name text NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_drug_ingredients_name ON drug_ingredients (name);

CREATE TABLE drug_concept_ingredients (
  drug_concept_id uuid NOT NULL REFERENCES drug_concepts(id) ON DELETE CASCADE,
  ingredient_id uuid NOT NULL REFERENCES drug_ingredients(id) ON DELETE CASCADE,
  strength_value numeric(12,4),
  strength_unit text,
  PRIMARY KEY (drug_concept_id, ingredient_id)
);

CREATE TABLE patient_medications (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  patient_id uuid NOT NULL REFERENCES patients(id) ON DELETE CASCADE,
  drug_concept_id uuid REFERENCES drug_concepts(id) ON DELETE SET NULL,
  entered_name text NOT NULL,
  entered_strength_value numeric(12,4),
  entered_strength_unit text,
  form text,
  route text,
  dose_quantity_value numeric(12,4) NOT NULL,
  dose_quantity_unit text NOT NULL,
  indication_text text,
  prescriber_text text,
  pharmacy_text text,
  label_instructions_text text,
  normalization_status normalization_status NOT NULL DEFAULT 'unresolved',
  status medication_status NOT NULL DEFAULT 'draft',
  high_attention_user_flag boolean NOT NULL DEFAULT false,
  start_date date,
  end_date date,
  created_by_user_id uuid REFERENCES users(id) ON DELETE SET NULL,
  updated_by_user_id uuid REFERENCES users(id) ON DELETE SET NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  paused_at timestamptz,
  discontinued_at timestamptz,
  archived_at timestamptz
);
CREATE INDEX idx_patient_medications_patient_id ON patient_medications (patient_id);
CREATE INDEX idx_patient_medications_status ON patient_medications (status);
CREATE INDEX idx_patient_medications_drug_concept ON patient_medications (drug_concept_id);
CREATE TRIGGER trg_patient_medications_updated_at BEFORE UPDATE ON patient_medications FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE medication_expirations (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  medication_id uuid NOT NULL REFERENCES patient_medications(id) ON DELETE CASCADE,
  expiration_date date NOT NULL,
  lot_number text,
  quantity_value numeric(12,4),
  quantity_unit text,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_medication_expirations_medication_id ON medication_expirations (medication_id);
CREATE INDEX idx_medication_expirations_date ON medication_expirations (expiration_date);
CREATE TRIGGER trg_medication_expirations_updated_at BEFORE UPDATE ON medication_expirations FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- =========================================================
-- 6.4 Schedules
-- =========================================================
CREATE TABLE medication_schedule_versions (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  medication_id uuid NOT NULL REFERENCES patient_medications(id) ON DELETE CASCADE,
  version_number integer NOT NULL CHECK (version_number >= 1),
  schedule_type schedule_type NOT NULL,
  timing_mode timing_mode NOT NULL,
  timezone text NOT NULL CHECK (char_length(timezone) BETWEEN 1 AND 64),
  effective_from timestamptz NOT NULL,
  effective_until timestamptz,
  miss_window_minutes integer CHECK (miss_window_minutes IS NULL OR miss_window_minutes >= 0),
  created_by_user_id uuid REFERENCES users(id) ON DELETE SET NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT medication_schedule_versions_effective_check CHECK (effective_until IS NULL OR effective_until > effective_from),
  UNIQUE (medication_id, version_number)
);
CREATE INDEX idx_med_schedule_versions_medication_id ON medication_schedule_versions (medication_id);
CREATE INDEX idx_med_schedule_versions_effective ON medication_schedule_versions (effective_from, effective_until);
CREATE TRIGGER trg_med_schedule_versions_updated_at BEFORE UPDATE ON medication_schedule_versions FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE schedule_fixed_times (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  schedule_version_id uuid NOT NULL REFERENCES medication_schedule_versions(id) ON DELETE CASCADE,
  local_time time NOT NULL,
  days_of_week integer[] ,
  dose_quantity_value numeric(12,4),
  dose_quantity_unit text,
  ambiguous_time_policy ambiguous_time_policy NOT NULL DEFAULT 'earlier'
);
CREATE INDEX idx_schedule_fixed_times_version_id ON schedule_fixed_times (schedule_version_id);

CREATE TABLE schedule_intervals (
  schedule_version_id uuid PRIMARY KEY REFERENCES medication_schedule_versions(id) ON DELETE CASCADE,
  interval_minutes integer NOT NULL CHECK (interval_minutes > 0),
  anchor_at timestamptz NOT NULL,
  dose_quantity_value numeric(12,4),
  dose_quantity_unit text,
  anchor_policy text NOT NULL DEFAULT 'fixed_anchor' CHECK (anchor_policy IN ('fixed_anchor','last_taken'))
);

CREATE TABLE schedule_cycles (
  schedule_version_id uuid PRIMARY KEY REFERENCES medication_schedule_versions(id) ON DELETE CASCADE,
  cycle_anchor_date date NOT NULL,
  on_days integer NOT NULL CHECK (on_days > 0),
  off_days integer NOT NULL CHECK (off_days >= 0)
);

CREATE TABLE taper_steps (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  schedule_version_id uuid NOT NULL REFERENCES medication_schedule_versions(id) ON DELETE CASCADE,
  step_order integer NOT NULL CHECK (step_order >= 1),
  starts_on date NOT NULL,
  ends_on date,
  dose_quantity_value numeric(12,4) NOT NULL,
  dose_quantity_unit text NOT NULL,
  UNIQUE (schedule_version_id, step_order),
  CONSTRAINT taper_steps_date_check CHECK (ends_on IS NULL OR ends_on >= starts_on)
);
CREATE INDEX idx_taper_steps_version_id ON taper_steps (schedule_version_id);

CREATE TABLE taper_step_fixed_times (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  taper_step_id uuid NOT NULL REFERENCES taper_steps(id) ON DELETE CASCADE,
  local_time time NOT NULL,
  days_of_week integer[],
  ambiguous_time_policy ambiguous_time_policy NOT NULL DEFAULT 'earlier'
);
CREATE INDEX idx_taper_step_fixed_times_step_id ON taper_step_fixed_times (taper_step_id);

CREATE TABLE medication_pause_periods (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  medication_id uuid NOT NULL REFERENCES patient_medications(id) ON DELETE CASCADE,
  starts_at timestamptz NOT NULL,
  ends_at timestamptz,
  reason pause_reason NOT NULL DEFAULT 'temporary',
  created_by_user_id uuid REFERENCES users(id) ON DELETE SET NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT pause_period_check CHECK (ends_at IS NULL OR ends_at > starts_at)
);
CREATE INDEX idx_pause_periods_medication_id ON medication_pause_periods (medication_id);
CREATE INDEX idx_pause_periods_starts_at ON medication_pause_periods (starts_at);

-- =========================================================
-- 6.5 Occurrences & Events
-- =========================================================
CREATE TABLE dose_occurrences (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  patient_id uuid NOT NULL REFERENCES patients(id) ON DELETE CASCADE,
  medication_id uuid NOT NULL REFERENCES patient_medications(id) ON DELETE CASCADE,
  schedule_version_id uuid NOT NULL REFERENCES medication_schedule_versions(id) ON DELETE CASCADE,
  scheduled_at_utc timestamptz NOT NULL,
  scheduled_local_datetime timestamp NOT NULL,
  timezone text NOT NULL,
  utc_offset_minutes integer NOT NULL,
  dst_adjusted boolean NOT NULL DEFAULT false,
  nominal_dose_value numeric(12,4),
  nominal_dose_unit text,
  state occurrence_state NOT NULL DEFAULT 'scheduled',
  generated_at timestamptz NOT NULL DEFAULT now(),
  state_updated_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE (schedule_version_id, scheduled_at_utc)
);
CREATE INDEX idx_dose_occurrences_patient_id ON dose_occurrences (patient_id);
CREATE INDEX idx_dose_occurrences_medication_id ON dose_occurrences (medication_id);
CREATE INDEX idx_dose_occurrences_scheduled_at ON dose_occurrences (scheduled_at_utc);
CREATE INDEX idx_dose_occurrences_state ON dose_occurrences (state);
CREATE TRIGGER trg_dose_occurrences_updated_at BEFORE UPDATE ON dose_occurrences FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE dose_event_logs (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  patient_id uuid NOT NULL REFERENCES patients(id) ON DELETE CASCADE,
  medication_id uuid NOT NULL REFERENCES patient_medications(id) ON DELETE CASCADE,
  occurrence_id uuid REFERENCES dose_occurrences(id) ON DELETE SET NULL,
  event_type dose_event_type NOT NULL,
  actual_at timestamptz NOT NULL,
  recorded_at timestamptz NOT NULL DEFAULT now(),
  timezone text,
  dose_value numeric(12,4),
  dose_unit text,
  note_ciphertext text,
  client_event_id text NOT NULL,
  device_id uuid REFERENCES devices(id) ON DELETE SET NULL,
  actor_user_id uuid REFERENCES users(id) ON DELETE SET NULL,
  corrects_event_id uuid REFERENCES dose_event_logs(id) ON DELETE SET NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT dose_event_no_self_correction CHECK (corrects_event_id IS NULL OR corrects_event_id != id)
);
CREATE UNIQUE INDEX dose_event_logs_device_client_unique ON dose_event_logs (device_id, client_event_id) WHERE device_id IS NOT NULL;
CREATE UNIQUE INDEX dose_event_logs_client_event_unique_fallback ON dose_event_logs (client_event_id, patient_id) WHERE device_id IS NULL;
CREATE INDEX idx_dose_event_logs_patient_id ON dose_event_logs (patient_id);
CREATE INDEX idx_dose_event_logs_medication_id ON dose_event_logs (medication_id);
CREATE INDEX idx_dose_event_logs_occurrence_id ON dose_event_logs (occurrence_id);
CREATE INDEX idx_dose_event_logs_actual_at ON dose_event_logs (actual_at);

CREATE TABLE dose_snoozes (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  occurrence_id uuid NOT NULL REFERENCES dose_occurrences(id) ON DELETE CASCADE,
  snooze_until timestamptz NOT NULL,
  actor_user_id uuid REFERENCES users(id) ON DELETE SET NULL,
  created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_dose_snoozes_occurrence_id ON dose_snoozes (occurrence_id);

-- =========================================================
-- 6.6 Inventory
-- =========================================================
CREATE TABLE inventory_accounts (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  patient_id uuid NOT NULL REFERENCES patients(id) ON DELETE CASCADE,
  medication_id uuid NOT NULL REFERENCES patient_medications(id) ON DELETE CASCADE,
  unit text NOT NULL,
  low_stock_threshold_value numeric(12,4),
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  archived_at timestamptz,
  UNIQUE (patient_id, medication_id, unit)
);
CREATE INDEX idx_inventory_accounts_patient_id ON inventory_accounts (patient_id);
CREATE TRIGGER trg_inventory_accounts_updated_at BEFORE UPDATE ON inventory_accounts FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE inventory_transactions (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  inventory_account_id uuid NOT NULL REFERENCES inventory_accounts(id) ON DELETE CASCADE,
  transaction_type transaction_type NOT NULL,
  quantity_delta numeric(12,4) NOT NULL,
  dose_event_id uuid REFERENCES dose_event_logs(id) ON DELETE SET NULL,
  reason_text text,
  actor_user_id uuid REFERENCES users(id) ON DELETE SET NULL,
  effective_at timestamptz NOT NULL DEFAULT now(),
  created_at timestamptz NOT NULL DEFAULT now(),
  reverses_transaction_id uuid REFERENCES inventory_transactions(id) ON DELETE SET NULL
);
CREATE INDEX idx_inventory_transactions_account_id ON inventory_transactions (inventory_account_id);
CREATE INDEX idx_inventory_transactions_effective_at ON inventory_transactions (effective_at);
CREATE UNIQUE INDEX inventory_transactions_dose_event_unique ON inventory_transactions (dose_event_id) WHERE dose_event_id IS NOT NULL;

-- =========================================================
-- 6.7 Notes & Injections
-- =========================================================
CREATE TABLE symptom_logs (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  patient_id uuid NOT NULL REFERENCES patients(id) ON DELETE CASCADE,
  occurred_at timestamptz NOT NULL,
  note_ciphertext text,
  linked_dose_event_id uuid REFERENCES dose_event_logs(id) ON DELETE SET NULL,
  actor_user_id uuid REFERENCES users(id) ON DELETE SET NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  corrected_by_log_id uuid REFERENCES symptom_logs(id) ON DELETE SET NULL
);
CREATE INDEX idx_symptom_logs_patient_id ON symptom_logs (patient_id);
CREATE INDEX idx_symptom_logs_occurred_at ON symptom_logs (occurred_at);
CREATE TRIGGER trg_symptom_logs_updated_at BEFORE UPDATE ON symptom_logs FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE injection_site_logs (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  patient_id uuid NOT NULL REFERENCES patients(id) ON DELETE CASCADE,
  medication_id uuid NOT NULL REFERENCES patient_medications(id) ON DELETE CASCADE,
  dose_event_id uuid REFERENCES dose_event_logs(id) ON DELETE SET NULL,
  site_code text NOT NULL,
  occurred_at timestamptz NOT NULL,
  actor_user_id uuid REFERENCES users(id) ON DELETE SET NULL,
  created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_injection_site_logs_patient_id ON injection_site_logs (patient_id);
CREATE INDEX idx_injection_site_logs_medication_id ON injection_site_logs (medication_id);
CREATE INDEX idx_injection_site_logs_dose_event_id ON injection_site_logs (dose_event_id);

-- =========================================================
-- 6.8 Alerts & Delivery
-- =========================================================
CREATE TABLE alert_preferences (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  patient_id uuid NOT NULL REFERENCES patients(id) ON DELETE CASCADE,
  user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  alert_type text NOT NULL,
  enabled boolean NOT NULL DEFAULT true,
  delay_minutes integer CHECK (delay_minutes IS NULL OR delay_minutes >= 0),
  quiet_hours_start time,
  quiet_hours_end time,
  timezone text,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE (patient_id, user_id, alert_type)
);
CREATE INDEX idx_alert_preferences_patient_id ON alert_preferences (patient_id);
CREATE TRIGGER trg_alert_preferences_updated_at BEFORE UPDATE ON alert_preferences FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE alerts (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  patient_id uuid NOT NULL REFERENCES patients(id) ON DELETE CASCADE,
  alert_type text NOT NULL,
  severity alert_severity NOT NULL DEFAULT 'info',
  status alert_status NOT NULL DEFAULT 'open',
  source_entity_type text,
  source_entity_id uuid,
  message_key text NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  acknowledged_at timestamptz,
  resolved_at timestamptz
);
CREATE INDEX idx_alerts_patient_id ON alerts (patient_id);
CREATE INDEX idx_alerts_status ON alerts (status);
CREATE INDEX idx_alerts_type ON alerts (alert_type);
CREATE TRIGGER trg_alerts_updated_at BEFORE UPDATE ON alerts FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE notification_deliveries (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  alert_id uuid NOT NULL REFERENCES alerts(id) ON DELETE CASCADE,
  user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  device_id uuid REFERENCES devices(id) ON DELETE SET NULL,
  channel text NOT NULL DEFAULT 'push',
  provider_message_id text,
  status notification_status NOT NULL DEFAULT 'queued',
  attempt_count integer NOT NULL DEFAULT 0 CHECK (attempt_count >= 0),
  next_attempt_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  sent_at timestamptz,
  failed_at timestamptz,
  failure_code text
);
CREATE INDEX idx_notification_deliveries_alert_id ON notification_deliveries (alert_id);
CREATE INDEX idx_notification_deliveries_user_id ON notification_deliveries (user_id);
CREATE INDEX idx_notification_deliveries_status ON notification_deliveries (status);
CREATE TRIGGER trg_notification_deliveries_updated_at BEFORE UPDATE ON notification_deliveries FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- =========================================================
-- 6.9 Safety scaffolding
-- =========================================================
CREATE TABLE clinical_source_versions (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  source_name text NOT NULL,
  source_version text NOT NULL,
  license_reference text,
  effective_at timestamptz NOT NULL DEFAULT now(),
  retired_at timestamptz,
  checksum text,
  UNIQUE (source_name, source_version)
);

CREATE TABLE safety_checks (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  patient_id uuid NOT NULL REFERENCES patients(id) ON DELETE CASCADE,
  check_type text NOT NULL,
  result safety_check_result NOT NULL,
  source_version_id uuid REFERENCES clinical_source_versions(id) ON DELETE SET NULL,
  input_fingerprint text NOT NULL,
  checked_at timestamptz NOT NULL DEFAULT now(),
  expires_at timestamptz
);
CREATE INDEX idx_safety_checks_patient_id ON safety_checks (patient_id);
CREATE INDEX idx_safety_checks_type ON safety_checks (check_type);

CREATE TABLE safety_findings (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  safety_check_id uuid NOT NULL REFERENCES safety_checks(id) ON DELETE CASCADE,
  severity text NOT NULL,
  finding_code text NOT NULL,
  structured_details_json jsonb,
  source_citation text
);
CREATE INDEX idx_safety_findings_check_id ON safety_findings (safety_check_id);

-- =========================================================
-- 6.10 Muse Spark intelligence & agent state
-- =========================================================
CREATE TABLE ai_runs (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  patient_id uuid REFERENCES patients(id) ON DELETE SET NULL,
  user_id uuid REFERENCES users(id) ON DELETE SET NULL,
  session_id uuid,
  purpose ai_purpose NOT NULL,
  provider ai_provider NOT NULL DEFAULT 'muse_spark',
  model text,
  prompt_template_id text,
  prompt_template_version text,
  status ai_run_status NOT NULL DEFAULT 'started',
  input_fingerprint text,
  output_contract_version text,
  input_tokens integer CHECK (input_tokens IS NULL OR input_tokens >= 0),
  output_tokens integer CHECK (output_tokens IS NULL OR output_tokens >= 0),
  latency_ms integer CHECK (latency_ms IS NULL OR latency_ms >= 0),
  safety_policy_version text,
  failure_code text,
  created_at timestamptz NOT NULL DEFAULT now(),
  finished_at timestamptz
);
CREATE INDEX idx_ai_runs_patient_id ON ai_runs (patient_id);
CREATE INDEX idx_ai_runs_user_id ON ai_runs (user_id);
CREATE INDEX idx_ai_runs_purpose ON ai_runs (purpose);

CREATE TABLE ai_tool_calls (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  ai_run_id uuid NOT NULL REFERENCES ai_runs(id) ON DELETE CASCADE,
  patient_id uuid REFERENCES patients(id) ON DELETE SET NULL,
  tool_name text NOT NULL,
  tool_version text,
  mode ai_tool_mode NOT NULL,
  arguments_fingerprint text,
  status ai_tool_call_status NOT NULL DEFAULT 'requested',
  result_classification text,
  created_at timestamptz NOT NULL DEFAULT now(),
  finished_at timestamptz
);
CREATE INDEX idx_ai_tool_calls_run_id ON ai_tool_calls (ai_run_id);
CREATE INDEX idx_ai_tool_calls_tool_name ON ai_tool_calls (tool_name);

CREATE TABLE ai_action_proposals (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  ai_run_id uuid REFERENCES ai_runs(id) ON DELETE SET NULL,
  patient_id uuid NOT NULL REFERENCES patients(id) ON DELETE CASCADE,
  proposed_by_user_id uuid REFERENCES users(id) ON DELETE SET NULL,
  action_type text NOT NULL,
  payload_ciphertext text NOT NULL,
  payload_hash text NOT NULL,
  human_summary_ciphertext text,
  required_permission text NOT NULL,
  resource_versions_json jsonb NOT NULL DEFAULT '{}'::jsonb,
  status ai_proposal_status NOT NULL DEFAULT 'pending',
  expires_at timestamptz NOT NULL,
  confirmed_by_user_id uuid REFERENCES users(id) ON DELETE SET NULL,
  confirmed_at timestamptz,
  executed_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_ai_proposals_patient_id ON ai_action_proposals (patient_id);
CREATE INDEX idx_ai_proposals_status ON ai_action_proposals (status);
CREATE INDEX idx_ai_proposals_expires_at ON ai_action_proposals (expires_at);
CREATE TRIGGER trg_ai_proposals_updated_at BEFORE UPDATE ON ai_action_proposals FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE ai_conversations (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  patient_id uuid NOT NULL REFERENCES patients(id) ON DELETE CASCADE,
  user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  title_ciphertext text,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  expires_at timestamptz
);
CREATE INDEX idx_ai_conversations_patient_id ON ai_conversations (patient_id);
CREATE TRIGGER trg_ai_conversations_updated_at BEFORE UPDATE ON ai_conversations FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE ai_messages (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  conversation_id uuid NOT NULL REFERENCES ai_conversations(id) ON DELETE CASCADE,
  ai_run_id uuid REFERENCES ai_runs(id) ON DELETE SET NULL,
  role text NOT NULL CHECK (role IN ('user','assistant','system','tool')),
  content_ciphertext text NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  expires_at timestamptz
);
CREATE INDEX idx_ai_messages_conversation_id ON ai_messages (conversation_id);

-- =========================================================
-- 6.11 Operations & Audit
-- =========================================================
CREATE TABLE audit_events (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  occurred_at timestamptz NOT NULL DEFAULT now(),
  actor_user_id uuid REFERENCES users(id) ON DELETE SET NULL,
  actor_session_id uuid REFERENCES auth_sessions(id) ON DELETE SET NULL,
  patient_id uuid REFERENCES patients(id) ON DELETE SET NULL,
  action text NOT NULL,
  entity_type text NOT NULL,
  entity_id uuid,
  request_id text,
  ip_prefix text,
  metadata_json jsonb NOT NULL DEFAULT '{}'::jsonb
);
CREATE INDEX idx_audit_events_occurred_at ON audit_events (occurred_at);
CREATE INDEX idx_audit_events_actor_user_id ON audit_events (actor_user_id);
CREATE INDEX idx_audit_events_patient_id ON audit_events (patient_id);
CREATE INDEX idx_audit_events_action ON audit_events (action);
CREATE INDEX idx_audit_events_entity ON audit_events (entity_type, entity_id);

CREATE TABLE outbox_events (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  event_type text NOT NULL,
  aggregate_type text NOT NULL,
  aggregate_id uuid NOT NULL,
  payload_json jsonb NOT NULL,
  available_at timestamptz NOT NULL DEFAULT now(),
  attempt_count integer NOT NULL DEFAULT 0 CHECK (attempt_count >= 0),
  locked_until timestamptz,
  processed_at timestamptz,
  last_error_code text,
  created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_outbox_events_available_at ON outbox_events (available_at) WHERE processed_at IS NULL;
CREATE INDEX idx_outbox_events_event_type ON outbox_events (event_type);
CREATE INDEX idx_outbox_events_aggregate ON outbox_events (aggregate_type, aggregate_id);

CREATE TABLE job_runs (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  job_type text NOT NULL,
  status text NOT NULL CHECK (status IN ('pending','running','completed','failed','dead_letter')),
  started_at timestamptz,
  finished_at timestamptz,
  items_processed integer NOT NULL DEFAULT 0 CHECK (items_processed >= 0),
  items_failed integer NOT NULL DEFAULT 0 CHECK (items_failed >= 0),
  error_code text,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_job_runs_job_type ON job_runs (job_type);
CREATE INDEX idx_job_runs_status ON job_runs (status);
CREATE TRIGGER trg_job_runs_updated_at BEFORE UPDATE ON job_runs FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- Migration tracking table (used by scripts/migrate.ts)
CREATE TABLE IF NOT EXISTS schema_migrations (
  id serial PRIMARY KEY,
  filename text NOT NULL UNIQUE,
  applied_at timestamptz NOT NULL DEFAULT now()
);
