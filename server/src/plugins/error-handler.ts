import type { FastifyInstance, FastifyRequest, FastifyReply } from "fastify";
import fp from "fastify-plugin";

export interface AppErrorBody {
  error: {
    code: string;
    message: string;
    request_id: string;
    details?: Record<string, unknown>;
  };
}

async function errorHandlerPluginImpl(app: FastifyInstance): Promise<void> {
  app.setErrorHandler((error, request: FastifyRequest, reply: FastifyReply) => {
    const requestId = (request as unknown as { id: string }).id ?? (request.headers["x-request-id"] as string) ?? "unknown";
    // Zod validation errors have no statusCode but have issues/name
    const isZod = (error as unknown as { name?: string; issues?: unknown }).name === "ZodError" || (error as unknown as { issues?: unknown }).issues !== undefined;
    const rawStatus = (error as unknown as { statusCode?: number }).statusCode;
    const status = isZod ? 400 : (rawStatus ?? 500);

    // Never expose internal stack traces or exception messages from DB/upstream libraries
    // Map known validation errors to 400
    let code = "INTERNAL_ERROR";
    let message = "An unexpected error occurred.";
    let details: Record<string, unknown> | undefined;

    if (isZod) {
      code = "VALIDATION_ERROR";
      message = "Request validation failed.";
      const issues = (error as unknown as { issues?: unknown }).issues;
      details = { validation: issues ?? (error as unknown as { validation?: unknown }).validation };
    } else if ((error as unknown as { validation?: unknown }).validation) {
      code = "VALIDATION_ERROR";
      message = "Request validation failed.";
      details = { validation: (error as unknown as { validation: unknown }).validation };
      // Fastify validation errors already have status 400
    } else if (status === 400) {
      code = (error as unknown as { code?: string }).code ?? "BAD_REQUEST";
      message = (error as Error).message ?? "Bad request.";
    } else if (status === 401) {
      code = "UNAUTHORIZED";
      message = "Authentication required.";
    } else if (status === 403) {
      code = "FORBIDDEN";
      message = "Forbidden.";
    } else if (status === 404) {
      code = "NOT_FOUND";
      message = "Not found.";
    } else if (status === 409) {
      code = "CONFLICT";
      message = (error as Error).message ?? "Conflict.";
    } else if (status === 422) {
      code = "UNPROCESSABLE_ENTITY";
      message = (error as Error).message ?? "Unprocessable entity.";
    } else if (status === 423) {
      code = (error as unknown as { code?: string }).code ?? "ACCOUNT_LOCKED";
      message = "Account temporarily locked.";
    } else if (status === 429) {
      code = "RATE_LIMITED";
      message = "Too many requests.";
    } else if (status >= 400 && status < 500) {
      code = (error as unknown as { code?: string }).code ?? "BAD_REQUEST";
      message = "Bad request.";
    } else {
      // 5xx: log internally but return generic
      request.log.error({ err: error, requestId, status }, "internal error");
      code = "INTERNAL_ERROR";
      message = "An unexpected error occurred.";
    }

    // For 4xx validation, log at warn without sensitive body
    if (status >= 400 && status < 500) {
      request.log.warn({ requestId, status, code }, message);
    }

    const body: AppErrorBody = {
      error: {
        code,
        message,
        request_id: requestId,
        ...(details ? { details } : {}),
      },
    };

    void reply.status(status).send(body);
  });

  app.setNotFoundHandler((request: FastifyRequest, reply: FastifyReply) => {
    const requestId = (request as unknown as { id: string }).id ?? (request.headers["x-request-id"] as string) ?? "unknown";
    const body: AppErrorBody = {
      error: {
        code: "NOT_FOUND",
        message: `Route ${request.method} ${request.url} not found.`,
        request_id: requestId,
      },
    };
    void reply.status(404).send(body);
  });
}

export const errorHandlerPlugin = fp(errorHandlerPluginImpl, { name: "error-handler", fastify: "5.x" });
export default errorHandlerPlugin;
