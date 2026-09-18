/**
 * Drizzle schema — mirrors migrations/0001_initial.up.sql
 * UUIDs: app generates UUIDv7 (via uuidv7() helper) when creating rows; DB fallback is gen_random_uuid() (v4).
 * Timestamps: timestamptz via `timestamp({ withTimezone: true })`.
 */
import {
  pgTable,
  pgEnum,
  uuid,
  text,
  timestamp,
  date,
  time,
  integer,
  numeric,
  boolean,
  jsonb,
  index,
  uniqueIndex,
  primaryKey,
} from 'drizzle-orm/pg-core';
import { sql } from 'drizzle-orm';

// ── Enums ───────────────────────────────────────────────────────────────
export const userStatusEnum = pgEnum('user_status', ['pending', 'active', 'locked', 'disabled', 'deletion_pending']);
export const patientRoleEnum = pgEnum('patient_role', ['owner', 'manager', 'contributor', 'viewer']);
export const membershipStatusEnum = pgEnum('membership_status', ['active', 'revoked']);
export const notificationPrivacyModeEnum = pgEnum('notification_privacy_mode', ['private', 'generic', 'detailed']);
export const normalizationStatusEnum = pgEnum('normalization_status', [
  'unresolved',
  'pending',
  'resolved',
  'ambiguous',
  'failed',
  'manually_confirmed',
]);
export const medicationStatusEnum = pgEnum('medication_status', ['draft', 'active', 'paused', 'discontinued', 'archived']);
export const scheduleTypeEnum = pgEnum('schedule_type', ['fixed_times', 'elapsed_interval', 'prn', 'cyclic', 'taper']);
export const timingModeEnum = pgEnum('timing_mode', ['local_clock', 'elapsed_interval']);
export const ambiguousTimePolicyEnum = pgEnum('ambiguous_time_policy', ['earlier', 'later']);
export const pauseReasonEnum = pgEnum('pause_reason', ['hospital', 'temporary', 'other']);
export const occurrenceStateEnum = pgEnum('occurrence_state', [
  'scheduled',
  'due',
  'taken',
  'skipped',
  'missed',
  'cancelled',
  'corrected',
]);
export const doseEventTypeEnum = pgEnum('dose_event_type', ['taken', 'skipped', 'snoozed', 'corrected', 'cancelled']);
export const transactionTypeEnum = pgEnum('transaction_type', [
  'fill',
  'dose_consumed',
  'dose_consumption_reversed',
  'manual_adjustment',
  'lost_or_damaged',
  'disposed',
  'transferred',
]);
export const alertSeverityEnum = pgEnum('alert_severity', ['info', 'attention', 'urgent_review', 'potential_emergency']);
export const alertStatusEnum = pgEnum('alert_status', ['open', 'acknowledged', 'resolved', 'cancelled']);
export const notificationStatusEnum = pgEnum('notification_status', [
  'queued',
  'sent',
  'failed',
  'invalid_token',
  'acknowledged',
]);
export const safetyCheckResultEnum = pgEnum('safety_check_result', [
  'clear_in_checked_source',
  'finding',
  'unknown',
  'failed',
  'not_supported',
]);
export const aiPurposeEnum = pgEnum('ai_purpose', [
  'label_interpretation',
  'instruction_parse',
  'assistant_turn',
  'schedule_draft',
  'summary',
  'timeline_pattern',
]);
export const aiProviderEnum = pgEnum('ai_provider', ['muse_spark']);
export const aiRunStatusEnum = pgEnum('ai_run_status', ['started', 'completed', 'failed', 'blocked', 'cancelled']);
export const aiToolModeEnum = pgEnum('ai_tool_mode', ['read', 'proposal']);
export const aiToolCallStatusEnum = pgEnum('ai_tool_call_status', ['requested', 'authorized', 'executed', 'rejected', 'failed']);
export const aiProposalStatusEnum = pgEnum('ai_proposal_status', [
  'pending',
  'confirmed',
  'executed',
  'rejected',
  'expired',
  'superseded',
  'failed',
]);
export const devicePlatformEnum = pgEnum('device_platform', ['ios', 'android', 'web', 'unknown']);

// helper for common timestamps
const createdAt = timestamp('created_at', { withTimezone: true }).notNull().defaultNow();
const updatedAt = timestamp('updated_at', { withTimezone: true }).notNull().defaultNow();

// ── 6.1 Identity ─────────────────────────────────────────────────────
export const users = pgTable(
  'users',
  {
    id: uuid('id').primaryKey().defaultRandom(),
    emailNormalized: text('email_normalized').notNull(),
    emailDisplay: text('email_display').notNull(),
    passwordHash: text('password_hash').notNull(),
    emailVerifiedAt: timestamp('email_verified_at', { withTimezone: true }),
    status: userStatusEnum('status').notNull().default('pending'),
    preferredLocale: text('preferred_locale').notNull().default('en'),
    createdAt,
    updatedAt,
    lastLoginAt: timestamp('last_login_at', { withTimezone: true }),
  },
  (t) => [uniqueIndex('users_email_normalized_unique').on(t.emailNormalized)],
);

