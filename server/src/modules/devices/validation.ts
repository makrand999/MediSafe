import { z } from "zod";

export const PutPushTokenSchema = z.object({
  token: z.string().min(10).max(4096),
  provider: z.enum(["fcm", "apns"]).default("fcm"),
}).strict();

export const RegisterDeviceSchema = z.object({
  platform: z.enum(["ios", "android", "web", "unknown"]).default("unknown"),
  display_name: z.string().max(100).optional(),
  app_version: z.string().max(50).optional(),
  timezone: z.string().max(100).optional(),
  notification_permission: z.enum(["granted", "denied", "provisional", "not_requested"]).optional(),
}).strict();
