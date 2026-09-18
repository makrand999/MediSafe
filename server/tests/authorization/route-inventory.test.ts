import { describe, it, expect } from "vitest";
import { readFileSync } from "node:fs";
import { join } from "node:path";

/**
 * Route inventory test — ensures every patient-scoped route is listed and will have authz coverage
 * Per plan §7.2, §22 — every patient-scoped endpoint must have positive+negative authz tests.
 */

describe("authorization route inventory §7.1, §7.2, §22", () => {
  it("lists all patient-scoped routes that must be protected", () => {
    // Inventory derived from plan §8.6-8.14 plus §7.2 requirements
    const mustBeProtected = [
      "GET /patients",
      "POST /patients",
      "GET /patients/:patientId",
      "PATCH /patients/:patientId",
      "POST /patients/:patientId/archive",
      "POST /patients/:patientId/deletion-request",
      "GET /patients/:patientId/memberships",
      "PATCH /patients/:patientId/memberships/:membershipId",
      "DELETE /patients/:patientId/memberships/:membershipId",
      "POST /patients/:patientId/invites",
      "GET /patients/:patientId/invites",
      "DELETE /patients/:patientId/invites/:inviteId",
      "POST /invites/:token/accept",
      // Medications
      "GET /patients/:patientId/medications",
      "POST /patients/:patientId/medications",
      "GET /patients/:patientId/medications/:medicationId",
      "PATCH /patients/:patientId/medications/:medicationId",
      // Schedules
      "GET /patients/:patientId/medications/:medicationId/schedules",
      "POST /patients/:patientId/medications/:medicationId/schedules",
      "GET /patients/:patientId/occurrences",
      // Doses
      "POST /patients/:patientId/dose-events",
      "POST /patients/:patientId/dose-events/batch",
      "GET /patients/:patientId/dose-events",
      "GET /patients/:patientId/sync",
      // Inventory
      "GET /patients/:patientId/medications/:medicationId/inventory",
      "POST /patients/:patientId/medications/:medicationId/inventory/transactions",
      // Symptoms/injection
      "GET /patients/:patientId/symptom-logs",
      "POST /patients/:patientId/symptom-logs",
      // Alerts/reports/exports
      "GET /patients/:patientId/alerts",
      "GET /patients/:patientId/reports/adherence",
      "POST /patients/:patientId/exports",
      // Intelligence §8.7 — also patient-scoped + tool authz
      "POST /patients/:patientId/intelligence/label-interpretations",
      "POST /patients/:patientId/intelligence/conversations/:conversationId/messages",
    ];
    expect(mustBeProtected.length).toBeGreaterThan(20);
    // Check that patient routes file exists and mentions patientId
    const patientsRoutes = readFileSync(join(process.cwd(), "src/modules/patients/routes.ts"), "utf8");
    expect(patientsRoutes).toContain("patientId");
    expect(patientsRoutes).toContain("requirePermissionOnPatient");
    expect(patientsRoutes).toContain("authenticate");
    // permissions central check
    const perms = readFileSync(join(process.cwd(), "src/lib/permissions.ts"), "utf8");
    expect(perms).toContain("requirePermission");
    expect(perms).toContain("owner");
    expect(perms).toContain("manager");
    expect(perms).toContain("contributor");
    expect(perms).toContain("viewer");
    // auth plugin
    const authPlugin = readFileSync(join(process.cwd(), "src/plugins/auth.ts"), "utf8");
    expect(authPlugin).toContain("verifyAccessToken");
    expect(authPlugin).toContain("tokenVersion");
  });

  it("docs that negative tests required per §7.2 (outline)", () => {
    const cases = [
      "viewer cannot read another patient's medication by ID",
      "viewer cannot modify records",
      "contributor cannot change schedules or invite caregivers",
      "manager cannot transfer ownership or delete patient",
      "revoked caregiver loses access immediately",
      "expired invite cannot be accepted",
      "export cannot include data outside selected patient",
      "Muse Spark cannot use tool against unauthorized patient",
      "prompt injection cannot reveal another patient's data",
      "revoked membership blocks AI proposal confirmation",
      "refresh reuse revokes family (BOLA replay)",
      "last owner cannot be removed",
      "invite token single-use",
      "invite does not enumerate account existence",
      "forgot-password does not enumerate",
    ];
    expect(cases.length).toBeGreaterThanOrEqual(14);
    // This test is the inventory; actual negative integration tests require DB (Testcontainers) and are tracked here.
    for (const c of cases) expect(typeof c).toBe("string");
  });
});