export const userSecurity = pgTable('user_security', {
  userId: uuid('user_id')
    .primaryKey()
    .references(() => users.id, { onDelete: 'cascade' }),
  failedLoginCount: integer('failed_login_count').notNull().default(0),
  lockedUntil: timestamp('locked_until', { withTimezone: true }),
  passwordChangedAt: timestamp('password_changed_at', { withTimezone: true }),
  mfaRequired: boolean('mfa_required').notNull().default(false),
  tokenVersion: integer('token_version').notNull().default(1),
});

export const emailVerificationTokens = pgTable(
  'email_verification_tokens',
  {
    id: uuid('id').primaryKey().defaultRandom(),
    userId: uuid('user_id')
      .notNull()
      .references(() => users.id, { onDelete: 'cascade' }),
    tokenHash: text('token_hash').notNull(),
    expiresAt: timestamp('expires_at', { withTimezone: true }).notNull(),
    usedAt: timestamp('used_at', { withTimezone: true }),
    createdAt: timestamp('created_at', { withTimezone: true }).notNull().defaultNow(),
  },
  (t) => [
    index('idx_email_verification_tokens_user_id').on(t.userId),
    index('idx_email_verification_tokens_token_hash').on(t.tokenHash),
  ],
);

export const passwordResetTokens = pgTable(
  'password_reset_tokens',
  {
    id: uuid('id').primaryKey().defaultRandom(),
    userId: uuid('user_id')
      .notNull()
      .references(() => users.id, { onDelete: 'cascade' }),
    tokenHash: text('token_hash').notNull(),
    expiresAt: timestamp('expires_at', { withTimezone: true }).notNull(),
    usedAt: timestamp('used_at', { withTimezone: true }),
    createdAt: timestamp('created_at', { withTimezone: true }).notNull().defaultNow(),
  },
  (t) => [index('idx_password_reset_tokens_user_id').on(t.userId)],
);

export const mfaTotpCredentials = pgTable(
  'mfa_totp_credentials',
  {
    id: uuid('id').primaryKey().defaultRandom(),
    userId: uuid('user_id')
      .notNull()
      .references(() => users.id, { onDelete: 'cascade' }),
    encryptedSecret: text('encrypted_secret').notNull(),
    enabledAt: timestamp('enabled_at', { withTimezone: true }),
    lastUsedStep: integer('last_used_step'), // bigint in SQL; drizzle integer covers 32-bit — use numeric? keep integer mapped to bigint via sql
    createdAt: timestamp('created_at', { withTimezone: true }).notNull().defaultNow(),
    updatedAt,
  },
  (t) => [uniqueIndex('mfa_totp_credentials_user_id_unique').on(t.userId)],
);

export const mfaRecoveryCodes = pgTable('mfa_recovery_codes', {
  id: uuid('id').primaryKey().defaultRandom(),
  userId: uuid('user_id')
    .notNull()
    .references(() => users.id, { onDelete: 'cascade' }),
  codeHash: text('code_hash').notNull(),
  usedAt: timestamp('used_at', { withTimezone: true }),
  createdAt: timestamp('created_at', { withTimezone: true }).notNull().defaultNow(),
});

export const devices = pgTable(
  'devices',
  {
    id: uuid('id').primaryKey().defaultRandom(),
    userId: uuid('user_id')
      .notNull()
      .references(() => users.id, { onDelete: 'cascade' }),
    platform: devicePlatformEnum('platform').notNull().default('unknown'),
    displayName: text('display_name'),
    appVersion: text('app_version'),
    lastSeenAt: timestamp('last_seen_at', { withTimezone: true }),
    timezone: text('timezone'),
    notificationPermission: text('notification_permission'),
    createdAt,
    updatedAt,
    revokedAt: timestamp('revoked_at', { withTimezone: true }),
  },
  (t) => [index('idx_devices_user_id').on(t.userId)],
);

export const authSessions = pgTable(
  'auth_sessions',
  {
    id: uuid('id').primaryKey().defaultRandom(),
    userId: uuid('user_id')
      .notNull()
      .references(() => users.id, { onDelete: 'cascade' }),
    deviceId: uuid('device_id').references(() => devices.id, { onDelete: 'set null' }),
    refreshTokenHash: text('refresh_token_hash').notNull(),
    refreshFamilyId: uuid('refresh_family_id').notNull(),
    previousTokenHash: text('previous_token_hash'),
    createdAt: timestamp('created_at', { withTimezone: true }).notNull().defaultNow(),
    lastUsedAt: timestamp('last_used_at', { withTimezone: true }),
    expiresAt: timestamp('expires_at', { withTimezone: true }).notNull(),
    revokedAt: timestamp('revoked_at', { withTimezone: true }),
    createdIpPrefix: text('created_ip_prefix'),
    userAgentSummary: text('user_agent_summary'),
  },
  (t) => [uniqueIndex('auth_sessions_refresh_token_hash_unique').on(t.refreshTokenHash)],
);

export const pushTokens = pgTable('push_tokens', {
  id: uuid('id').primaryKey().defaultRandom(),
  deviceId: uuid('device_id')
    .notNull()
    .references(() => devices.id, { onDelete: 'cascade' }),
  provider: text('provider').notNull().default('fcm'),
  tokenCiphertext: text('token_ciphertext').notNull(),
  tokenFingerprint: text('token_fingerprint').notNull(),
  createdAt: timestamp('created_at', { withTimezone: true }).notNull().defaultNow(),
  updatedAt,
  lastSuccessAt: timestamp('last_success_at', { withTimezone: true }),
  invalidatedAt: timestamp('invalidated_at', { withTimezone: true }),
});

