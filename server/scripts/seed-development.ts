#!/usr/bin/env tsx
/**
 * Development seed — creates deterministic sample data for local/staging.
 * Safe to re-run: uses upsert/insert-or-skip patterns.
 * Do NOT run against production with real credentials.
 *
 * Creates:
 *  - one user (dev@example.com / password: MedacDev1234!)
 *  - one patient (Alex Rivera)
 *  - owner membership
 *  - sample medication (Lisinopril 10mg) with active schedule (fixed times 08:00, 20:00 America/New_York)
 *  - inventory account + fill transaction
 *  - 3 days of dose_occurrences
 *  - one taken dose event + inventory deduction
 *  - expiration entry, symptom log, alert preference, clinical source version
 */
import pg from 'pg';
import { createHash } from 'node:crypto';

const connectionString = process.env.DATABASE_URL ?? 'postgres://medac:medac@localhost:5432/medac';

// Simple Argon2id placeholder for dev: store hash as `argon2:dev:<sha256>` — production uses real Argon2id
function devPasswordHash(password: string): string {
  const h = createHash('sha256').update(password).digest('hex');
  return `argon2:dev:${h}`;
}

async function main() {
  const client = new pg.Client({ connectionString });
  await client.connect();
  try {
    console.log('[seed] connected to', connectionString.replace(/:[^@]*@/, ':***@'));

    // Check migrations table exists
    const mig = await client.query("SELECT to_regclass('public.users') as tbl");
    if (!mig.rows[0]?.tbl) {
      console.error('[seed] users table missing — run `npm run db:migrate` first');
      process.exit(1);
    }

    await client.query('BEGIN');

    // 1. User
    const userId = '01999999-0000-7000-8000-000000000001';
    const emailNormalized = 'dev@example.com';
    const emailDisplay = 'dev@example.com';
    const passwordHash = devPasswordHash('MedacDev1234!');

    await client.query(
      `INSERT INTO users (id, email_normalized, email_display, password_hash, email_verified_at, status)
       VALUES ($1,$2,$3,$4, now(), 'active')
       ON CONFLICT (id) DO UPDATE SET email_normalized=EXCLUDED.email_normalized`,
      [userId, emailNormalized, emailDisplay, passwordHash],
    );
    // Ensure unique email index doesn't conflict: handle by email
    await client.query(
      `INSERT INTO user_security (user_id) VALUES ($1) ON CONFLICT (user_id) DO NOTHING`,
      [userId],
    );

    // Device for idempotent dose events
    const deviceId = '01999999-0000-7000-8000-000000000010';
    await client.query(
      `INSERT INTO devices (id, user_id, platform, display_name, timezone)
       VALUES ($1,$2,'web','Dev Laptop','America/New_York')
       ON CONFLICT (id) DO NOTHING`,
      [deviceId, userId],
    );

    // 2. Patient
    const patientId = '01999999-0000-7000-8000-000000000002';
    await client.query(
      `INSERT INTO patients (id, display_name, preferred_timezone, created_by_user_id)
       VALUES ($1,'Alex Rivera','America/New_York',$2)
       ON CONFLICT (id) DO NOTHING`,
      [patientId, userId],
    );

    // 3. Membership (owner)
    const membershipId = '01999999-0000-7000-8000-000000000003';
    await client.query(
      `INSERT INTO patient_memberships (id, patient_id, user_id, role, status, granted_by_user_id)
       VALUES ($1,$2,$3,'owner','active',$3)
       ON CONFLICT (id) DO NOTHING`,
      [membershipId, patientId, userId],
    );

    // 4. Medication
    const medicationId = '01999999-0000-7000-8000-000000000004';
    await client.query(
      `INSERT INTO patient_medications
        (id, patient_id, entered_name, dose_quantity_value, dose_quantity_unit, form, route, normalization_status, status, start_date, created_by_user_id)
       VALUES ($1,$2,'Lisinopril 10 mg','1','tablet','tablet','oral','resolved','active', CURRENT_DATE, $3)
       ON CONFLICT (id) DO NOTHING`,
      [medicationId, patientId, userId],
    );

    // 5. Schedule version (fixed times 08:00 and 20:00 America/New_York)
    const scheduleVersionId = '01999999-0000-7000-8000-000000000005';
    await client.query(
      `INSERT INTO medication_schedule_versions
        (id, medication_id, version_number, schedule_type, timing_mode, timezone, effective_from, miss_window_minutes, created_by_user_id)
       VALUES ($1,$2,1,'fixed_times','local_clock','America/New_York', now() - interval '7 days', 120, $3)
       ON CONFLICT (medication_id, version_number) DO NOTHING`,
      [scheduleVersionId, medicationId, userId],
    );
    // schedule_fixed_times has no natural unique constraint — guard for idempotent re-seed
    const existingTimes = await client.query(`SELECT count(*)::int as cnt FROM schedule_fixed_times WHERE schedule_version_id=$1`, [
      scheduleVersionId,
    ]);
    if ((existingTimes.rows[0]?.cnt ?? 0) === 0) {
      await client.query(
        `INSERT INTO schedule_fixed_times (schedule_version_id, local_time, dose_quantity_value, dose_quantity_unit)
         VALUES ($1,'08:00','1','tablet')`,
        [scheduleVersionId],
      );
      await client.query(
        `INSERT INTO schedule_fixed_times (schedule_version_id, local_time, dose_quantity_value, dose_quantity_unit)
         VALUES ($1,'20:00','1','tablet')`,
        [scheduleVersionId],
      );
    }

    // 6. Inventory
    const accountId = '01999999-0000-7000-8000-000000000006';
    await client.query(
      `INSERT INTO inventory_accounts (id, patient_id, medication_id, unit, low_stock_threshold_value)
       VALUES ($1,$2,$3,'tablet','7')
       ON CONFLICT (patient_id, medication_id, unit) DO NOTHING`,
      [accountId, patientId, medicationId],
    );
    // Resolve accountId if ON CONFLICT skipped (fetch)
    const acct = await client.query(`SELECT id FROM inventory_accounts WHERE patient_id=$1 AND medication_id=$2 AND unit='tablet'`, [
      patientId,
      medicationId,
    ]);
    const resolvedAccountId = acct.rows[0].id as string;

    await client.query(
      `INSERT INTO inventory_transactions (inventory_account_id, transaction_type, quantity_delta, actor_user_id)
       VALUES ($1,'fill','30',$2)`,
      [resolvedAccountId, userId],
    );

    // 7. Dose occurrences — 3 days, 2x per day = 6 rows
    for (let dayOffset = -1; dayOffset <= 1; dayOffset++) {
      for (const hour of [8, 20]) {
        const scheduledLocalStr = `now()::date + interval '${dayOffset} days' + interval '${hour} hours'`;
        // Use America/New_York as timezone; generate UTC via AT TIME ZONE
        await client.query(
          `INSERT INTO dose_occurrences
            (patient_id, medication_id, schedule_version_id, scheduled_at_utc, scheduled_local_datetime, timezone, utc_offset_minutes, nominal_dose_value, nominal_dose_unit)
           VALUES (
             $1, $2, $3,
             ((${scheduledLocalStr}) AT TIME ZONE 'America/New_York'),
             ((${scheduledLocalStr})::timestamp),
             'America/New_York',
             -240, -- EDT approximate (seed only)
             '1','tablet'
           )
           ON CONFLICT (schedule_version_id, scheduled_at_utc) DO NOTHING`,
          [patientId, medicationId, scheduleVersionId],
        );
      }
    }

    // 8. One taken dose event (idempotent key = device_id + client_event_id)
    const occurrenceRes = await client.query(
      `SELECT id FROM dose_occurrences WHERE patient_id=$1 ORDER BY scheduled_at_utc ASC LIMIT 1`,
      [patientId],
    );
    const occurrenceId = occurrenceRes.rows[0]?.id as string | undefined;
    if (occurrenceId) {
      await client.query(
        `INSERT INTO dose_event_logs
          (patient_id, medication_id, occurrence_id, event_type, actual_at, timezone, dose_value, dose_unit, client_event_id, device_id, actor_user_id)
         VALUES ($1,$2,$3,'taken', now() - interval '2 hours', 'America/New_York','1','tablet','seed-client-event-001',$4,$5)
         ON CONFLICT DO NOTHING`,
        [patientId, medicationId, occurrenceId, deviceId, userId],
      );
      // Deduce inventory for the taken event — create if not exists
      const evt = await client.query(`SELECT id FROM dose_event_logs WHERE client_event_id='seed-client-event-001' AND device_id=$1`, [
        deviceId,
      ]);
      const evtId = evt.rows[0]?.id as string | undefined;
      if (evtId) {
        await client.query(
          `INSERT INTO inventory_transactions (inventory_account_id, transaction_type, quantity_delta, dose_event_id, actor_user_id)
           VALUES ($1,'dose_consumed','-1',$2,$3)
           ON CONFLICT DO NOTHING`,
          [resolvedAccountId, evtId, userId],
        );
      }
    }

    // 9. Expiration
    await client.query(
      `INSERT INTO medication_expirations (medication_id, expiration_date, lot_number, quantity_value, quantity_unit)
       VALUES ($1, CURRENT_DATE + interval '90 days','DEV-LOT-001','30','tablet')`,
      [medicationId],
    );

    // 10. Symptom log
    await client.query(
      `INSERT INTO symptom_logs (patient_id, occurred_at, note_ciphertext, actor_user_id)
       VALUES ($1, now() - interval '1 day', 'dev-seed-symptom-ciphertext', $2)`,
      [patientId, userId],
    );

    // 11. Alert preferences
    await client.query(
      `INSERT INTO alert_preferences (patient_id, user_id, alert_type, enabled, delay_minutes, timezone)
       VALUES ($1,$2,'low_stock', true, 1440, 'America/New_York')
       ON CONFLICT (patient_id, user_id, alert_type) DO NOTHING`,
      [patientId, userId],
    );

    // 12. Clinical source version (placeholder)
    await client.query(
      `INSERT INTO clinical_source_versions (source_name, source_version, license_reference, checksum)
       VALUES ('rxnorm','2024-08-01','NLM RxNorm - for development fixtures only','dev-checksum-001')
       ON CONFLICT (source_name, source_version) DO NOTHING`,
    );

    // 13. Audit event
    await client.query(
      `INSERT INTO audit_events (actor_user_id, patient_id, action, entity_type, entity_id, request_id, metadata_json)
       VALUES ($1,$2,'seed_development','patient',$2,'seed-req-001','{"seed": true}'::jsonb)`,
      [userId, patientId],
    );

    await client.query('COMMIT');
    console.log('[seed] completed successfully');
    console.log(`[seed] user: ${emailDisplay} / MedacDev1234!`);
    console.log(`[seed] patient: Alex Rivera (${patientId})`);
    console.log(`[seed] medication: Lisinopril 10 mg (${medicationId})`);
  } catch (e) {
    await client.query('ROLLBACK');
    console.error('[seed] failed, rolled back:', e);
    process.exit(1);
  } finally {
    await client.end();
  }
}

await main();
