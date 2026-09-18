/**
 * lib/permissions.ts — Central permission matrix (plan §5.2, §7)
 * No ad hoc role checks in handlers; import from here.
 */

export type PatientRole = "owner" | "manager" | "contributor" | "viewer";

export type Permission =
  | "patient:read"
  | "patient:update"
  | "patient:archive"
  | "patient:delete"
  | "patient:transferOwnership"
  | "membership:read"
  | "membership:update"
  | "membership:revoke"
  | "invite:create"
  | "invite:read"
  | "invite:revoke"
  | "medication:read"
  | "medication:create"
  | "medication:update"
  | "medication:activate"
  | "medication:pause"
  | "medication:resume"
  | "medication:discontinue"
  | "medication:archive"
  | "schedule:read"
  | "schedule:create"
  | "schedule:supersede"
  | "occurrence:read"
  | "doseEvent:read"
  | "doseEvent:create"
  | "doseEvent:correct"
  | "inventory:read"
  | "inventory:write"
  | "symptom:read"
  | "symptom:write"
  | "injection:read"
  | "injection:write"
  | "alert:read"
  | "alert:acknowledge"
  | "alertPreference:read"
  | "alertPreference:write"
  | "export:create"
  | "export:read"
  | "adherence:read"
  | "consent:read"
  | "consent:write"
  | "intelligence:read"
  | "intelligence:write";

/**
 * Matrix: role -> allowed permissions
 * Owner: everything. Manager: medication/schedule/dose/inventory/notes but not ownership/delete.
 * Contributor: view + log dose events, not medication definitions or memberships.
 * Viewer: read-only.
 */
const MATRIX: Record<PatientRole, Set<Permission>> = {
  owner: new Set<Permission>([
    "patient:read",
    "patient:update",
    "patient:archive",
    "patient:delete",
    "patient:transferOwnership",
    "membership:read",
    "membership:update",
    "membership:revoke",
    "invite:create",
    "invite:read",
    "invite:revoke",
    "medication:read",
    "medication:create",
    "medication:update",
    "medication:activate",
    "medication:pause",
    "medication:resume",
    "medication:discontinue",
    "medication:archive",
    "schedule:read",
    "schedule:create",
    "schedule:supersede",
    "occurrence:read",
    "doseEvent:read",
    "doseEvent:create",
    "doseEvent:correct",
    "inventory:read",
    "inventory:write",
    "symptom:read",
    "symptom:write",
    "injection:read",
    "injection:write",
    "alert:read",
    "alert:acknowledge",
    "alertPreference:read",
    "alertPreference:write",
    "export:create",
    "export:read",
    "adherence:read",
    "consent:read",
    "consent:write",
    "intelligence:read",
    "intelligence:write",
  ]),
  manager: new Set<Permission>([
    "patient:read",
    "patient:update",
    "membership:read",
    "invite:create",
    "invite:read",
    "invite:revoke",
    "medication:read",
    "medication:create",
    "medication:update",
    "medication:activate",
    "medication:pause",
    "medication:resume",
    "medication:discontinue",
    "medication:archive",
    "schedule:read",
    "schedule:create",
    "schedule:supersede",
    "occurrence:read",
    "doseEvent:read",
    "doseEvent:create",
    "doseEvent:correct",
    "inventory:read",
    "inventory:write",
    "symptom:read",
    "symptom:write",
    "injection:read",
    "injection:write",
    "alert:read",
    "alert:acknowledge",
    "alertPreference:read",
    "alertPreference:write",
    "export:create",
    "export:read",
    "adherence:read",
    "consent:read",
    "intelligence:read",
    "intelligence:write",
  ]),
  contributor: new Set<Permission>([
    "patient:read",
    "membership:read",
    "medication:read",
    "schedule:read",
    "occurrence:read",
    "doseEvent:read",
    "doseEvent:create",
    "inventory:read",
    "symptom:read",
    "symptom:write",
    "injection:read",
    "injection:write",
    "alert:read",
    "alert:acknowledge",
    "alertPreference:read",
    "export:read",
    "adherence:read",
    "intelligence:read",
    "intelligence:write",
  ]),
  viewer: new Set<Permission>([
    "patient:read",
    "membership:read",
    "medication:read",
    "schedule:read",
    "occurrence:read",
    "doseEvent:read",
    "inventory:read",
    "symptom:read",
    "injection:read",
    "alert:read",
    "alertPreference:read",
    "export:read",
    "adherence:read",
    "intelligence:read",
  ]),
};

export function can(role: PatientRole, permission: Permission): boolean {
  return MATRIX[role]?.has(permission) ?? false;
}

export function requirePermission(role: PatientRole, permission: Permission): void {
  if (!can(role, permission)) {
    const err = new Error(`Forbidden: role ${role} lacks ${permission}`) as Error & { statusCode?: number; code?: string };
    err.statusCode = 403;
    err.code = "FORBIDDEN";
    throw err;
  }
}

export function assertValidRole(role: string): asserts role is PatientRole {
  if (!["owner", "manager", "contributor", "viewer"].includes(role)) {
    const err = new Error(`Invalid role ${role}`) as Error & { statusCode?: number };
    err.statusCode = 400;
    throw err;
  }
}

/** For tests: expose matrix size */
export function listPermissions(role: PatientRole): Permission[] {
  return [...(MATRIX[role] ?? new Set())];
}

/** Actions that require recent authentication (plan §5.2 sensitive) */
export const REAUTH_REQUIRED: Set<Permission> = new Set<Permission>([
  "patient:transferOwnership",
  "patient:delete",
  "membership:revoke",
  "membership:update",
]);
export function requiresReauth(permission: Permission): boolean {
  return REAUTH_REQUIRED.has(permission);
}