// ── 6.2 Patients & Sharing ───────────────────────────────────────────
export const patients = pgTable('patients', {
  id: uuid('id').primaryKey().defaultRandom(),
  displayName: text('display_name').notNull(),
  dateOfBirth: date('date_of_birth'),
  sexAtBirth: text('sex_at_birth'),
  preferredTimezone: text('preferred_timezone').notNull().default('UTC'),
  notificationPrivacyMode: notificationPrivacyModeEnum('notification_privacy_mode').notNull().default('generic'),
  createdByUserId: uuid('created_by_user_id').references(() => users.id, { onDelete: 'set null' }),
  createdAt,
  updatedAt,
  archivedAt: timestamp('archived_at', { withTimezone: true }),
  deletionRequestedAt: timestamp('deletion_requested_at', { withTimezone: true }),
});

export const patientMemberships = pgTable('patient_memberships', {
  id: uuid('id').primaryKey().defaultRandom(),
  patientId: uuid('patient_id')
    .notNull()
    .references(() => patients.id, { onDelete: 'cascade' }),
  userId: uuid('user_id')
    .notNull()
    .references(() => users.id, { onDelete: 'cascade' }),
  role: patientRoleEnum('role').notNull(),
  status: membershipStatusEnum('status').notNull().default('active'),
  grantedByUserId: uuid('granted_by_user_id').references(() => users.id, { onDelete: 'set null' }),
  createdAt: timestamp('created_at', { withTimezone: true }).notNull().defaultNow(),
  updatedAt,
  revokedAt: timestamp('revoked_at', { withTimezone: true }),
});

export const caregiverInvites = pgTable('caregiver_invites', {
  id: uuid('id').primaryKey().defaultRandom(),
  patientId: uuid('patient_id')
    .notNull()
    .references(() => patients.id, { onDelete: 'cascade' }),
  invitedEmailNormalized: text('invited_email_normalized').notNull(),
  role: patientRoleEnum('role').notNull(),
  tokenHash: text('token_hash').notNull(),
  invitedByUserId: uuid('invited_by_user_id').references(() => users.id, { onDelete: 'set null' }),
  expiresAt: timestamp('expires_at', { withTimezone: true }).notNull(),
  acceptedAt: timestamp('accepted_at', { withTimezone: true }),
  revokedAt: timestamp('revoked_at', { withTimezone: true }),
  createdAt: timestamp('created_at', { withTimezone: true }).notNull().defaultNow(),
});

export const patientConsents = pgTable('patient_consents', {
  id: uuid('id').primaryKey().defaultRandom(),
  patientId: uuid('patient_id')
    .notNull()
    .references(() => patients.id, { onDelete: 'cascade' }),
  consentType: text('consent_type').notNull(),
  grantedByUserId: uuid('granted_by_user_id').references(() => users.id, { onDelete: 'set null' }),
  scopeJson: jsonb('scope_json').notNull().default(sql`'{}'::jsonb`),
  policyVersion: text('policy_version').notNull(),
  grantedAt: timestamp('granted_at', { withTimezone: true }).notNull().defaultNow(),
  revokedAt: timestamp('revoked_at', { withTimezone: true }),
});

// ── 6.3 Drug concepts & medications ──────────────────────────────────
export const drugConcepts = pgTable('drug_concepts', {
  id: uuid('id').primaryKey().defaultRandom(),
  rxnormRxcui: text('rxnorm_rxcui'),
  ndc: text('ndc'),
  conceptName: text('concept_name').notNull(),
  termType: text('term_type'),
  source: text('source'),
  sourceVersion: text('source_version'),
  rawSnapshotJson: jsonb('raw_snapshot_json'),
  lastVerifiedAt: timestamp('last_verified_at', { withTimezone: true }),
  createdAt,
  updatedAt,
});

export const drugIngredients = pgTable('drug_ingredients', {
  id: uuid('id').primaryKey().defaultRandom(),
  rxnormRxcui: text('rxnorm_rxcui'),
  name: text('name').notNull(),
  createdAt: timestamp('created_at', { withTimezone: true }).notNull().defaultNow(),
});

export const drugConceptIngredients = pgTable(
  'drug_concept_ingredients',
  {
    drugConceptId: uuid('drug_concept_id')
      .notNull()
      .references(() => drugConcepts.id, { onDelete: 'cascade' }),
    ingredientId: uuid('ingredient_id')
      .notNull()
      .references(() => drugIngredients.id, { onDelete: 'cascade' }),
    strengthValue: numeric('strength_value', { precision: 12, scale: 4 }),
    strengthUnit: text('strength_unit'),
  },
  (t) => [primaryKey({ columns: [t.drugConceptId, t.ingredientId] })],
);

