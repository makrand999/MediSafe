import type { FastifyInstance, FastifyRequest, FastifyReply } from "fastify";
import fp from "fastify-plugin";
import { randomUUID } from "node:crypto";

async function requestIdPluginImpl(app: FastifyInstance): Promise<void> {
  app.addHook("onRequest", async (request: FastifyRequest, reply: FastifyReply) => {
    const headerId = request.headers["x-request-id"];
    const incoming = typeof headerId === "string" && /^[A-Za-z0-9._:-]{1,128}$/.test(headerId) ? headerId : undefined;
    const id = incoming ?? randomUUID();
    (request as unknown as { id: string }).id = id;
    (request.headers as Record<string, string>)["x-request-id"] = id;
    // Set header early; also ensure onSend will guarantee it
    reply.header("x-request-id", id);
  });

  // Ensure header is set on the way out (covers cases where early header didn't persist)
  app.addHook("onSend", async (request: FastifyRequest, reply: FastifyReply, payload) => {
    const id =
      (request as unknown as { id: string }).id ??
      (request.headers["x-request-id"] as string) ??
      (request as unknown as { id: string }).id ??
      randomUUID();
    // Fastify normalizes header names; one call is sufficient.
    reply.header("x-request-id", id);
    return payload;
  });
}

export const requestIdPlugin = fp(requestIdPluginImpl, { name: "request-id", fastify: "5.x" });
export default requestIdPlugin;
