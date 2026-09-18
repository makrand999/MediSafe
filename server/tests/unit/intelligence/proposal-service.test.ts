import { describe, it, expect, beforeEach } from "vitest";
import { ProposalService, InMemoryProposalStore, hashPayloadCanonical, encryptPayload, decryptPayload } from "../../../src/modules/intelligence/proposal-service.js";

describe("proposal-service hash/expiry/replay", () => {
  let store: InMemoryProposalStore;
  let svc: ProposalService;

  beforeEach(() => {
    store = new InMemoryProposalStore();
    svc = new ProposalService(store, 30);
  });

  it("hash is deterministic and binds payload", () => {
    const payload = { b: 2, a: 1 } as unknown as Record<string, unknown>;
    const h1 = hashPayloadCanonical(payload);
    const h2 = hashPayloadCanonical({ a: 1, b: 2 } as unknown as Record<string, unknown>);
    expect(h1).toBe(h2);
    expect(h1).toMatch(/^[0-9a-f]{64}$/);
    const h3 = hashPayloadCanonical({ a: 1, b: 3 } as unknown as Record<string, unknown>);
    expect(h3).not.toBe(h1);
  });

  it("encrypt/decrypt round-trip", () => {
    const json = JSON.stringify({ hello: "world" });
    const ct = encryptPayload(json);
    const pt = decryptPayload(ct);
    expect(JSON.parse(pt)).toEqual({ hello: "world" });
  });

  it("create sets pending + expiry and payloadHash", () => {
    const p = svc.create({
      aiRunId: "00000000-0000-4000-a000-000000000010",
      patientId: "00000000-0000-4000-a000-000000000011",
      proposedByUserId: "00000000-0000-4000-a000-000000000012",
      actionType: "propose_create_medication",
      payload: { enteredName: "Lisinopril" },
      humanSummary: "Create Lisinopril",
      requiredPermission: "medication:write",
      resourceVersionsJson: { medication: 0 },
      ttlMinutes: 30,
    });
    expect(p.status).toBe("pending");
    expect(p.payloadHash).toBe(hashPayloadCanonical({ enteredName: "Lisinopril" }));
    expect(new Date(p.expiresAt).getTime()).toBeGreaterThan(Date.now());
  });

  it("confirm requires auth+hash+versions+unexpired (success path)", () => {
    const p = svc.create({
      aiRunId: "00000000-0000-4000-a000-000000000020",
      patientId: "00000000-0000-4000-a000-000000000021",
      proposedByUserId: "00000000-0000-4000-a000-000000000022",
      actionType: "propose_create_medication",
      payload: { enteredName: "A" },
      humanSummary: "Create A",
      requiredPermission: "medication:write",
      resourceVersionsJson: { medication: 1 },
      ttlMinutes: 30,
    });
    const confirmed = svc.confirm({
      proposalId: p.id,
      patientId: p.patientId,
      confirmingUserId: "00000000-0000-4000-a000-000000000023",
      payloadHash: p.payloadHash,
      expectedResourceVersions: { medication: 1 },
      hasRecentAuth: true,
      isAuthorized: true,
    });
    expect(confirmed.status).toBe("confirmed");
  });

  it("confirm fails on hash mismatch", () => {
    const p = svc.create({
      aiRunId: "00000000-0000-4000-a000-000000000030",
      patientId: "00000000-0000-4000-a000-000000000031",
      proposedByUserId: "00000000-0000-4000-a000-000000000032",
      actionType: "propose_create_medication",
      payload: { enteredName: "A" },
      humanSummary: "Create A",
      requiredPermission: "medication:write",
      resourceVersionsJson: { medication: 1 },
      ttlMinutes: 30,
    });
    try {
      svc.confirm({
        proposalId: p.id,
        patientId: p.patientId,
        confirmingUserId: "00000000-0000-4000-a000-000000000033",
        payloadHash: "badhash",
        expectedResourceVersions: { medication: 1 },
        hasRecentAuth: true,
        isAuthorized: true,
      });
      expect.fail("should have thrown");
    } catch (e) {
      expect((e as unknown as { code: string }).code).toBe("HASH_MISMATCH");
    }
  });

  it("confirm fails on version conflict (optimistic concurrency)", () => {
    const p = svc.create({
      aiRunId: "00000000-0000-4000-a000-000000000040",
      patientId: "00000000-0000-4000-a000-000000000041",
      proposedByUserId: "00000000-0000-4000-a000-000000000042",
      actionType: "propose_update_medication",
      payload: { patch: { x: 1 } },
      humanSummary: "Update",
      requiredPermission: "medication:write",
      resourceVersionsJson: { medication: 2 },
      ttlMinutes: 30,
    });
    try {
      svc.confirm({
        proposalId: p.id,
        patientId: p.patientId,
        confirmingUserId: "00000000-0000-4000-a000-000000000043",
        payloadHash: p.payloadHash,
        expectedResourceVersions: { medication: 3 }, // stale
        hasRecentAuth: true,
        isAuthorized: true,
      });
      expect.fail("should have thrown");
    } catch (e) {
      expect((e as unknown as { code: string }).code).toBe("VERSION_CONFLICT");
    }
  });

  it("confirm fails when expired", () => {
    const p = svc.create({
      aiRunId: "00000000-0000-4000-a000-000000000050",
      patientId: "00000000-0000-4000-a000-000000000051",
      proposedByUserId: "00000000-0000-4000-a000-000000000052",
      actionType: "propose_create_medication",
      payload: { enteredName: "A" },
      humanSummary: "Create A",
      requiredPermission: "medication:write",
      resourceVersionsJson: { medication: 1 },
      ttlMinutes: -1, // already expired
    });
    expect(() =>
      svc.confirm({
        proposalId: p.id,
        patientId: p.patientId,
        confirmingUserId: "00000000-0000-4000-a000-000000000053",
        payloadHash: p.payloadHash,
        expectedResourceVersions: { medication: 1 },
        hasRecentAuth: true,
        isAuthorized: true,
      }),
    ).toThrow(/expired/i);
    expect(store.get(p.id)?.status).toBe("expired");
  });

  it("replay is prevented: confirm twice fails, execute exactly-once", () => {
    const p = svc.create({
      aiRunId: "00000000-0000-4000-a000-000000000060",
      patientId: "00000000-0000-4000-a000-000000000061",
      proposedByUserId: "00000000-0000-4000-a000-000000000062",
      actionType: "propose_create_medication",
      payload: { enteredName: "A" },
      humanSummary: "Create A",
      requiredPermission: "medication:write",
      resourceVersionsJson: { medication: 1 },
      ttlMinutes: 30,
    });
    svc.confirm({
      proposalId: p.id,
      patientId: p.patientId,
      confirmingUserId: "00000000-0000-4000-a000-000000000063",
      payloadHash: p.payloadHash,
      expectedResourceVersions: { medication: 1 },
      hasRecentAuth: true,
      isAuthorized: true,
    });
    // second confirm should fail (not pending)
    expect(() =>
      svc.confirm({
        proposalId: p.id,
        patientId: p.patientId,
        confirmingUserId: "00000000-0000-4000-a000-000000000063",
        payloadHash: p.payloadHash,
        expectedResourceVersions: { medication: 1 },
        hasRecentAuth: true,
        isAuthorized: true,
      }),
    ).toThrow(/not pending/i);

    // execute once
    const ex = svc.execute(p.id, () => {});
    expect(ex.status).toBe("executed");
    // replay execute fails
    expect(() => svc.execute(p.id, () => {})).toThrow(/not confirmed/i);
  });

  it("high-impact requires recent auth", () => {
    const p = svc.create({
      aiRunId: "00000000-0000-4000-a000-000000000070",
      patientId: "00000000-0000-4000-a000-000000000071",
      proposedByUserId: "00000000-0000-4000-a000-000000000072",
      actionType: "propose_discontinue_medication",
      payload: { medicationId: "00000000-0000-4000-a000-000000000073" },
      humanSummary: "Discontinue",
      requiredPermission: "medication:write",
      resourceVersionsJson: { medication: 5 },
      ttlMinutes: 30,
    });
    expect(() =>
      svc.confirm({
        proposalId: p.id,
        patientId: p.patientId,
        confirmingUserId: "00000000-0000-4000-a000-000000000073",
        payloadHash: p.payloadHash,
        expectedResourceVersions: { medication: 5 },
        hasRecentAuth: false, // missing
        isAuthorized: true,
      }),
    ).toThrow(/Recent authentication/);
  });

  it("patient mismatch rejected", () => {
    const p = svc.create({
      aiRunId: "00000000-0000-4000-a000-000000000080",
      patientId: "00000000-0000-4000-a000-000000000081",
      proposedByUserId: "00000000-0000-4000-a000-000000000082",
      actionType: "propose_create_medication",
      payload: { enteredName: "A" },
      humanSummary: "Create A",
      requiredPermission: "medication:write",
      resourceVersionsJson: { medication: 1 },
      ttlMinutes: 30,
    });
    expect(() =>
      svc.confirm({
        proposalId: p.id,
        patientId: "00000000-0000-4000-a000-000000000099", // wrong patient
        confirmingUserId: "00000000-0000-4000-a000-000000000083",
        payloadHash: p.payloadHash,
        expectedResourceVersions: { medication: 1 },
        hasRecentAuth: true,
        isAuthorized: true,
      }),
    ).toThrow(/Patient mismatch/);
  });
});