export const patientMedications = pgTable('patient_medications', {
  id: uuid('id').primaryKey().defaultRandom(),
  patientId: uuid('patient_id')
    .notNull()
    .references(() => patients.id, { onDelete: 'cascade' }),
  drugConceptId: uuid('drug_concept_id').references(() => drugConcepts.id, { onDelete: 'set null' }),
  enteredName: text('entered_name').notNull(),
  enteredStrengthValue: numeric('entered_strength_value', { precision: 12, scale: 4 }),
  enteredStrengthUnit: text('entered_strength_unit'),
  form: text('form'),
  route: text('route'),
  doseQuantityValue: numeric('dose_quantity_value', { precision: 12, scale: 4 }).notNull(),
  doseQuantityUnit: text('dose_quantity_unit').notNull(),
  indicationText: text('indication_text'),
  prescriberText: text('prescriber_text'),
  pharmacyText: text('pharmacy_text'),
  labelInstructionsText: text('label_instructions_text'),
  normalizationStatus: normalizationStatusEnum('normalization_status').notNull().default('unresolved'),
  status: medicationStatusEnum('status').notNull().default('draft'),
  highAttentionUserFlag: boolean('high_attention_user_flag').notNull().default(false),
  startDate: date('start_date'),
  endDate: date('end_date'),
  createdByUserId: uuid('created_by_user_id').references(() => users.id, { onDelete: 'set null' }),
  updatedByUserId: uuid('updated_by_user_id').references(() => users.id, { onDelete: 'set null' }),
  createdAt,
  updatedAt,
  pausedAt: timestamp('paused_at', { withTimezone: true }),
  discontinuedAt: timestamp('discontinued_at', { withTimezone: true }),
  archivedAt: timestamp('archived_at', { withTimezone: true }),
});

export const medicationExpirations = pgTable('medication_expirations', {
  id: uuid('id').primaryKey().defaultRandom(),
  medicationId: uuid('medication_id')
    .notNull()
    .references(() => patientMedications.id, { onDelete: 'cascade' }),
  expirationDate: date('expiration_date').notNull(),
  lotNumber: text('lot_number'),
  quantityValue: numeric('quantity_value', { precision: 12, scale: 4 }),
  quantityUnit: text('quantity_unit'),
  createdAt: timestamp('created_at', { withTimezone: true }).notNull().defaultNow(),
  updatedAt,
});

// ── 6.4 Schedules ────────────────────────────────────────────────────
export const medicationScheduleVersions = pgTable(
  'medication_schedule_versions',
  {
    id: uuid('id').primaryKey().defaultRandom(),
    medicationId: uuid('medication_id')
      .notNull()
      .references(() => patientMedications.id, { onDelete: 'cascade' }),
    versionNumber: integer('version_number').notNull(),
    scheduleType: scheduleTypeEnum('schedule_type').notNull(),
    timingMode: timingModeEnum('timing_mode').notNull(),
    timezone: text('timezone').notNull(),
    effectiveFrom: timestamp('effective_from', { withTimezone: true }).notNull(),
    effectiveUntil: timestamp('effective_until', { withTimezone: true }),
    missWindowMinutes: integer('miss_window_minutes'),
    createdByUserId: uuid('created_by_user_id').references(() => users.id, { onDelete: 'set null' }),
    createdAt: timestamp('created_at', { withTimezone: true }).notNull().defaultNow(),
    updatedAt,
  },
  (t) => [uniqueIndex('medication_schedule_versions_med_version_unique').on(t.medicationId, t.versionNumber)],
);

export const scheduleFixedTimes = pgTable('schedule_fixed_times', {
  id: uuid('id').primaryKey().defaultRandom(),
  scheduleVersionId: uuid('schedule_version_id')
    .notNull()
    .references(() => medicationScheduleVersions.id, { onDelete: 'cascade' }),
  localTime: time('local_time').notNull(),
  daysOfWeek: integer('days_of_week').array(),
  doseQuantityValue: numeric('dose_quantity_value', { precision: 12, scale: 4 }),
  doseQuantityUnit: text('dose_quantity_unit'),
  ambiguousTimePolicy: ambiguousTimePolicyEnum('ambiguous_time_policy').notNull().default('earlier'),
});

export const scheduleIntervals = pgTable('schedule_intervals', {
  scheduleVersionId: uuid('schedule_version_id')
    .primaryKey()
    .references(() => medicationScheduleVersions.id, { onDelete: 'cascade' }),
  intervalMinutes: integer('interval_minutes').notNull(),
  anchorAt: timestamp('anchor_at', { withTimezone: true }).notNull(),
  doseQuantityValue: numeric('dose_quantity_value', { precision: 12, scale: 4 }),
  doseQuantityUnit: text('dose_quantity_unit'),
  anchorPolicy: text('anchor_policy').notNull().default('fixed_anchor'),
});

export const scheduleCycles = pgTable('schedule_cycles', {
  scheduleVersionId: uuid('schedule_version_id')
    .primaryKey()
    .references(() => medicationScheduleVersions.id, { onDelete: 'cascade' }),
  cycleAnchorDate: date('cycle_anchor_date').notNull(),
  onDays: integer('on_days').notNull(),
  offDays: integer('off_days').notNull(),
});

