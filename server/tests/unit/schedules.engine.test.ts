import { describe, it, expect } from 'vitest';
import {
  generateOccurrences,
  previewOccurrences,
  calculateOccurrences,
  type ScheduleVersionInput
} from '../../src/modules/schedules/engine.js';

function mkVersion(overrides: Partial<ScheduleVersionInput> & { id: string }): ScheduleVersionInput {
  return {
    medication_id: 'med-1',
    version_number: 1,
    schedule_type: 'fixed_times',
    timing_mode: 'local_clock',
    timezone: 'America/New_York',
    effective_from: '2024-01-01T00:00:00Z',
    effective_until: null,
    ...overrides
  } as ScheduleVersionInput;
}

describe('Schedule Engine - pure deterministic', () => {
  it('rejects bare offset timezone', () => {
    const v = mkVersion({
      id: 'v1',
      timezone: '-05:00',
      schedule_type: 'fixed_times',
      fixed_times: [{ schedule_version_id: 'v1', local_time: '08:00', dose_quantity_value: '1', dose_quantity_unit: 'tablet' }]
    });
    expect(() =>
      generateOccurrences({
        scheduleVersion: v,
        rangeFrom: '2024-01-10T00:00:00Z',
        rangeTo: '2024-01-11T00:00:00Z',
        timezone: '-05:00'
      })
    ).toThrow(/IANA/);
  });

  it('one daily time', () => {
    const v = mkVersion({
      id: 'v-one',
      schedule_type: 'fixed_times',
      timezone: 'America/New_York',
      effective_from: '2024-01-01T00:00:00Z',
      fixed_times: [{ schedule_version_id: 'v-one', local_time: '08:00', dose_quantity_value: '1', dose_quantity_unit: 'tablet' }]
    });
    const occ = generateOccurrences({
      scheduleVersion: v,
      rangeFrom: '2024-01-10T00:00:00Z',
      rangeTo: '2024-01-13T00:00:00Z'
    });
    expect(occ).toHaveLength(3);
    // Verify deterministic keys and decimal as string
    for (const o of occ) {
      expect(typeof o.dose_value).toBe('string');
      expect(o.business_key).toBe(`${v.id}:${o.scheduled_at_utc}`);
      expect(o.timezone).toBe('America/New_York');
      expect(o.schedule_version_id).toBe('v-one');
    }
    // Check local times are 08:00
    expect(occ[0].scheduled_local_datetime).toBe('2024-01-10T08:00:00');
    // 08:00 EST is UTC 13:00
    expect(occ[0].scheduled_at_utc).toBe('2024-01-10T13:00:00.000Z');
    expect(occ[0].utc_offset_minutes).toBe(-300);
    expect(occ[0].dst_adjusted).toBe(false);
  });

  it('multiple daily times', () => {
    const v = mkVersion({
      id: 'v-multi',
      timezone: 'UTC',
      effective_from: '2024-01-01T00:00:00Z',
      fixed_times: [
        { schedule_version_id: 'v-multi', local_time: '08:00', dose_quantity_value: '1', dose_quantity_unit: 'tablet' },
        { schedule_version_id: 'v-multi', local_time: '20:00', dose_quantity_value: '2', dose_quantity_unit: 'tablet' }
      ]
    });
    const occ = generateOccurrences({
      scheduleVersion: v,
      rangeFrom: '2024-01-10T00:00:00Z',
      rangeTo: '2024-01-11T00:00:00Z'
    });
    expect(occ).toHaveLength(2);
    expect(occ[0].scheduled_local_datetime).toBe('2024-01-10T08:00:00');
    expect(occ[1].scheduled_local_datetime).toBe('2024-01-10T20:00:00');
    expect(occ[0].scheduled_at_utc).toBe('2024-01-10T08:00:00.000Z');
    expect(occ[1].scheduled_at_utc).toBe('2024-01-10T20:00:00.000Z');
  });

  it('weekdays filtering', () => {
    // Monday=1 .. Friday=5
    const v = mkVersion({
      id: 'v-weekday',
      timezone: 'UTC',
      effective_from: '2024-01-01T00:00:00Z',
      fixed_times: [
        {
          schedule_version_id: 'v-weekday',
          local_time: '09:00',
          days_of_week: [1, 2, 3, 4, 5],
          dose_quantity_value: '1',
          dose_quantity_unit: 'tablet'
        }
      ]
    });
    // 2024-01-08 Mon to 2024-01-15 Mon
    const occ = generateOccurrences({
      scheduleVersion: v,
      rangeFrom: '2024-01-08T00:00:00Z',
      rangeTo: '2024-01-15T00:00:00Z'
    });
    // Mon-Fri = 5 days
    expect(occ).toHaveLength(5);
    const dates = occ.map(o => o.scheduled_local_datetime.slice(0, 10));
    expect(dates).toEqual(['2024-01-08', '2024-01-09', '2024-01-10', '2024-01-11', '2024-01-12']);
  });

  it('month/year boundaries', () => {
    const v = mkVersion({
      id: 'v-boundary',
      timezone: 'UTC',
      effective_from: '2023-12-01T00:00:00Z',
      fixed_times: [{ schedule_version_id: 'v-boundary', local_time: '08:00', dose_quantity_value: '1', dose_quantity_unit: 'tablet' }]
    });
    const occ = generateOccurrences({
      scheduleVersion: v,
      rangeFrom: '2023-12-31T00:00:00Z',
      rangeTo: '2024-01-03T00:00:00Z'
    });
    expect(occ.map(o => o.scheduled_local_datetime)).toEqual([
      '2023-12-31T08:00:00',
      '2024-01-01T08:00:00',
      '2024-01-02T08:00:00'
    ]);
  });

  it('leap day', () => {
    const v = mkVersion({
      id: 'v-leap',
      timezone: 'UTC',
      effective_from: '2024-01-01T00:00:00Z',
      fixed_times: [{ schedule_version_id: 'v-leap', local_time: '08:00', dose_quantity_value: '1.5', dose_quantity_unit: 'mL' }]
    });
    const occ = generateOccurrences({
      scheduleVersion: v,
      rangeFrom: '2024-02-28T00:00:00Z',
      rangeTo: '2024-03-02T00:00:00Z'
    });
    expect(occ.map(o => o.scheduled_local_datetime.slice(0, 10))).toEqual(['2024-02-28', '2024-02-29', '2024-03-01']);
    expect(occ[1].dose_value).toBe('1.5');
  });

  it('spring-forward nonexistent time -> first valid instant + dst_adjusted=true', () => {
    const v = mkVersion({
      id: 'v-spring',
      timezone: 'America/New_York',
      effective_from: '2024-03-01T00:00:00Z',
      fixed_times: [{ schedule_version_id: 'v-spring', local_time: '02:30', dose_quantity_value: '1', dose_quantity_unit: 'tablet' }]
    });
    // DST in NY 2024: Mar 10 jumps 02:00->03:00
    const occ = generateOccurrences({
      scheduleVersion: v,
      rangeFrom: '2024-03-09T00:00:00Z',
      rangeTo: '2024-03-12T00:00:00Z'
    });
    // Should have 3 occurrences Mar9, Mar10, Mar11
    expect(occ).toHaveLength(3);
    const mar10 = occ.find(o => o.scheduled_local_datetime.startsWith('2024-03-10'));
    expect(mar10).toBeDefined();
    // nonexistent -> adjusted
    expect(mar10!.dst_adjusted).toBe(true);
    // Our implementation maps to 03:00 (first valid after gap)
    expect(mar10!.scheduled_local_datetime).toBe('2024-03-10T03:00:00');
    expect(mar10!.utc_offset_minutes).toBe(-240); // EDT
    // Verify Mar9 offset EST, Mar11 EDT
    const mar9 = occ.find(o => o.scheduled_local_datetime.startsWith('2024-03-09'))!;
    expect(mar9.utc_offset_minutes).toBe(-300);
    expect(mar9.dst_adjusted).toBe(false);
    const mar11 = occ.find(o => o.scheduled_local_datetime.startsWith('2024-03-11'))!;
    expect(mar11.utc_offset_minutes).toBe(-240);
  });

  it('fall-back ambiguous earlier by default', () => {
    const vEarly = mkVersion({
      id: 'v-fall-early',
      timezone: 'America/New_York',
      effective_from: '2024-11-01T00:00:00Z',
      fixed_times: [
        {
          schedule_version_id: 'v-fall-early',
          local_time: '01:30',
          dose_quantity_value: '1',
          dose_quantity_unit: 'tablet',
          ambiguous_time_policy: 'earlier'
        }
      ]
    });
    const occEarly = generateOccurrences({
      scheduleVersion: vEarly,
      rangeFrom: '2024-11-02T00:00:00Z',
      rangeTo: '2024-11-05T00:00:00Z'
    });
    const nov3Early = occEarly.find(o => o.scheduled_local_datetime.startsWith('2024-11-03'));
    expect(nov3Early).toBeDefined();
    expect(nov3Early!.scheduled_local_datetime).toBe('2024-11-03T01:30:00');
    // earlier => EDT offset -240 => UTC 05:30
    expect(nov3Early!.utc_offset_minutes).toBe(-240);
    expect(nov3Early!.scheduled_at_utc).toBe('2024-11-03T05:30:00.000Z');
    expect(nov3Early!.dst_adjusted).toBe(false);

    const vLater = mkVersion({
      id: 'v-fall-later',
      timezone: 'America/New_York',
      effective_from: '2024-11-01T00:00:00Z',
      fixed_times: [
        {
          schedule_version_id: 'v-fall-later',
          local_time: '01:30',
          dose_quantity_value: '1',
          dose_quantity_unit: 'tablet',
          ambiguous_time_policy: 'later'
        }
      ]
    });
    const occLater = generateOccurrences({
      scheduleVersion: vLater,
      rangeFrom: '2024-11-02T00:00:00Z',
      rangeTo: '2024-11-05T00:00:00Z'
    });
    const nov3Later = occLater.find(o => o.scheduled_local_datetime.startsWith('2024-11-03'));
    expect(nov3Later!.scheduled_at_utc).toBe('2024-11-03T06:30:00.000Z');
    expect(nov3Later!.utc_offset_minutes).toBe(-300);
    expect(nov3Later!.scheduled_at_utc).not.toBe(nov3Early!.scheduled_at_utc);
  });

  it('travel via new version/timezone - DST offset changes', () => {
    const vNY = mkVersion({
      id: 'v-ny',
      timezone: 'America/New_York',
      effective_from: '2024-06-01T00:00:00Z',
      effective_until: '2024-06-15T12:00:00Z',
      fixed_times: [{ schedule_version_id: 'v-ny', local_time: '08:00', dose_quantity_value: '1', dose_quantity_unit: 'tablet' }]
    });
    const vLA = mkVersion({
      id: 'v-la',
      timezone: 'America/Los_Angeles',
      effective_from: '2024-06-15T12:00:00Z',
      schedule_type: 'fixed_times',
      timing_mode: 'local_clock',
      medication_id: 'med-1',
      version_number: 2,
      fixed_times: [{ schedule_version_id: 'v-la', local_time: '08:00', dose_quantity_value: '1', dose_quantity_unit: 'tablet' }]
    });
    // Range spanning travel
    const occNY = generateOccurrences({
      scheduleVersion: vNY,
      rangeFrom: '2024-06-14T00:00:00Z',
      rangeTo: '2024-06-16T00:00:00Z'
    });
    const occLA = generateOccurrences({
      scheduleVersion: vLA,
      rangeFrom: '2024-06-14T00:00:00Z',
      rangeTo: '2024-06-17T00:00:00Z'
    });
    // NY 08:00 EDT = 12:00 UTC ; LA 08:00 PDT = 15:00 UTC
    const nyJune14 = occNY.find(o => o.scheduled_local_datetime === '2024-06-14T08:00:00');
    expect(nyJune14!.scheduled_at_utc).toBe('2024-06-14T12:00:00.000Z');
    expect(nyJune14!.utc_offset_minutes).toBe(-240);
    const laJune16 = occLA.find(o => o.scheduled_local_datetime === '2024-06-16T08:00:00');
    expect(laJune16!.scheduled_at_utc).toBe('2024-06-16T15:00:00.000Z');
    expect(laJune16!.utc_offset_minutes).toBe(-420);
    // Supersession: NY should not generate after effective_until
    expect(occNY.some(o => o.scheduled_at_utc >= '2024-06-15T12:00:00.000Z')).toBe(false);
    expect(occLA.some(o => o.scheduled_at_utc < '2024-06-15T12:00:00.000Z')).toBe(false);
  });

  it('elapsed interval across DST keeps elapsed time', () => {
    const v = mkVersion({
      id: 'v-elapsed',
      schedule_type: 'elapsed_interval',
      timing_mode: 'elapsed_interval',
      timezone: 'America/New_York',
      effective_from: '2024-03-08T00:00:00Z',
      interval: {
        schedule_version_id: 'v-elapsed',
        interval_minutes: 24 * 60,
        anchor_at: '2024-03-09T13:00:00.000Z', // 08:00 EST Mar9
        dose_quantity_value: '10',
        dose_quantity_unit: 'mg',
        anchor_policy: 'fixed'
      }
    });
    const occ = generateOccurrences({
      scheduleVersion: v,
      rangeFrom: '2024-03-09T00:00:00Z',
      rangeTo: '2024-03-12T00:00:00Z'
    });
    // Every 24h elapsed: anchor Mar9 13UTC, then Mar10 13UTC, Mar11 13UTC
    expect(occ.map(o => o.scheduled_at_utc)).toEqual([
      '2024-03-09T13:00:00.000Z',
      '2024-03-10T13:00:00.000Z',
      '2024-03-11T13:00:00.000Z'
    ]);
    // Local times: Mar9 08:00 EST (-300), Mar10 09:00 EDT (-240) because elapsed shifts wall, Mar11 09:00 EDT
    expect(occ[0].scheduled_local_datetime).toBe('2024-03-09T08:00:00');
    expect(occ[0].utc_offset_minutes).toBe(-300);
    expect(occ[1].scheduled_local_datetime).toBe('2024-03-10T09:00:00');
    expect(occ[1].utc_offset_minutes).toBe(-240);
    // vs local_clock fixed 08:00 would stay 08:00 local and UTC shift 13->12
    const vFixed = mkVersion({
      id: 'v-fixed-compare',
      schedule_type: 'fixed_times',
      timing_mode: 'local_clock',
      timezone: 'America/New_York',
      effective_from: '2024-03-08T00:00:00Z',
      fixed_times: [{ schedule_version_id: 'v-fixed-compare', local_time: '08:00', dose_quantity_value: '10', dose_quantity_unit: 'mg' }]
    });
    const occFixed = generateOccurrences({
      scheduleVersion: vFixed,
      rangeFrom: '2024-03-09T00:00:00Z',
      rangeTo: '2024-03-12T00:00:00Z'
    });
    expect(occFixed[0].scheduled_at_utc).toBe('2024-03-09T13:00:00.000Z');
    expect(occFixed[1].scheduled_at_utc).toBe('2024-03-10T12:00:00.000Z'); // one hour earlier UTC because wall stays 08:00 EDT
    expect(occFixed[1].scheduled_local_datetime).toBe('2024-03-10T08:00:00');
  });

  it('elapsed anchor_policy last_taken shifts', () => {
    const v = mkVersion({
      id: 'v-elapsed-last',
      schedule_type: 'elapsed_interval',
      timing_mode: 'elapsed_interval',
      timezone: 'UTC',
      effective_from: '2024-01-01T00:00:00Z',
      interval: {
        schedule_version_id: 'v-elapsed-last',
        interval_minutes: 8 * 60,
        anchor_at: '2024-01-10T08:00:00.000Z',
        dose_quantity_value: '1',
        dose_quantity_unit: 'tablet',
        anchor_policy: 'last_taken'
      }
    });
    const occFixed = generateOccurrences({
      scheduleVersion: v,
      rangeFrom: '2024-01-10T00:00:00Z',
      rangeTo: '2024-01-11T00:00:00Z'
    });
    expect(occFixed.map(o => o.scheduled_at_utc)).toEqual([
      '2024-01-10T08:00:00.000Z',
      '2024-01-10T16:00:00.000Z',
      '2024-01-11T00:00:00.000Z'
    ].slice(0, 2)); // up to exclusive
    // Now with lastTaken at 10:00
    const occShifted = generateOccurrences({
      scheduleVersion: v,
      rangeFrom: '2024-01-10T00:00:00Z',
      rangeTo: '2024-01-11T00:00:00Z',
      lastTakenAt: '2024-01-10T10:00:00.000Z'
    });
    expect(occShifted.map(o => o.scheduled_at_utc)).toEqual([
      '2024-01-10T10:00:00.000Z',
      '2024-01-10T18:00:00.000Z'
    ]);
  });

  it('cyclic boundaries', () => {
    const v = mkVersion({
      id: 'v-cyclic',
      schedule_type: 'cyclic',
      timing_mode: 'local_clock',
      timezone: 'UTC',
      effective_from: '2024-01-01T00:00:00Z',
      cycle: {
        schedule_version_id: 'v-cyclic',
        cycle_anchor_date: '2024-01-01',
        on_days: 3,
        off_days: 4,
        local_time: '08:00',
        dose_quantity_value: '1',
        dose_quantity_unit: 'tablet'
      }
    });
    // Cycle len 7, on 3 off 4: On Jan1-3, off 4-7, on 8-10 etc.
    const occ = generateOccurrences({
      scheduleVersion: v,
      rangeFrom: '2024-01-01T00:00:00Z',
      rangeTo: '2024-01-15T00:00:00Z'
    });
    const dates = occ.map(o => o.scheduled_local_datetime.slice(0, 10));
    expect(dates).toEqual([
      '2024-01-01',
      '2024-01-02',
      '2024-01-03',
      // gap
      '2024-01-08',
      '2024-01-09',
      '2024-01-10'
      // next on would be 15 but range exclusive 15
    ]);
  });

  it('taper boundaries - sequential non-overlapping steps', () => {
    const v = mkVersion({
      id: 'v-taper',
      schedule_type: 'taper',
      timing_mode: 'local_clock',
      timezone: 'UTC',
      effective_from: '2024-01-01T00:00:00Z',
      taper_steps: [
        {
          id: 's1',
          schedule_version_id: 'v-taper',
          step_order: 1,
          starts_on: '2024-01-01',
          ends_on: '2024-01-03',
          dose_quantity_value: '2',
          dose_quantity_unit: 'tablet',
          local_time: '08:00'
        },
        {
          id: 's2',
          schedule_version_id: 'v-taper',
          step_order: 2,
          starts_on: '2024-01-04',
          ends_on: '2024-01-06',
          dose_quantity_value: '1',
          dose_quantity_unit: 'tablet',
          local_time: '08:00'
        },
        {
          id: 's3',
          schedule_version_id: 'v-taper',
          step_order: 3,
          starts_on: '2024-01-07',
          ends_on: '2024-01-07',
          dose_quantity_value: '0.5',
          dose_quantity_unit: 'tablet',
          local_time: '08:00'
        }
      ]
    });
    const occ = generateOccurrences({
      scheduleVersion: v,
      rangeFrom: '2024-01-01T00:00:00Z',
      rangeTo: '2024-01-08T00:00:00Z'
    });
    expect(occ).toHaveLength(7);
    expect(occ.slice(0, 3).every(o => o.dose_value === '2')).toBe(true);
    expect(occ.slice(3, 6).every(o => o.dose_value === '1')).toBe(true);
    expect(occ[6].dose_value).toBe('0.5');
    expect(occ.map(o => o.scheduled_local_datetime.slice(0, 10))).toEqual([
      '2024-01-01',
      '2024-01-02',
      '2024-01-03',
      '2024-01-04',
      '2024-01-05',
      '2024-01-06',
      '2024-01-07'
    ]);

    // overlapping taper should throw
    const vOverlap = mkVersion({
      id: 'v-taper-overlap',
      schedule_type: 'taper',
      timing_mode: 'local_clock',
      timezone: 'UTC',
      effective_from: '2024-01-01T00:00:00Z',
      taper_steps: [
        {
          id: 's1',
          schedule_version_id: 'v-taper-overlap',
          step_order: 1,
          starts_on: '2024-01-01',
          ends_on: '2024-01-05',
          dose_quantity_value: '2',
          dose_quantity_unit: 'tablet'
        },
        {
          id: 's2',
          schedule_version_id: 'v-taper-overlap',
          step_order: 2,
          starts_on: '2024-01-04',
          ends_on: '2024-01-06',
          dose_quantity_value: '1',
          dose_quantity_unit: 'tablet'
        }
      ]
    });
    expect(() =>
      generateOccurrences({
        scheduleVersion: vOverlap,
        rangeFrom: '2024-01-01T00:00:00Z',
        rangeTo: '2024-01-08T00:00:00Z'
      })
    ).toThrow(/TAPER_OVERLAP/);
  });

  it('pause overlapping window', () => {
    const v = mkVersion({
      id: 'v-pause',
      timezone: 'UTC',
      effective_from: '2024-01-01T00:00:00Z',
      fixed_times: [{ schedule_version_id: 'v-pause', local_time: '08:00', dose_quantity_value: '1', dose_quantity_unit: 'tablet' }]
    });
    const occNoPause = generateOccurrences({
      scheduleVersion: v,
      rangeFrom: '2024-01-10T00:00:00Z',
      rangeTo: '2024-01-15T00:00:00Z'
    });
    expect(occNoPause).toHaveLength(5);
    const pauses = [
      {
        id: 'pause1',
        medication_id: 'med-1',
        starts_at: '2024-01-12T00:00:00Z',
        ends_at: '2024-01-13T12:00:00Z'
      }
    ];
    const occPaused = generateOccurrences({
      scheduleVersion: v,
      rangeFrom: '2024-01-10T00:00:00Z',
      rangeTo: '2024-01-15T00:00:00Z',
      pausePeriods: pauses
    });
    // Jan12 08:00 falls inside pause, Jan13 08:00 also inside (ends 12:00)
    expect(occPaused.map(o => o.scheduled_local_datetime.slice(0, 10))).toEqual(['2024-01-10', '2024-01-11', '2024-01-14']);
  });

  it('medication start/end filtering', () => {
    const v = mkVersion({
      id: 'v-meds',
      timezone: 'UTC',
      effective_from: '2024-01-01T00:00:00Z',
      fixed_times: [{ schedule_version_id: 'v-meds', local_time: '08:00', dose_quantity_value: '1', dose_quantity_unit: 'tablet' }]
    });
    const occ = generateOccurrences({
      scheduleVersion: v,
      rangeFrom: '2024-01-01T00:00:00Z',
      rangeTo: '2024-01-10T00:00:00Z',
      medicationStartEnd: { start_date: '2024-01-03', end_date: '2024-01-06' }
    });
    expect(occ.map(o => o.scheduled_local_datetime.slice(0, 10))).toEqual(['2024-01-03', '2024-01-04', '2024-01-05', '2024-01-06']);
  });

  it('supersession via effective_from/until', () => {
    const v = mkVersion({
      id: 'v-sup',
      timezone: 'UTC',
      effective_from: '2024-01-10T10:00:00Z',
      effective_until: '2024-01-12T10:00:00Z',
      fixed_times: [{ schedule_version_id: 'v-sup', local_time: '08:00', dose_quantity_value: '1', dose_quantity_unit: 'tablet' }]
    });
    const occ = generateOccurrences({
      scheduleVersion: v,
      rangeFrom: '2024-01-09T00:00:00Z',
      rangeTo: '2024-01-14T00:00:00Z'
    });
    // Only Jan11 08:00 (Jan10 08:00 is before effective_from 10:00, Jan12 08:00 is after? Jan12 08:00 is before 10:00 so included? Check logic inclusive)
    // Effective_until exclusive: Jan12 08:00 < 10:00 so included, Jan13 excluded
    // So Jan11 and Jan12
    expect(occ.map(o => o.scheduled_local_datetime.slice(0, 10))).toEqual(['2024-01-11', '2024-01-12']);
    // Also test before effective_from not included
    expect(occ.some(o => o.scheduled_local_datetime.startsWith('2024-01-10'))).toBe(false);
  });

  it('duplicate job idempotency - same input same keys', () => {
    const v = mkVersion({
      id: 'v-idempotent',
      timezone: 'America/New_York',
      effective_from: '2024-03-09T00:00:00Z',
      fixed_times: [
        { schedule_version_id: 'v-idempotent', local_time: '08:00', dose_quantity_value: '1', dose_quantity_unit: 'tablet' },
        { schedule_version_id: 'v-idempotent', local_time: '20:00', dose_quantity_value: '1', dose_quantity_unit: 'tablet' }
      ]
    });
    const input = {
      scheduleVersion: v,
      rangeFrom: '2024-03-09T00:00:00Z',
      rangeTo: '2024-03-12T00:00:00Z'
    };
    const a = generateOccurrences(input);
    const b = generateOccurrences(input);
    expect(a).toEqual(b);
    // keys unique and stable
    const keys = a.map(o => o.business_key);
    expect(new Set(keys).size).toBe(keys.length);
  });

  it('PRN excluded from generation', () => {
    const v = mkVersion({
      id: 'v-prn',
      schedule_type: 'prn',
      timing_mode: 'local_clock',
      timezone: 'UTC',
      effective_from: '2024-01-01T00:00:00Z'
    });
    const occ = generateOccurrences({
      scheduleVersion: v,
      rangeFrom: '2024-01-10T00:00:00Z',
      rangeTo: '2024-01-11T00:00:00Z'
    });
    expect(occ).toHaveLength(0);
    const occPreview = generateOccurrences({
      scheduleVersion: v,
      rangeFrom: '2024-01-10T00:00:00Z',
      rangeTo: '2024-01-11T00:00:00Z',
      includePrnPreview: true
    });
    expect(occPreview).toHaveLength(0);
  });

  it('single calculation for preview/persistence/reporting', () => {
    const v = mkVersion({
      id: 'v-single',
      timezone: 'UTC',
      effective_from: '2024-01-01T00:00:00Z',
      fixed_times: [{ schedule_version_id: 'v-single', local_time: '09:00', dose_quantity_value: '1', dose_quantity_unit: 'tablet' }]
    });
    const input = {
      scheduleVersion: v,
      rangeFrom: '2024-01-10T00:00:00Z',
      rangeTo: '2024-01-12T00:00:00Z'
    };
    expect(generateOccurrences(input)).toEqual(previewOccurrences(input));
    expect(generateOccurrences(input)).toEqual(calculateOccurrences(input));
  });

  it('taper with fixed_times per step', () => {
    const v = mkVersion({
      id: 'v-taper-ft',
      schedule_type: 'taper',
      timing_mode: 'local_clock',
      timezone: 'UTC',
      effective_from: '2024-01-01T00:00:00Z',
      taper_steps: [
        {
          id: 's1',
          schedule_version_id: 'v-taper-ft',
          step_order: 1,
          starts_on: '2024-01-10',
          ends_on: '2024-01-11',
          dose_quantity_value: '2',
          dose_quantity_unit: 'tablet',
          fixed_times: [
            { schedule_version_id: 'v-taper-ft', local_time: '08:00', dose_quantity_value: '2', dose_quantity_unit: 'tablet' },
            { schedule_version_id: 'v-taper-ft', local_time: '20:00', dose_quantity_value: '2', dose_quantity_unit: 'tablet' }
          ]
        }
      ]
    });
    const occ = generateOccurrences({
      scheduleVersion: v,
      rangeFrom: '2024-01-10T00:00:00Z',
      rangeTo: '2024-01-12T00:00:00Z'
    });
    expect(occ).toHaveLength(4);
  });

  it('concurrent occurrence generation idempotent (simulated parallel)', async () => {
    const v = mkVersion({
      id: 'v-concurrent',
      timezone: 'UTC',
      effective_from: '2024-01-01T00:00:00Z',
      fixed_times: [{ schedule_version_id: 'v-concurrent', local_time: '08:00', dose_quantity_value: '1', dose_quantity_unit: 'tablet' }]
    });
    const input = {
      scheduleVersion: v,
      rangeFrom: '2024-01-10T00:00:00Z',
      rangeTo: '2024-01-15T00:00:00Z'
    };
    const results = await Promise.all([
      Promise.resolve(generateOccurrences(input)),
      Promise.resolve(generateOccurrences(input)),
      Promise.resolve(generateOccurrences(input))
    ]);
    expect(results[0]).toEqual(results[1]);
    expect(results[1]).toEqual(results[2]);
  });

  it('elapsed interval with timezone UTC vs America/New_York same UTC', () => {
    const v = mkVersion({
      id: 'v-elapsed-utc',
      schedule_type: 'elapsed_interval',
      timing_mode: 'elapsed_interval',
      timezone: 'UTC',
      effective_from: '2024-01-01T00:00:00Z',
      interval: {
        schedule_version_id: 'v-elapsed-utc',
        interval_minutes: 60,
        anchor_at: '2024-01-10T00:00:00.000Z',
        dose_quantity_value: '1',
        dose_quantity_unit: 'mg'
      }
    });
    const occUtc = generateOccurrences({
      scheduleVersion: v,
      rangeFrom: '2024-01-10T00:00:00Z',
      rangeTo: '2024-01-10T04:00:00Z'
    });
    const vNy = { ...v, id: 'v-elapsed-ny', timezone: 'America/New_York', interval: { ...v.interval!, schedule_version_id: 'v-elapsed-ny' } };
    const occNy = generateOccurrences({
      scheduleVersion: vNy,
      rangeFrom: '2024-01-10T00:00:00Z',
      rangeTo: '2024-01-10T04:00:00Z'
    });
    // UTC instants same regardless of tz
    expect(occUtc.map(o => o.scheduled_at_utc)).toEqual(occNy.map(o => o.scheduled_at_utc));
    // local datetimes differ
    expect(occUtc[0].scheduled_local_datetime).not.toBe(occNy[0].scheduled_local_datetime);
    expect(occNy[0].utc_offset_minutes).toBe(-300);
  });
});
