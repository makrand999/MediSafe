import pino, { type Logger } from "pino";

export const REDACT_PATHS = [
  "req.headers.authorization",
  "req.headers.cookie",
  "req.headers['x-api-key']",
  "req.body.password",
  "req.body.password_hash",
  "req.body.token",
  "req.body.access_token",
  "req.body.refresh_token",
  "req.body.api_key",
  "req.body.muse_spark_api_key",
  "req.body.email",
  "req.body.medication_name",
  "req.body.symptom",
  "req.body.note",
  "req.body.note_ciphertext",
  "req.body.label_image",
  "req.body.image_base64",
  "req.body.payload_ciphertext",
  "req.body.human_summary_ciphertext",
  "res.headers['set-cookie']",
  "*.password",
  "*.password_hash",
  "*.token",
  "*.access_token",
  "*.refresh_token",
  "*.medication_name",
  "*.symptom",
  "*.note",
  "*.note_ciphertext",
  "*.label_image",
  "*.prompt",
  "*.response",
  "*.tool_args",
  "*.tool_result",
  "*.patient_name",
  "*.display_name",
  "*.indication_text",
  "*.prescriber_text",
  "*.pharmacy_text",
  "authorization",
  "cookie",
  "muse_spark_api_key",
  "MEDAC_SPARK_API_KEY",
  "MUSE_SPARK_API_KEY",
];

export function createLogger(level = "info"): Logger {
  return pino({
    level,
    redact: {
      paths: REDACT_PATHS,
      censor: "[REDACTED]",
    },
    serializers: {
      req(req) {
        return {
          method: req.method,
          url: req.url,
          // route template is added by plugin, not raw path
          id: (req as unknown as { id: string }).id,
        };
      },
      res(res) {
        return {
          statusCode: res.statusCode,
        };
      },
    },
  });
}