export const taperSteps = pgTable(
  'taper_steps',
  {
    id: uuid('id').primaryKey().defaultRandom(),
    scheduleVersionId: uuid('schedule_version_id')
      .notNull()
      .references(() => medicationScheduleVersions.id, { onDelete: 'cascade' }),
    stepOrder: integer('step_order').notNull(),
    startsOn: date('starts_on').notNull(),
    endsOn: date('ends_on'),
    doseQuantityValue: numeric('dose_quantity_value', { precision: 12, scale: 4 }).notNull(),
    doseQuantityUnit: text('dose_quantity_unit').notNull(),
  },
  (t) => [uniqueIndex('taper_steps_version_order_unique').on(t.scheduleVersionId, t.stepOrder)],
);

export const taperStepFixedTimes = pgTable('taper_step_fixed_times', {
  id: uuid('id').primaryKey().defaultRandom(),
  taperStepId: uuid('taper_step_id')
    .notNull()
    .references(() => taperSteps.id, { onDelete: 'cascade' }),
  localTime: time('local_time').notNull(),
  daysOfWeek: integer('days_of_week').array(),
  ambiguousTimePolicy: ambiguousTimePolicyEnum('ambiguous_time_policy').notNull().default('earlier'),
});

export const medicationPausePeriods = pgTable('medication_pause_periods', {
  id: uuid('id').primaryKey().defaultRandom(),
  medicationId: uuid('medication_id')
    .notNull()
    .references(() => patientMedications.id, { onDelete: 'cascade' }),
  startsAt: timestamp('starts_at', { withTimezone: true }).notNull(),
  endsAt: timestamp('ends_at', { withTimezone: true }),
  reason: pauseReasonEnum('reason').notNull().default('temporary'),
  createdByUserId: uuid('created_by_user_id').references(() => users.id, { onDelete: 'set null' }),
  createdAt: timestamp('created_at', { withTimezone: true }).notNull().defaultNow(),
});

// ── 6.5 Occurrences & Events ─────────────────────────────────────────
export const doseOccurrences = pgTable(
  'dose_occurrences',
  {
    id: uuid('id').primaryKey().defaultRandom(),
    patientId: uuid('patient_id')
      .notNull()
      .references(() => patients.id, { onDelete: 'cascade' }),
    medicationId: uuid('medication_id')
      .notNull()
      .references(() => patientMedications.id, { onDelete: 'cascade' }),
    scheduleVersionId: uuid('schedule_version_id')
      .notNull()
      .references(() => medicationScheduleVersions.id, { onDelete: 'cascade' }),
    scheduledAtUtc: timestamp('scheduled_at_utc', { withTimezone: true }).notNull(),
    scheduledLocalDatetime: timestamp('scheduled_local_datetime', { withTimezone: false }).notNull(),
    timezone: text('timezone').notNull(),
    utcOffsetMinutes: integer('utc_offset_minutes').notNull(),
    dstAdjusted: boolean('dst_adjusted').notNull().default(false),
    nominalDoseValue: numeric('nominal_dose_value', { precision: 12, scale: 4 }),
    nominalDoseUnit: text('nominal_dose_unit'),
    state: occurrenceStateEnum('state').notNull().default('scheduled'),
    generatedAt: timestamp('generated_at', { withTimezone: true }).notNull().defaultNow(),
    stateUpdatedAt: timestamp('state_updated_at', { withTimezone: true }),
    createdAt: timestamp('created_at', { withTimezone: true }).notNull().defaultNow(),
    updatedAt,
  },
  (t) => [uniqueIndex('dose_occurrences_version_instant_unique').on(t.scheduleVersionId, t.scheduledAtUtc)],
);

export const doseEventLogs = pgTable('dose_event_logs', {
  id: uuid('id').primaryKey().defaultRandom(),
  patientId: uuid('patient_id')
    .notNull()
    .references(() => patients.id, { onDelete: 'cascade' }),
  medicationId: uuid('medication_id')
    .notNull()
    .references(() => patientMedications.id, { onDelete: 'cascade' }),
  occurrenceId: uuid('occurrence_id').references(() => doseOccurrences.id, { onDelete: 'set null' }),
  eventType: doseEventTypeEnum('event_type').notNull(),
  actualAt: timestamp('actual_at', { withTimezone: true }).notNull(),
  recordedAt: timestamp('recorded_at', { withTimezone: true }).notNull().defaultNow(),
  timezone: text('timezone'),
  doseValue: numeric('dose_value', { precision: 12, scale: 4 }),
  doseUnit: text('dose_unit'),
  noteCiphertext: text('note_ciphertext'),
  clientEventId: text('client_event_id').notNull(),
  deviceId: uuid('device_id').references(() => devices.id, { onDelete: 'set null' }),
  actorUserId: uuid('actor_user_id').references(() => users.id, { onDelete: 'set null' }),
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  correctsEventId: uuid('corrects_event_id').references((): any => doseEventLogs.id, { onDelete: 'set null' }),
  createdAt: timestamp('created_at', { withTimezone: true }).notNull().defaultNow(),
});

export const doseSnoozes = pgTable('dose_snoozes', {
  id: uuid('id').primaryKey().defaultRandom(),
  occurrenceId: uuid('occurrence_id')
    .notNull()
    .references(() => doseOccurrences.id, { onDelete: 'cascade' }),
  snoozeUntil: timestamp('snooze_until', { withTimezone: true }).notNull(),
  actorUserId: uuid('actor_user_id').references(() => users.id, { onDelete: 'set null' }),
  createdAt: timestamp('created_at', { withTimezone: true }).notNull().defaultNow(),
});

