import { describe, it, expect } from 'vitest';
import { SQL, Param } from 'drizzle-orm';
import { listSchedules, listOccurrences } from '../../src/modules/schedules/service.js';
import { toApi } from '../../src/modules/schedules/routes.js';
import type { MedacDb } from '../../src/db/index.js';

// Flatten a drizzle SQL tree into its raw fragments + bound params so tests can
// assert on the generated WHERE/ORDER BY without a live database.
function collectSql(node: unknown): { text: string; params: unknown[] } {
  const strings: string[] = [];
  const params: unknown[] = [];
  const walk = (n: unknown): void => {
    if (n instanceof SQL) {
      for (const c of n.queryChunks) walk(c);
    } else if (n instanceof Param) {
      params.push(n.value);
    } else if (typeof n === 'object' && n !== null && 'value' in n) {
      const v = (n as { value: unknown }).value;
      if (Array.isArray(v)) for (const s of v) if (typeof s === 'string') strings.push(s);
    }
  };
  walk(node);
  return { text: strings.join(''), params };
}

function fakeDb(overrides: Record<string, Record<string, unknown>>) {
  const membership = { id: 'mship-1', role: 'owner' };
  const medication = { id: 'med-1', patientId: 'patient-1' };
  return {
    query: {
      patientMemberships: { findFirst: async () => membership },
      patientMedications: { findFirst: async () => medication },
      ...overrides,
    },
  } as unknown as MedacDb;
}

describe('listSchedules — fixed times attached (fresh-install sync)', () => {
  it('returns each version with its child fixed_times rows', async () => {
    const versions = [
      { id: 'v-active', medicationId: 'med-1', versionNumber: 2 },
      { id: 'v-old', medicationId: 'med-1', versionNumber: 1 },
    ];
    const fixedTimes = [
      { id: 'ft-1', scheduleVersionId: 'v-active', localTime: '08:00', doseQuantityValue: '1', doseQuantityUnit: 'tablet' },
      { id: 'ft-2', scheduleVersionId: 'v-active', localTime: '20:00', doseQuantityValue: '1', doseQuantityUnit: 'tablet' },
    ];
    const db = fakeDb({
      medicationScheduleVersions: { findMany: async () => versions },
      scheduleFixedTimes: { findMany: async () => fixedTimes },
    });
    const result = await listSchedules(db, 'user-1', 'patient-1', 'med-1');
    expect(result).toHaveLength(2);
    expect(result[0].id).toBe('v-active');
    expect(result[0].fixedTimes.map((f) => f.localTime)).toEqual(['08:00', '20:00']);
    // Versions without child rows (e.g. PRN) get an empty list, not undefined.
    expect(result[1].fixedTimes).toEqual([]);
  });

  it('returns [] without querying fixed times when no versions exist', async () => {
    let fixedTimesQueried = false;
    const db = fakeDb({
      medicationScheduleVersions: { findMany: async () => [] },
      scheduleFixedTimes: { findMany: async () => { fixedTimesQueried = true; return []; } },
    });
    const result = await listSchedules(db, 'user-1', 'patient-1', 'med-1');
    expect(result).toEqual([]);
    expect(fixedTimesQueried).toBe(false);
  });
});

describe('listOccurrences — date window pushed into SQL', () => {
  const occRows = [
    { id: 'o1', scheduledAtUtc: new Date('2024-05-01T08:00:00Z') },
    { id: 'o2', scheduledAtUtc: new Date('2024-05-01T20:00:00Z') },
  ];

  it('filters scheduled_at_utc by from/to in the SQL where clause', async () => {
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    let captured: any;
    const db = fakeDb({
      doseOccurrences: { findMany: async (args: unknown) => { captured = args; return occRows; } },
    });
    const from = '2024-05-01T00:00:00Z';
    const to = '2024-05-02T00:00:00Z';
    await listOccurrences(db, 'user-1', 'patient-1', { from, to });
    expect(captured).toBeDefined();
    expect(captured.where).toBeDefined();
    const { text, params } = collectSql(captured.where);
    // Regression: the window must be in SQL, not applied in memory after a
    // "latest N" fetch (pre-generated future rows would crowd out today).
    expect(text).toContain('>=');
    expect(text).toContain('<=');
    expect(params).toContainEqual(new Date(from));
    expect(params).toContainEqual(new Date(to));
    expect(params).toContain('patient-1');
  });

  it('orders ascending when a date range is requested', async () => {
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    let captured: any;
    const db = fakeDb({
      doseOccurrences: { findMany: async (args: unknown) => { captured = args; return occRows; } },
    });
    await listOccurrences(db, 'user-1', 'patient-1', { from: '2024-05-01T00:00:00Z' });
    expect(captured.orderBy).toHaveLength(1);
    expect(collectSql(captured.orderBy[0]).text).toContain('asc');
  });

  it('returns occurrences within the window even when rows exist outside it', async () => {
    // Simulates a DB that (correctly) applies the SQL window: the service must
    // not drop in-window rows in its defensive in-memory filter.
    const db = fakeDb({
      doseOccurrences: { findMany: async () => occRows },
    });
    const { occurrences } = await listOccurrences(db, 'user-1', 'patient-1', {
      from: '2024-05-01T00:00:00Z',
      to: '2024-05-02T00:00:00Z',
    });
    expect(occurrences.map((o) => o.id)).toEqual(['o1', 'o2']);
  });

  it('keeps cursor pagination working without a range', async () => {
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    let captured: any;
    const db = fakeDb({
      doseOccurrences: { findMany: async (args: unknown) => { captured = args; return occRows; } },
    });
    await listOccurrences(db, 'user-1', 'patient-1', {});
    expect(captured.where).toBeDefined();
    expect(collectSql(captured.where).params).toContain('patient-1');
    expect(collectSql(captured.orderBy[0]).text).toContain('desc');
  });
});

describe('schedule toApi — fixed_times JSON contract', () => {
  const version = {
    id: 'v-1', medicationId: 'med-1', versionNumber: 1, scheduleType: 'fixed_times',
    timingMode: 'local_clock', timezone: 'UTC', effectiveFrom: '2024-01-01T00:00:00Z',
    effectiveUntil: null, missWindowMinutes: 60, createdByUserId: 'u-1',
    createdAt: '2024-01-01T00:00:00Z', updatedAt: '2024-01-01T00:00:00Z',
  };

  it('emits fixed_times in the Android ScheduleVersionDto shape', () => {
    const api = toApi(version, [
      { localTime: '08:00', doseQuantityValue: '1.5', doseQuantityUnit: 'tablet' },
      { localTime: '20:00', doseQuantityValue: null, doseQuantityUnit: null },
    ]);
    expect(api.fixed_times).toEqual([
      { local_time: '08:00', dose_quantity_value: '1.5', dose_quantity_unit: 'tablet' },
      { local_time: '20:00', dose_quantity_value: '1', dose_quantity_unit: 'tablet' },
    ]);
  });

  it('emits fixed_times null for schedules without fixed times (PRN)', () => {
    expect(toApi(version).fixed_times).toBeNull();
    expect(toApi(version, []).fixed_times).toEqual([]);
  });
});
