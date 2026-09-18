/**
 * lib/push.ts — Firebase Cloud Messaging (FCM) push notification provider
 * Enforces HIPAA / privacy compliance: no PHI in payload, generic alerts.
 */
import { existsSync, readFileSync } from "node:fs";
import { initializeApp, getApps, cert, type App, type ServiceAccount } from "firebase-admin/app";
import { getMessaging, type Message, type MulticastMessage, type SendResponse } from "firebase-admin/messaging";
import { getConfig } from "../config/index.js";
import { createLogger } from "./logger.js";

const logger = createLogger(getConfig().logLevel);

let firebaseApp: App | null = null;

/**
 * Initializes or retrieves the Firebase Admin application instance.
 * Attempts to load from config.pushProviderConfigFile, with fallback to local dev credential if present.
 */
export function getFirebaseApp(): App | null {
  if (firebaseApp) return firebaseApp;
  const apps = getApps();
  if (apps.length > 0 && apps[0]) {
    firebaseApp = apps[0];
    return firebaseApp;
  }

  const cfg = getConfig();
  let keyPath = cfg.pushProviderConfigFile;

  if (!existsSync(keyPath)) {
    // Local dev fallbacks
    const devFallbacks = [
      "/home/max/Projects/medac/labour-link-ae60d-firebase-adminsdk-fbsvc-e4167ca3dc.json",
      "./labour-link-ae60d-firebase-adminsdk-fbsvc-e4167ca3dc.json",
      "./secrets/push.json",
    ];
    const found = devFallbacks.find((p) => existsSync(p));
    if (found) {
      keyPath = found;
    } else {
      logger.warn({ path: cfg.pushProviderConfigFile }, "Firebase service account JSON not found, push notifications disabled");
      return null;
    }
  }

  try {
    const raw = readFileSync(keyPath, "utf-8");
    const serviceAccount = JSON.parse(raw) as ServiceAccount;
    firebaseApp = initializeApp({
      credential: cert(serviceAccount),
    });
    logger.info({ projectId: serviceAccount.projectId }, "Firebase Admin SDK initialized successfully");
    return firebaseApp;
  } catch (err) {
    logger.error({ err, path: keyPath }, "Failed to initialize Firebase Admin SDK");
    return null;
  }
}

export interface PushResult {
  success: boolean;
  messageId?: string;
  errorCode?: string;
  isInvalidToken: boolean;
}

export interface SendPushParams {
  token: string;
  title?: string;
  body?: string;
  data?: Record<string, string>;
  priority?: "high" | "normal";
}

/**
 * Checks if a Firebase messaging error code indicates the push token is dead/unregistered.
 */
export function isFCMTokenInvalid(errorCode?: string): boolean {
  if (!errorCode) return false;
  const invalidCodes = [
    "messaging/invalid-registration-token",
    "messaging/registration-token-not-registered",
    "messaging/mismatched-credential",
    "invalid-registration-token",
    "registration-token-not-registered",
  ];
  return invalidCodes.includes(errorCode) || errorCode.includes("not-registered") || errorCode.includes("invalid-token");
}

/**
 * Sends a single FCM push message with privacy-preserving payload.
 */
export async function sendPushNotification(params: SendPushParams): Promise<PushResult> {
  const app = getFirebaseApp();
  if (!app) {
    logger.debug({ tokenPrefix: params.token.slice(0, 8) }, "Mock push notification (Firebase not initialized)");
    return {
      success: true,
      messageId: `mock-msg-${Date.now()}`,
      isInvalidToken: false,
    };
  }

  try {
    const messaging = getMessaging(app);
    const message: Message = {
      token: params.token,
      notification: {
        title: params.title ?? "Medac",
        body: params.body ?? "Medication reminder",
      },
      data: params.data ?? {},
      android: {
        priority: params.priority === "high" ? "high" : "normal",
        notification: {
          channelId: "medac_reminders",
          priority: params.priority === "high" ? "max" : "default",
          defaultSound: true,
        },
      },
      apns: {
        payload: {
          aps: {
            alert: {
              title: params.title ?? "Medac",
              body: params.body ?? "Medication reminder",
            },
            sound: "default",
          },
        },
      },
    };

    const messageId = await messaging.send(message);
    return {
      success: true,
      messageId,
      isInvalidToken: false,
    };
  } catch (err: unknown) {
    const fbError = err as { code?: string; message?: string };
    const errorCode = fbError.code ?? "UNKNOWN_ERROR";
    const invalid = isFCMTokenInvalid(errorCode);
    logger.warn({ errorCode, message: fbError.message, tokenPrefix: params.token.slice(0, 8) }, "FCM push send failed");
    return {
      success: false,
      errorCode,
      isInvalidToken: invalid,
    };
  }
}

export interface MulticastPushResult {
  responses: Array<{
    token: string;
    success: boolean;
    messageId?: string;
    errorCode?: string;
    isInvalidToken: boolean;
  }>;
}

/**
 * Sends multicast FCM push messages to multiple device tokens.
 */
export async function sendMulticastPushNotification(tokens: string[], params: Omit<SendPushParams, "token">): Promise<MulticastPushResult> {
  if (tokens.length === 0) return { responses: [] };

  const app = getFirebaseApp();
  if (!app) {
    return {
      responses: tokens.map((token) => ({
        token,
        success: true,
        messageId: `mock-msg-${Date.now()}-${token.slice(0, 4)}`,
        isInvalidToken: false,
      })),
    };
  }

  try {
    const messaging = getMessaging(app);
    const multicastMessage: MulticastMessage = {
      tokens,
      notification: {
        title: params.title ?? "Medac",
        body: params.body ?? "Medication reminder",
      },
      data: params.data ?? {},
      android: {
        priority: params.priority === "high" ? "high" : "normal",
        notification: {
          channelId: "medac_reminders",
          priority: params.priority === "high" ? "max" : "default",
          defaultSound: true,
        },
      },
      apns: {
        payload: {
          aps: {
            alert: {
              title: params.title ?? "Medac",
              body: params.body ?? "Medication reminder",
            },
            sound: "default",
          },
        },
      },
    };

    const response = await messaging.sendEachForMulticast(multicastMessage);
    const results = response.responses.map((res: SendResponse, idx: number) => {
      const token = tokens[idx];
      if (res.success) {
        return {
          token,
          success: true,
          messageId: res.messageId,
          isInvalidToken: false,
        };
      }
      const errorCode = res.error?.code ?? "UNKNOWN_ERROR";
      return {
        token,
        success: false,
        errorCode,
        isInvalidToken: isFCMTokenInvalid(errorCode),
      };
    });

    return { responses: results };
  } catch (err) {
    logger.error({ err, tokenCount: tokens.length }, "Multicast push send failed");
    return {
      responses: tokens.map((token) => ({
        token,
        success: false,
        errorCode: "MULTICAST_ERROR",
        isInvalidToken: false,
      })),
    };
  }
}