// ── 6.6 Inventory ────────────────────────────────────────────────────
export const inventoryAccounts = pgTable(
  'inventory_accounts',
  {
    id: uuid('id').primaryKey().defaultRandom(),
    patientId: uuid('patient_id')
      .notNull()
      .references(() => patients.id, { onDelete: 'cascade' }),
    medicationId: uuid('medication_id')
      .notNull()
      .references(() => patientMedications.id, { onDelete: 'cascade' }),
    unit: text('unit').notNull(),
    lowStockThresholdValue: numeric('low_stock_threshold_value', { precision: 12, scale: 4 }),
    createdAt: timestamp('created_at', { withTimezone: true }).notNull().defaultNow(),
    updatedAt,
    archivedAt: timestamp('archived_at', { withTimezone: true }),
  },
  (t) => [uniqueIndex('inventory_accounts_patient_med_unit_unique').on(t.patientId, t.medicationId, t.unit)],
);

export const inventoryTransactions = pgTable('inventory_transactions', {
  id: uuid('id').primaryKey().defaultRandom(),
  inventoryAccountId: uuid('inventory_account_id')
    .notNull()
    .references(() => inventoryAccounts.id, { onDelete: 'cascade' }),
  transactionType: transactionTypeEnum('transaction_type').notNull(),
  quantityDelta: numeric('quantity_delta', { precision: 12, scale: 4 }).notNull(),
  doseEventId: uuid('dose_event_id').references(() => doseEventLogs.id, { onDelete: 'set null' }),
  reasonText: text('reason_text'),
  actorUserId: uuid('actor_user_id').references(() => users.id, { onDelete: 'set null' }),
  effectiveAt: timestamp('effective_at', { withTimezone: true }).notNull().defaultNow(),
  createdAt: timestamp('created_at', { withTimezone: true }).notNull().defaultNow(),
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  reversesTransactionId: uuid('reverses_transaction_id').references((): any => inventoryTransactions.id, {
    onDelete: 'set null',
  }),
});

// ── 6.7 Notes & Injection ────────────────────────────────────────────
export const symptomLogs = pgTable('symptom_logs', {
  id: uuid('id').primaryKey().defaultRandom(),
  patientId: uuid('patient_id')
    .notNull()
    .references(() => patients.id, { onDelete: 'cascade' }),
  occurredAt: timestamp('occurred_at', { withTimezone: true }).notNull(),
  noteCiphertext: text('note_ciphertext'),
  linkedDoseEventId: uuid('linked_dose_event_id').references(() => doseEventLogs.id, { onDelete: 'set null' }),
  actorUserId: uuid('actor_user_id').references(() => users.id, { onDelete: 'set null' }),
  createdAt: timestamp('created_at', { withTimezone: true }).notNull().defaultNow(),
  updatedAt,
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  correctedByLogId: uuid('corrected_by_log_id').references((): any => symptomLogs.id, { onDelete: 'set null' }),
});

export const injectionSiteLogs = pgTable('injection_site_logs', {
  id: uuid('id').primaryKey().defaultRandom(),
  patientId: uuid('patient_id')
    .notNull()
    .references(() => patients.id, { onDelete: 'cascade' }),
  medicationId: uuid('medication_id')
    .notNull()
    .references(() => patientMedications.id, { onDelete: 'cascade' }),
  doseEventId: uuid('dose_event_id').references(() => doseEventLogs.id, { onDelete: 'set null' }),
  siteCode: text('site_code').notNull(),
  occurredAt: timestamp('occurred_at', { withTimezone: true }).notNull(),
  actorUserId: uuid('actor_user_id').references(() => users.id, { onDelete: 'set null' }),
  createdAt: timestamp('created_at', { withTimezone: true }).notNull().defaultNow(),
});

// ── 6.8 Alerts & Delivery ────────────────────────────────────────────
export const alertPreferences = pgTable(
  'alert_preferences',
  {
    id: uuid('id').primaryKey().defaultRandom(),
    patientId: uuid('patient_id')
      .notNull()
      .references(() => patients.id, { onDelete: 'cascade' }),
    userId: uuid('user_id')
      .notNull()
      .references(() => users.id, { onDelete: 'cascade' }),
    alertType: text('alert_type').notNull(),
    enabled: boolean('enabled').notNull().default(true),
    delayMinutes: integer('delay_minutes'),
    quietHoursStart: time('quiet_hours_start'),
    quietHoursEnd: time('quiet_hours_end'),
    timezone: text('timezone'),
    createdAt: timestamp('created_at', { withTimezone: true }).notNull().defaultNow(),
    updatedAt,
  },
  (t) => [uniqueIndex('alert_preferences_patient_user_type_unique').on(t.patientId, t.userId, t.alertType)],
);

export const alerts = pgTable('alerts', {
  id: uuid('id').primaryKey().defaultRandom(),
  patientId: uuid('patient_id')
    .notNull()
    .references(() => patients.id, { onDelete: 'cascade' }),
  alertType: text('alert_type').notNull(),
  severity: alertSeverityEnum('severity').notNull().default('info'),
  status: alertStatusEnum('status').notNull().default('open'),
  sourceEntityType: text('source_entity_type'),
  sourceEntityId: uuid('source_entity_id'),
  messageKey: text('message_key').notNull(),
  createdAt: timestamp('created_at', { withTimezone: true }).notNull().defaultNow(),
  updatedAt,
  acknowledgedAt: timestamp('acknowledged_at', { withTimezone: true }),
  resolvedAt: timestamp('resolved_at', { withTimezone: true }),
});

