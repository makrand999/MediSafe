import { describe, it, expect } from "vitest";
import { can, requirePermission, listPermissions } from "../../src/lib/permissions.js";

describe("permission matrix §5.2", () => {
  it("owner can do everything", () => {
    expect(can("owner", "patient:delete")).toBe(true);
    expect(can("owner", "invite:create")).toBe(true);
    expect(can("owner", "medication:create")).toBe(true);
    expect(can("owner", "doseEvent:create")).toBe(true);
  });
  it("manager cannot transfer ownership or delete patient", () => {
    expect(can("manager", "patient:transferOwnership")).toBe(false);
    expect(can("manager", "patient:delete")).toBe(false);
    expect(can("manager", "medication:create")).toBe(true);
    expect(can("manager", "schedule:create")).toBe(true);
  });
  it("contributor can view and log dose events but not change schedules", () => {
    expect(can("contributor", "doseEvent:create")).toBe(true);
    expect(can("contributor", "schedule:create")).toBe(false);
    expect(can("contributor", "invite:create")).toBe(false);
    expect(can("contributor", "medication:update")).toBe(false);
  });
  it("viewer is read-only", () => {
    expect(can("viewer", "patient:read")).toBe(true);
    expect(can("viewer", "doseEvent:create")).toBe(false);
    expect(can("viewer", "medication:read")).toBe(true);
  });
  it("requirePermission throws 403", () => {
    expect(() => requirePermission("viewer", "medication:create")).toThrow(/Forbidden/);
  });
  it("listPermissions sizes are ordered owner > manager > contributor > viewer", () => {
    const o = listPermissions("owner").length;
    const m = listPermissions("manager").length;
    const c = listPermissions("contributor").length;
    const v = listPermissions("viewer").length;
    expect(o).toBeGreaterThan(m);
    expect(m).toBeGreaterThan(c);
    expect(c).toBeGreaterThan(v);
  });
});
