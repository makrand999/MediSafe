#!/usr/bin/env node
/**
 * medac-worker — background job processor §9
 * Processes outbox_events and notification_deliveries with FOR UPDATE SKIP LOCKED,
 * idempotent handlers, FCM push dispatch, dead-letter.
 */
import { loadConfig } from "./config/index.js";
import { createLogger } from "./lib/logger.js";
import { createPool, createDb } from "./db/index.js";
import { getFirebaseApp, sendPushNotification } from "./lib/push.js";
import { getValidPushTokensForUser } from "./modules/devices/service.js";

function getGenericAlertTitleAndBody(messageKey: string, alertType: string): { title: string; body: string } {
  switch (alertType) {
    case "low_stock":
      return { title: "Medac Refill Alert", body: "A medication is running low on stock." };
    case "expiration":
      return { title: "Medac Expiration Alert", body: "A medication batch is nearing expiration." };
    case "stale_device":
      return { title: "Medac Sync Alert", body: "A registered device has not synchronized recently." };
    case "missed_user_attention_med":
      return { title: "Medac Schedule Alert", body: "A scheduled high-attention dose was missed." };
    default:
      return { title: "Medac Reminder", body: "You have a new health reminder." };
  }
}

async function main(): Promise<void> {
  const config = loadConfig();
  const logger = createLogger(config.logLevel);
  const pool = createPool(config.databaseUrl);
  const db = createDb(pool);

  // Initialize Firebase Admin if credential exists
  getFirebaseApp();

  logger.info({ buildId: config.appBuildId, concurrency: config.workerConcurrency }, "medac-worker starting");

  let running = true;
  const shutdown = (signal: string) => {
    logger.info({ signal }, "medac-worker shutting down");
    running = false;
    setTimeout(() => process.exit(0), 2000);
  };
  process.on("SIGINT", () => shutdown("SIGINT"));
  process.on("SIGTERM", () => shutdown("SIGTERM"));

  // Simple email sender (console in dev, real provider later)
  async function sendEmail(to: string, subject: string, body: string): Promise<void> {
    if (config.emailProvider === "console") {
      logger.info({ to: to.slice(0, 3) + "***", subject }, "email (console) would send");
      return;
    }
    logger.info({ to: to.slice(0, 3) + "***", subject }, "email provider not configured, logged");
  }

  async function processOutboxBatch(): Promise<number> {
    const client = await pool.connect();
    try {
      await client.query("BEGIN");
      const res = await client.query(
        `SELECT id, event_type, aggregate_type, aggregate_id, payload_json, attempt_count
         FROM outbox_events
         WHERE processed_at IS NULL AND available_at <= NOW() AND (locked_until IS NULL OR locked_until <= NOW())
         ORDER BY available_at ASC
         LIMIT 10
         FOR UPDATE SKIP LOCKED`,
      );
      if (res.rows.length === 0) {
        await client.query("ROLLBACK");
        return 0;
      }
      for (const row of res.rows) {
        const id = row.id as string;
        const eventType = row.event_type as string;
        const payload = row.payload_json as Record<string, unknown>;
        const attempt = (row.attempt_count as number) ?? 0;
        await client.query(`UPDATE outbox_events SET locked_until = NOW() + INTERVAL '30 seconds', attempt_count = attempt_count + 1 WHERE id = $1`, [id]);
        await client.query("COMMIT");
        try {
          if (eventType === "user_registered") {
            const userId = payload.userId as string | undefined ?? row.aggregate_id as string;
            const userRes = await client.query(`SELECT email_display FROM users WHERE id = $1`, [userId]);
            if (userRes.rows[0]) {
              const email = userRes.rows[0].email_display as string;
              logger.info({ userId: userId.slice(0, 8), email: email.slice(0, 3) + "***" }, "outbox: user_registered — verification email processed");
            }
          } else if (eventType === "caregiver_invited") {
            const patientId = payload.patientId as string;
            const invitedEmail = payload.invitedEmailNormalized as string;
            const inviteId = row.aggregate_id as string;
            const rawToken = payload.rawToken as string | undefined;
            if (rawToken) {
              await sendEmail(invitedEmail, "Medac caregiver invite", `You have been invited to patient ${patientId}. Token: ${rawToken.slice(0, 8)}...`);
            } else {
              logger.warn({ inviteId }, "caregiver_invited missing rawToken in payload — invite unusable");
            }
          } else if (eventType === "password_changed") {
            logger.info({ aggregateId: row.aggregate_id }, "outbox: password_changed");
          }
          await pool.query(`UPDATE outbox_events SET processed_at = NOW(), locked_until = NULL WHERE id = $1`, [id]);
        } catch (e) {
          const msg = (e as Error).message.slice(0, 200);
          logger.error({ err: e, id, eventType }, "outbox handler failed");
          await pool.query(`UPDATE outbox_events SET last_error_code = $1, locked_until = NOW() + INTERVAL '30 seconds' * (attempt_count + 1) WHERE id = $2`, [msg, id]);
          if (attempt + 1 >= config.jobMaxAttempts) {
            await pool.query(`UPDATE outbox_events SET last_error_code = 'DEAD_LETTER' WHERE id = $1`, [id]);
          }
        }
        await client.query("BEGIN");
      }
      await client.query("COMMIT");
      return res.rows.length;
    } catch (e) {
      try { await client.query("ROLLBACK"); } catch {}
      logger.error({ err: e }, "outbox batch failed");
      return 0;
    } finally {
      client.release();
    }
  }

  async function processPushDeliveriesBatch(): Promise<number> {
    const client = await pool.connect();
    try {
      await client.query("BEGIN");
      const res = await client.query(
        `SELECT nd.id, nd.alert_id, nd.user_id, nd.attempt_count, a.alert_type, a.severity, a.message_key, a.patient_id
         FROM notification_deliveries nd
         JOIN alerts a ON nd.alert_id = a.id
         WHERE nd.status = 'queued' AND nd.channel = 'push' AND (nd.next_attempt_at IS NULL OR nd.next_attempt_at <= NOW())
         ORDER BY nd.created_at ASC
         LIMIT 10
         FOR UPDATE OF nd SKIP LOCKED`,
      );
      if (res.rows.length === 0) {
        await client.query("ROLLBACK");
        return 0;
      }

      for (const row of res.rows) {
        const id = row.id as string;
        const alertId = row.alert_id as string;
        const userId = row.user_id as string;
        const alertType = row.alert_type as string;
        const severity = row.severity as string;
        const messageKey = row.message_key as string;
        const patientId = row.patient_id as string;
        const attempt = (row.attempt_count as number) ?? 0;

        await client.query(`UPDATE notification_deliveries SET next_attempt_at = NOW() + INTERVAL '30 seconds', attempt_count = attempt_count + 1 WHERE id = $1`, [id]);
        await client.query("COMMIT");

        try {
          const pushTokens = await getValidPushTokensForUser(db, userId);
          if (pushTokens.length === 0) {
            logger.info({ userId: userId.slice(0, 8), deliveryId: id }, "No active push tokens for user, marking delivery failed");
            await pool.query(`UPDATE notification_deliveries SET status = 'failed', failed_at = NOW(), failure_code = 'NO_ACTIVE_PUSH_TOKEN' WHERE id = $1`, [id]);
          } else {
            const { title, body } = getGenericAlertTitleAndBody(messageKey, alertType);
            let anySuccess = false;
            let lastMessageId: string | undefined;
            let lastErrorCode: string | undefined;
            let allInvalid = true;

            for (const pt of pushTokens) {
              const res = await sendPushNotification({
                token: pt.token,
                title,
                body,
                data: {
                  alert_id: alertId,
                  alert_type: alertType,
                  severity,
                  patient_id: patientId,
                },
                priority: severity === "urgent_review" || severity === "potential_emergency" ? "high" : "normal",
              });

              if (res.success) {
                anySuccess = true;
                allInvalid = false;
                lastMessageId = res.messageId;
                await pool.query(`UPDATE push_tokens SET last_success_at = NOW() WHERE device_id = $1`, [pt.deviceId]);
              } else {
                lastErrorCode = res.errorCode;
                if (res.isInvalidToken) {
                  logger.info({ deviceId: pt.deviceId }, "Invalidating dead FCM push token");
                  await pool.query(`UPDATE push_tokens SET invalidated_at = NOW() WHERE device_id = $1`, [pt.deviceId]);
                } else {
                  allInvalid = false;
                }
              }
            }

            if (anySuccess) {
              await pool.query(
                `UPDATE notification_deliveries SET status = 'sent', sent_at = NOW(), provider_message_id = $1 WHERE id = $2`,
                [lastMessageId, id],
              );
            } else if (allInvalid) {
              await pool.query(
                `UPDATE notification_deliveries SET status = 'invalid_token', failed_at = NOW(), failure_code = $1 WHERE id = $2`,
                [lastErrorCode ?? "INVALID_TOKEN", id],
              );
            } else {
              if (attempt + 1 >= config.jobMaxAttempts) {
                await pool.query(
                  `UPDATE notification_deliveries SET status = 'failed', failed_at = NOW(), failure_code = $1 WHERE id = $2`,
                  [lastErrorCode ?? "SEND_FAILED", id],
                );
              } else {
                await pool.query(
                  `UPDATE notification_deliveries SET next_attempt_at = NOW() + INTERVAL '1 minute' * $1, failure_code = $2 WHERE id = $3`,
                  [attempt + 1, lastErrorCode ?? "RETRY", id],
                );
              }
            }
          }
        } catch (e) {
          const msg = (e as Error).message.slice(0, 200);
          logger.error({ err: e, id }, "push delivery handler failed");
          await pool.query(`UPDATE notification_deliveries SET failure_code = $1, next_attempt_at = NOW() + INTERVAL '30 seconds' * (attempt_count + 1) WHERE id = $2`, [msg, id]);
        }

        await client.query("BEGIN");
      }
      await client.query("COMMIT");
      return res.rows.length;
    } catch (e) {
      try { await client.query("ROLLBACK"); } catch {}
      logger.error({ err: e }, "push delivery batch failed");
      return 0;
    } finally {
      client.release();
    }
  }

  while (running) {
    const nOutbox = await processOutboxBatch();
    const nPush = await processPushDeliveriesBatch();
    const total = nOutbox + nPush;

    if (total === 0) {
      await new Promise((r) => setTimeout(r, 2000));
    } else {
      logger.debug({ outboxProcessed: nOutbox, pushProcessed: nPush }, "worker batch processed");
      await new Promise((r) => setTimeout(r, 200));
    }
  }

  await pool.end();
  logger.info("medac-worker stopped");
}

if (import.meta.url === `file://${process.argv[1]}`) {
  void main().catch((err) => {
    console.error("medac-worker failed:", err);
    process.exit(1);
  });
}

export { main as runWorker };