export const notificationDeliveries = pgTable('notification_deliveries', {
  id: uuid('id').primaryKey().defaultRandom(),
  alertId: uuid('alert_id')
    .notNull()
    .references(() => alerts.id, { onDelete: 'cascade' }),
  userId: uuid('user_id')
    .notNull()
    .references(() => users.id, { onDelete: 'cascade' }),
  deviceId: uuid('device_id').references(() => devices.id, { onDelete: 'set null' }),
  channel: text('channel').notNull().default('push'),
  providerMessageId: text('provider_message_id'),
  status: notificationStatusEnum('status').notNull().default('queued'),
  attemptCount: integer('attempt_count').notNull().default(0),
  nextAttemptAt: timestamp('next_attempt_at', { withTimezone: true }),
  createdAt: timestamp('created_at', { withTimezone: true }).notNull().defaultNow(),
  updatedAt,
  sentAt: timestamp('sent_at', { withTimezone: true }),
  failedAt: timestamp('failed_at', { withTimezone: true }),
  failureCode: text('failure_code'),
});

// ── 6.9 Safety scaffolding ────────────────────────────────────────────
export const clinicalSourceVersions = pgTable(
  'clinical_source_versions',
  {
    id: uuid('id').primaryKey().defaultRandom(),
    sourceName: text('source_name').notNull(),
    sourceVersion: text('source_version').notNull(),
    licenseReference: text('license_reference'),
    effectiveAt: timestamp('effective_at', { withTimezone: true }).notNull().defaultNow(),
    retiredAt: timestamp('retired_at', { withTimezone: true }),
    checksum: text('checksum'),
  },
  (t) => [uniqueIndex('clinical_source_name_version_unique').on(t.sourceName, t.sourceVersion)],
);

export const safetyChecks = pgTable('safety_checks', {
  id: uuid('id').primaryKey().defaultRandom(),
  patientId: uuid('patient_id')
    .notNull()
    .references(() => patients.id, { onDelete: 'cascade' }),
  checkType: text('check_type').notNull(),
  result: safetyCheckResultEnum('result').notNull(),
  sourceVersionId: uuid('source_version_id').references(() => clinicalSourceVersions.id, { onDelete: 'set null' }),
  inputFingerprint: text('input_fingerprint').notNull(),
  checkedAt: timestamp('checked_at', { withTimezone: true }).notNull().defaultNow(),
  expiresAt: timestamp('expires_at', { withTimezone: true }),
});

export const safetyFindings = pgTable('safety_findings', {
  id: uuid('id').primaryKey().defaultRandom(),
  safetyCheckId: uuid('safety_check_id')
    .notNull()
    .references(() => safetyChecks.id, { onDelete: 'cascade' }),
  severity: text('severity').notNull(),
  findingCode: text('finding_code').notNull(),
  structuredDetailsJson: jsonb('structured_details_json'),
  sourceCitation: text('source_citation'),
});

// ── 6.10 Intelligence ─────────────────────────────────────────────────
export const aiRuns = pgTable('ai_runs', {
  id: uuid('id').primaryKey().defaultRandom(),
  patientId: uuid('patient_id').references(() => patients.id, { onDelete: 'set null' }),
  userId: uuid('user_id').references(() => users.id, { onDelete: 'set null' }),
  sessionId: uuid('session_id'),
  purpose: aiPurposeEnum('purpose').notNull(),
  provider: aiProviderEnum('provider').notNull().default('muse_spark'),
  model: text('model'),
  promptTemplateId: text('prompt_template_id'),
  promptTemplateVersion: text('prompt_template_version'),
  status: aiRunStatusEnum('status').notNull().default('started'),
  inputFingerprint: text('input_fingerprint'),
  outputContractVersion: text('output_contract_version'),
  inputTokens: integer('input_tokens'),
  outputTokens: integer('output_tokens'),
  latencyMs: integer('latency_ms'),
  safetyPolicyVersion: text('safety_policy_version'),
  failureCode: text('failure_code'),
  createdAt: timestamp('created_at', { withTimezone: true }).notNull().defaultNow(),
  finishedAt: timestamp('finished_at', { withTimezone: true }),
});

export const aiToolCalls = pgTable('ai_tool_calls', {
  id: uuid('id').primaryKey().defaultRandom(),
  aiRunId: uuid('ai_run_id')
    .notNull()
    .references(() => aiRuns.id, { onDelete: 'cascade' }),
  patientId: uuid('patient_id').references(() => patients.id, { onDelete: 'set null' }),
  toolName: text('tool_name').notNull(),
  toolVersion: text('tool_version'),
  mode: aiToolModeEnum('mode').notNull(),
  argumentsFingerprint: text('arguments_fingerprint'),
  status: aiToolCallStatusEnum('status').notNull().default('requested'),
  resultClassification: text('result_classification'),
  createdAt: timestamp('created_at', { withTimezone: true }).notNull().defaultNow(),
  finishedAt: timestamp('finished_at', { withTimezone: true }),
});

export const aiActionProposals = pgTable('ai_action_proposals', {
  id: uuid('id').primaryKey().defaultRandom(),
  aiRunId: uuid('ai_run_id').references(() => aiRuns.id, { onDelete: 'set null' }),
  patientId: uuid('patient_id')
    .notNull()
    .references(() => patients.id, { onDelete: 'cascade' }),
  proposedByUserId: uuid('proposed_by_user_id').references(() => users.id, { onDelete: 'set null' }),
  actionType: text('action_type').notNull(),
  payloadCiphertext: text('payload_ciphertext').notNull(),
  payloadHash: text('payload_hash').notNull(),
  humanSummaryCiphertext: text('human_summary_ciphertext'),
  requiredPermission: text('required_permission').notNull(),
  resourceVersionsJson: jsonb('resource_versions_json').notNull().default(sql`'{}'::jsonb`),
  status: aiProposalStatusEnum('status').notNull().default('pending'),
  expiresAt: timestamp('expires_at', { withTimezone: true }).notNull(),
  confirmedByUserId: uuid('confirmed_by_user_id').references(() => users.id, { onDelete: 'set null' }),
  confirmedAt: timestamp('confirmed_at', { withTimezone: true }),
  executedAt: timestamp('executed_at', { withTimezone: true }),
  createdAt: timestamp('created_at', { withTimezone: true }).notNull().defaultNow(),
  updatedAt,
});

export const aiConversations = pgTable('ai_conversations', {
  id: uuid('id').primaryKey().defaultRandom(),
  patientId: uuid('patient_id')
    .notNull()
    .references(() => patients.id, { onDelete: 'cascade' }),
  userId: uuid('user_id')
    .notNull()
    .references(() => users.id, { onDelete: 'cascade' }),
  titleCiphertext: text('title_ciphertext'),
  createdAt: timestamp('created_at', { withTimezone: true }).notNull().defaultNow(),
  updatedAt,
  expiresAt: timestamp('expires_at', { withTimezone: true }),
});

export const aiMessages = pgTable('ai_messages', {
  id: uuid('id').primaryKey().defaultRandom(),
  conversationId: uuid('conversation_id')
    .notNull()
    .references(() => aiConversations.id, { onDelete: 'cascade' }),
  aiRunId: uuid('ai_run_id').references(() => aiRuns.id, { onDelete: 'set null' }),
  role: text('role').notNull(),
  contentCiphertext: text('content_ciphertext').notNull(),
  createdAt: timestamp('created_at', { withTimezone: true }).notNull().defaultNow(),
  expiresAt: timestamp('expires_at', { withTimezone: true }),
});

// ── 6.11 Operations & Audit ───────────────────────────────────────────
export const auditEvents = pgTable('audit_events', {
  id: uuid('id').primaryKey().defaultRandom(),
  occurredAt: timestamp('occurred_at', { withTimezone: true }).notNull().defaultNow(),
  actorUserId: uuid('actor_user_id').references(() => users.id, { onDelete: 'set null' }),
  actorSessionId: uuid('actor_session_id').references(() => authSessions.id, { onDelete: 'set null' }),
  patientId: uuid('patient_id').references(() => patients.id, { onDelete: 'set null' }),
  action: text('action').notNull(),
  entityType: text('entity_type').notNull(),
  entityId: uuid('entity_id'),
  requestId: text('request_id'),
  ipPrefix: text('ip_prefix'),
  metadataJson: jsonb('metadata_json').notNull().default(sql`'{}'::jsonb`),
});

export const outboxEvents = pgTable('outbox_events', {
  id: uuid('id').primaryKey().defaultRandom(),
  eventType: text('event_type').notNull(),
  aggregateType: text('aggregate_type').notNull(),
  aggregateId: uuid('aggregate_id').notNull(),
  payloadJson: jsonb('payload_json').notNull(),
  availableAt: timestamp('available_at', { withTimezone: true }).notNull().defaultNow(),
  attemptCount: integer('attempt_count').notNull().default(0),
  lockedUntil: timestamp('locked_until', { withTimezone: true }),
  processedAt: timestamp('processed_at', { withTimezone: true }),
  lastErrorCode: text('last_error_code'),
  createdAt: timestamp('created_at', { withTimezone: true }).notNull().defaultNow(),
});

export const jobRuns = pgTable('job_runs', {
  id: uuid('id').primaryKey().defaultRandom(),
  jobType: text('job_type').notNull(),
  status: text('status').notNull(),
  startedAt: timestamp('started_at', { withTimezone: true }),
  finishedAt: timestamp('finished_at', { withTimezone: true }),
  itemsProcessed: integer('items_processed').notNull().default(0),
  itemsFailed: integer('items_failed').notNull().default(0),
  errorCode: text('error_code'),
  createdAt: timestamp('created_at', { withTimezone: true }).notNull().defaultNow(),
  updatedAt,
});

export const schemaMigrations = pgTable('schema_migrations', {
  id: integer('id').primaryKey().generatedAlwaysAsIdentity(),
  filename: text('filename').notNull().unique(),
  appliedAt: timestamp('applied_at', { withTimezone: true }).notNull().defaultNow(),
});
