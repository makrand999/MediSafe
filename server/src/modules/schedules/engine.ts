/**
 * Pure deterministic schedule & occurrence engine
 * Implements plan sections 5.5, 5.6, 6.4, 6.5, 14
 * No DB calls, no I/O. Deterministic.
 */

// ---------------------------------------------------------------------------
// Types (mirroring DB tables as pure interfaces)
// ---------------------------------------------------------------------------

export type ScheduleType = 'fixed_times' | 'elapsed_interval' | 'prn' | 'cyclic' | 'taper';
export type TimingMode = 'local_clock' | 'elapsed_interval';

export interface MedicationScheduleVersion {
  id: string;
  medication_id: string;
  version_number: number;
  schedule_type: ScheduleType;
  timing_mode: TimingMode;
  timezone: string; // IANA only
  effective_from: string; // ISO timestamptz
  effective_until?: string | null;
  miss_window_minutes?: number | null;
}

export interface ScheduleFixedTime {
  id?: string;
  schedule_version_id: string;
  local_time: string; // HH:mm or HH:mm:ss
  days_of_week?: number[] | null; // 0=Sun .. 6=Sat ; null => every day
  dose_quantity_value: string; // decimal as string
  dose_quantity_unit: string;
  ambiguous_time_policy?: 'earlier' | 'later';
}

export interface ScheduleInterval {
  schedule_version_id: string;
  interval_minutes: number;
  anchor_at: string; // ISO
  dose_quantity_value: string;
  dose_quantity_unit: string;
  anchor_policy?: 'fixed' | 'last_taken';
}

export interface ScheduleCycle {
  schedule_version_id: string;
  cycle_anchor_date: string; // YYYY-MM-DD
  on_days: number;
  off_days: number;
  // during ON days, dose at local_time
  local_time?: string; // HH:mm
  dose_quantity_value: string;
  dose_quantity_unit: string;
  ambiguous_time_policy?: 'earlier' | 'later';
}

export interface TaperStep {
  id: string;
  schedule_version_id: string;
  step_order: number;
  starts_on: string; // YYYY-MM-DD inclusive
  ends_on: string; // YYYY-MM-DD inclusive
  dose_quantity_value: string;
  dose_quantity_unit: string;
  // optional fixed times for this step; if empty use local_time
  fixed_times?: ScheduleFixedTime[] | null;
  local_time?: string | null; // fallback single time per day
  ambiguous_time_policy?: 'earlier' | 'later';
  days_of_week?: number[] | null;
}

export interface MedicationPausePeriod {
  id: string;
  medication_id: string;
  starts_at: string; // ISO
  ends_at: string; // ISO
  reason?: string;
}

export interface ScheduleVersionInput extends MedicationScheduleVersion {
  fixed_times?: ScheduleFixedTime[];
  interval?: ScheduleInterval;
  cycle?: ScheduleCycle;
  taper_steps?: TaperStep[];
}

export interface GenerateOccurrencesInput {
  scheduleVersion: ScheduleVersionInput;
  rangeFrom: string; // ISO UTC
  rangeTo: string; // ISO UTC
  // timezone override optional; if provided must match scheduleVersion.timezone or be IANA; we validate.
  // If omitted, use scheduleVersion.timezone
  timezone?: string;
  pausePeriods?: MedicationPausePeriod[];
  medicationStartEnd?: { start_date?: string | null; end_date?: string | null };
  lastTakenAt?: string | null; // for anchor_policy last_taken preview
  includePrnPreview?: boolean;
}

export interface OccurrenceCandidate {
  business_key: string;
  scheduled_at_utc: string; // ISO
  scheduled_local_datetime: string; // YYYY-MM-DDTHH:mm:ss (no offset)
  timezone: string;
  utc_offset_minutes: number;
  dst_adjusted: boolean;
  dose_value: string; // string decimal
  dose_unit: string;
  schedule_version_id: string;
  // debug helpers not required but useful
  // medication_id?: string;
}

// ---------------------------------------------------------------------------
// Validation helpers
// ---------------------------------------------------------------------------

const IANA_REJECT_OFFSET = /^[+-]\d{1,2}(:\d{2})?$/;
const LOCAL_TIME_RE = /^([01]\d|2[0-3]):[0-5]\d(:[0-5]\d)?$/;
const DATE_RE = /^\d{4}-\d{2}-\d{2}$/;

function assertIanaTimezone(tz: string): void {
  if (!tz || typeof tz !== 'string') throw new Error(`INVALID_TIMEZONE: timezone required`);
  if (IANA_REJECT_OFFSET.test(tz.trim())) {
    throw new Error(`INVALID_TIMEZONE: bare offset not allowed, use IANA zone: ${tz}`);
  }
  if (tz.includes('/') === false && tz !== 'UTC') {
    // allow UTC but otherwise require slash; quick heuristic
    // Still test via Intl for full validation
  }
  try {
    new Intl.DateTimeFormat('en-US', { timeZone: tz });
  } catch {
    throw new Error(`INVALID_TIMEZONE: unknown IANA timezone ${tz}`);
  }
}

function parseLocalTime(t: string): { h: number; m: number; s: number } {
  if (!LOCAL_TIME_RE.test(t)) throw new Error(`INVALID_LOCAL_TIME: ${t}`);
  const [hh, mm, ss] = t.split(':').map(Number);
  return { h: hh, m: mm, s: ss ?? 0 };
}

function parseIsoOrThrow(s: string, label: string): Date {
  const d = new Date(s);
  if (Number.isNaN(d.getTime())) throw new Error(`INVALID_ISO: ${label} = ${s}`);
  return d;
}

// ---------------------------------------------------------------------------
// Timezone helpers using Intl
// ---------------------------------------------------------------------------

interface WallParts {
  year: number;
  month: number;
  day: number;
  hour: number;
  minute: number;
  second: number;
}

const formatterCache = new Map<string, Intl.DateTimeFormat>();

function getFormatter(tz: string): Intl.DateTimeFormat {
  let fmt = formatterCache.get(tz);
  if (!fmt) {
    fmt = new Intl.DateTimeFormat('en-US', {
      timeZone: tz,
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit',
      hour12: false,
      hourCycle: 'h23'
    });
    formatterCache.set(tz, fmt);
  }
  return fmt;
}

function getWallParts(dateUtc: Date, tz: string): WallParts {
  const fmt = getFormatter(tz);
  const parts = fmt.formatToParts(dateUtc);
  const map: Record<string, string> = {};
  for (const p of parts) map[p.type] = p.value;
  return {
    year: Number(map.year),
    month: Number(map.month),
    day: Number(map.day),
    hour: Number(map.hour),
    minute: Number(map.minute),
    second: Number(map.second)
  };
}

function getOffsetMinutes(dateUtc: Date, tz: string): number {
  const wall = getWallParts(dateUtc, tz);
  const wallAsUtcMs = Date.UTC(wall.year, wall.month - 1, wall.day, wall.hour, wall.minute, wall.second);
  // wallAsUtc - dateUtc => positive if tz east of UTC? Actually offset = wallAsUtc - dateUtc ??? Let's verify
  // For New York EDT (UTC-4): UTC 07:00 => wall 03:00 => wallAsUtc 03:00 => diff -4h => -240 correct
  return (wallAsUtcMs - dateUtc.getTime()) / 60000;
  // note: for tz east like Europe/Paris UTC+2: UTC 10:00 => wall 12:00 => wallAsUtc 12:00 => diff +2h => +120
}

function formatLocalDateTime(w: WallParts): string {
  const pad = (n: number, len = 2) => String(n).padStart(len, '0');
  return `${pad(w.year, 4)}-${pad(w.month)}-${pad(w.day)}T${pad(w.hour)}:${pad(w.minute)}:${pad(w.second)}`;
}

function _dateToLocalString(dateUtc: Date, tz: string): string {
  const w = getWallParts(dateUtc, tz);
  return formatLocalDateTime(w);
}

function dateToLocalDateString(dateUtc: Date, tz: string): string {
  const w = getWallParts(dateUtc, tz);
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${String(w.year).padStart(4, '0')}-${pad(w.month)}-${pad(w.day)}`;
}

// Resolve wall local "YYYY-MM-DDTHH:mm:ss" in tz to UTC.
// Handles DST gap (spring-forward nonexistent) -> first valid after gap, dst_adjusted true
// Handles ambiguous (fall-back) -> earlier vs later per policy
function resolveLocalToUtc(
  localYmd: string,
  localTime: string,
  tz: string,
  ambiguousPolicy: 'earlier' | 'later' = 'earlier'
): { utc: Date; offsetMinutes: number; dstAdjusted: boolean; adjustedLocal: string } {
  const parsed = parseLocalTime(localTime);
  const [ys, ms, ds] = localYmd.split('-').map(Number);
  const targetWallMs = Date.UTC(ys, ms - 1, ds, parsed.h, parsed.m, parsed.s);

  // Search window +-24h around target as UTC guesses step 1 minute
  const startMs = targetWallMs - 24 * 3600 * 1000;
  const endMs = targetWallMs + 24 * 3600 * 1000;

  let earliestMatch: { utcMs: number; offset: number; wall: WallParts } | null = null;
  let latestMatch: { utcMs: number; offset: number; wall: WallParts } | null = null;
  let firstAfter: { utcMs: number; offset: number; wall: WallParts; wallMs: number } | null = null;

  for (let ms = startMs; ms <= endMs; ms += 60 * 1000) {
    const date = new Date(ms);
    const wall = getWallParts(date, tz);
    const wallMs = Date.UTC(wall.year, wall.month - 1, wall.day, wall.hour, wall.minute, wall.second);
    const offset = (wallMs - ms) / 60000; // same as getOffsetMinutes but avoid extra call

    if (wallMs === targetWallMs) {
      if (!earliestMatch) earliestMatch = { utcMs: ms, offset, wall };
      latestMatch = { utcMs: ms, offset, wall };
    }
    if (wallMs > targetWallMs) {
      if (!firstAfter) {
        firstAfter = { utcMs: ms, offset, wall, wallMs };
        // Since we scan ascending utc, first wallMs > target is minimal > target
        // For nonexistent case we could break? But we also need to know if there was exact match later
        // For gap, exact matches don't exist, so firstAfter is the gap end.
        // We continue to ensure no exact match appears later (scanning whole window ensures ambiguous collected)
        // But we could not break; for performance keep scanning for matches
      }
    }
  }

  if (earliestMatch) {
    // ambiguous or single
    const chosen = ambiguousPolicy === 'later' && latestMatch ? latestMatch : earliestMatch;
    // For ambiguous, dstAdjusted false because not gap; but we could set false.
    return {
      utc: new Date(chosen.utcMs),
      offsetMinutes: chosen.offset,
      dstAdjusted: false,
      adjustedLocal: formatLocalDateTime(chosen.wall)
    };
  }

  // Nonexistent
  if (firstAfter) {
    // firstAfter corresponds to first valid wall after gap start (e.g., 03:00 for 02:30 gap)
    // Our spec says map to first valid instant after gap (03:00). That's what firstAfter gives.
    // Note firstAfter.wallMs is e.g., 03:00 wallMs, not 02:30
    return {
      utc: new Date(firstAfter.utcMs),
      offsetMinutes: firstAfter.offset,
      dstAdjusted: true,
      adjustedLocal: formatLocalDateTime(firstAfter.wall)
    };
  }

  throw new Error(`UNRESOLVABLE_LOCAL_TIME: ${localYmd}T${localTime} in ${tz}`);
}

// Helpers for calendar day enumeration
function addDaysLocal(localDateStr: string, days: number): string {
  // localDateStr "YYYY-MM-DD" interpret as UTC midnight for calendar arithmetic
  const [y, m, d] = localDateStr.split('-').map(Number);
  const ms = Date.UTC(y, m - 1, d);
  const nd = new Date(ms + days * 24 * 3600 * 1000);
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${String(nd.getUTCFullYear()).padStart(4, '0')}-${pad(nd.getUTCMonth() + 1)}-${pad(nd.getUTCDate())}`;
}

function diffDaysLocal(a: string, b: string): number {
  const [ya, ma, da] = a.split('-').map(Number);
  const [yb, mb, db] = b.split('-').map(Number);
  const maMs = Date.UTC(ya, ma - 1, da);
  const mbMs = Date.UTC(yb, mb - 1, db);
  return Math.round((maMs - mbMs) / (24 * 3600 * 1000));
}

function enumerateLocalDates(startInclusive: string, endExclusive: string): string[] {
  const out: string[] = [];
  let cur = startInclusive;
  // safety limit 366 days
  let iter = 0;
  while (cur < endExclusive && iter < 1000) {
    out.push(cur);
    cur = addDaysLocal(cur, 1);
    iter++;
  }
  return out;
}

// ---------------------------------------------------------------------------
// Business key
// ---------------------------------------------------------------------------
function makeBusinessKey(scheduleVersionId: string, utcIso: string): string {
  // stable key per spec: unique over schedule version and nominal schedule instant
  // Use second precision
  return `${scheduleVersionId}:${utcIso}`;
}

// ---------------------------------------------------------------------------
// Pause / medication start-end filters
// ---------------------------------------------------------------------------
function isPaused(utc: Date, pauses: MedicationPausePeriod[] | undefined): boolean {
  if (!pauses || pauses.length === 0) return false;
  const t = utc.getTime();
  for (const p of pauses) {
    const s = parseIsoOrThrow(p.starts_at, 'pause.starts_at').getTime();
    const e = parseIsoOrThrow(p.ends_at, 'pause.ends_at').getTime();
    if (t >= s && t < e) return true;
  }
  return false;
}

function isWithinMedicationDates(
  utc: Date,
  tz: string,
  med: { start_date?: string | null; end_date?: string | null } | undefined
): boolean {
  if (!med) return true;
  const localDate = dateToLocalDateString(utc, tz);
  if (med.start_date) {
    const sd = med.start_date.trim();
    if (DATE_RE.test(sd)) {
      if (localDate < sd) return false;
    } else {
      // ISO datetime
      const sdDate = parseIsoOrThrow(sd, 'medication.start_date');
      if (utc.getTime() < sdDate.getTime()) return false;
    }
  }
  if (med.end_date) {
    const ed = med.end_date.trim();
    if (DATE_RE.test(ed)) {
      if (localDate > ed) return false;
    } else {
      const edDate = parseIsoOrThrow(ed, 'medication.end_date');
      if (utc.getTime() > edDate.getTime()) return false;
    }
  }
  return true;
}

function isWithinEffectiveWindow(utc: Date, version: MedicationScheduleVersion): boolean {
  const effFrom = parseIsoOrThrow(version.effective_from, 'effective_from');
  if (utc.getTime() < effFrom.getTime()) return false;
  if (version.effective_until) {
    const effUntil = parseIsoOrThrow(version.effective_until, 'effective_until');
    if (utc.getTime() >= effUntil.getTime()) return false;
  }
  return true;
}

function isWithinRange(utc: Date, from: Date, to: Date): boolean {
  const t = utc.getTime();
  return t >= from.getTime() && t < to.getTime();
}

// ---------------------------------------------------------------------------
// Core generation per type
// ---------------------------------------------------------------------------

export function generateOccurrences(input: GenerateOccurrencesInput): OccurrenceCandidate[] {
  const { scheduleVersion, rangeFrom, rangeTo, timezone, pausePeriods, medicationStartEnd, lastTakenAt, includePrnPreview } = input;

  // Validate timezone
  const tz = timezone ?? scheduleVersion.timezone;
  assertIanaTimezone(tz);
  assertIanaTimezone(scheduleVersion.timezone);
  // Do not allow bare offsets even if passed as timezone
  // Use schedule's timezone as canonical for occurrences; but if override provided, use override for generation
  const effectiveTz = tz;

  const fromDate = parseIsoOrThrow(rangeFrom, 'rangeFrom');
  const toDate = parseIsoOrThrow(rangeTo, 'rangeTo');
  if (fromDate.getTime() >= toDate.getTime()) throw new Error('INVALID_RANGE: rangeFrom must be < rangeTo');

  // PRN handling
  if (scheduleVersion.schedule_type === 'prn') {
    if (!includePrnPreview) return [];
    // preview: still return empty per spec, preview is empty unless explicitly asked? We'll return empty as well
    return [];
  }

  // Validate schedule timing mode vs type? No strict.

  const out: OccurrenceCandidate[] = [];

  // Dispatch by type
  if (scheduleVersion.schedule_type === 'fixed_times') {
    const fts = scheduleVersion.fixed_times ?? [];
    if (fts.length === 0) return [];

    // Determine local date range to enumerate
    // Expand by 1 day on each side to capture offset edge then filter by utc range
    const startLocal = dateToLocalDateString(fromDate, effectiveTz);
    const endLocal = dateToLocalDateString(toDate, effectiveTz);
    const enumStart = addDaysLocal(startLocal, -1);
    const enumEnd = addDaysLocal(endLocal, 2); // exclusive

    const localDates = enumerateLocalDates(enumStart, enumEnd);

    for (const ld of localDates) {
      // weekday for filtering
      const [yy, mm, dd] = ld.split('-').map(Number);
      const wd = new Date(Date.UTC(yy, mm - 1, dd)).getUTCDay(); // 0 Sun
      for (const ft of fts) {
        if (ft.days_of_week && ft.days_of_week.length > 0) {
          if (!ft.days_of_week.includes(wd)) continue;
        }
        const policy = ft.ambiguous_time_policy ?? 'earlier';
        let resolved: ReturnType<typeof resolveLocalToUtc>;
        try {
          resolved = resolveLocalToUtc(ld, ft.local_time, effectiveTz, policy);
        } catch {
          // invalid local_time etc. skip
          continue;
        }
        const utc = resolved.utc;
        if (!isWithinRange(utc, fromDate, toDate)) continue;
        if (!isWithinEffectiveWindow(utc, scheduleVersion)) continue;
        if (!isWithinMedicationDates(utc, effectiveTz, medicationStartEnd)) continue;
        if (isPaused(utc, pausePeriods)) continue;

        const utcIso = utc.toISOString();
        out.push({
          business_key: makeBusinessKey(scheduleVersion.id, utcIso),
          scheduled_at_utc: utcIso,
          scheduled_local_datetime: resolved.adjustedLocal,
          timezone: effectiveTz,
          utc_offset_minutes: resolved.offsetMinutes,
          dst_adjusted: resolved.dstAdjusted,
          dose_value: ft.dose_quantity_value,
          dose_unit: ft.dose_quantity_unit,
          schedule_version_id: scheduleVersion.id
        });
      }
    }
  } else if (scheduleVersion.schedule_type === 'elapsed_interval') {
    const iv = scheduleVersion.interval;
    if (!iv) throw new Error('MISSING_INTERVAL: schedule_type elapsed_interval requires interval');
    if (iv.interval_minutes <= 0) throw new Error('INVALID_INTERVAL: interval_minutes must be >0');

    let anchorMs: number;
    if (iv.anchor_policy === 'last_taken' && lastTakenAt) {
      anchorMs = parseIsoOrThrow(lastTakenAt, 'lastTakenAt').getTime();
    } else {
      anchorMs = parseIsoOrThrow(iv.anchor_at, 'interval.anchor_at').getTime();
    }

    const intervalMs = iv.interval_minutes * 60 * 1000;

    // Find first n such that anchor + n*interval >= from and >= effective_from
    const minMs = Math.max(fromDate.getTime(), parseIsoOrThrow(scheduleVersion.effective_from, 'effective_from').getTime());
    let n: number;
    if (anchorMs >= minMs) {
      n = 0;
      // but maybe we need earlier n that still >= minMs? If anchor past, n=0 is first
    } else {
      n = Math.ceil((minMs - anchorMs) / intervalMs);
    }
    // Safety cap 10000 occurrences
    for (let i = 0; i < 10000; i++) {
      const utcMs = anchorMs + (n + i) * intervalMs;
      const utc = new Date(utcMs);
      if (utc.getTime() >= toDate.getTime()) break;
      if (scheduleVersion.effective_until) {
        const eu = parseIsoOrThrow(scheduleVersion.effective_until, 'effective_until').getTime();
        if (utc.getTime() >= eu) break;
      }
      if (!isWithinMedicationDates(utc, effectiveTz, medicationStartEnd)) continue;
      if (isPaused(utc, pausePeriods)) continue;
      // For elapsed, local datetime derived from utc
      const wall = getWallParts(utc, effectiveTz);
      const offset = getOffsetMinutes(utc, effectiveTz);
      const localDt = formatLocalDateTime(wall);
      const utcIso = utc.toISOString();
      out.push({
        business_key: makeBusinessKey(scheduleVersion.id, utcIso),
        scheduled_at_utc: utcIso,
        scheduled_local_datetime: localDt,
        timezone: effectiveTz,
        utc_offset_minutes: offset,
        dst_adjusted: false,
        dose_value: iv.dose_quantity_value,
        dose_unit: iv.dose_quantity_unit,
        schedule_version_id: scheduleVersion.id
      });
    }
  } else if (scheduleVersion.schedule_type === 'cyclic') {
    const cy = scheduleVersion.cycle;
    if (!cy) throw new Error('MISSING_CYCLE: cyclic requires cycle');
    if (cy.on_days <= 0) throw new Error('INVALID_CYCLE: on_days must be >0');
    const cycleLen = cy.on_days + (cy.off_days ?? 0);
    const localTime = cy.local_time ?? '08:00';
    parseLocalTime(localTime); // validate
    const policy = cy.ambiguous_time_policy ?? 'earlier';

    const startLocal = dateToLocalDateString(fromDate, effectiveTz);
    const endLocal = dateToLocalDateString(toDate, effectiveTz);
    const enumStart = addDaysLocal(startLocal, -1);
    const enumEnd = addDaysLocal(endLocal, 2);
    const localDates = enumerateLocalDates(enumStart, enumEnd);

    for (const ld of localDates) {
      const diff = diffDaysLocal(ld, cy.cycle_anchor_date);
      if (diff < 0) continue;
      const pos = diff % cycleLen;
      if (pos >= cy.on_days) continue; // off
      // Optionally days_of_week filtering? Not needed for cyclic but could support?
      const resolved = resolveLocalToUtc(ld, localTime, effectiveTz, policy);
      const utc = resolved.utc;
      if (!isWithinRange(utc, fromDate, toDate)) continue;
      if (!isWithinEffectiveWindow(utc, scheduleVersion)) continue;
      if (!isWithinMedicationDates(utc, effectiveTz, medicationStartEnd)) continue;
      if (isPaused(utc, pausePeriods)) continue;
      const utcIso = utc.toISOString();
      out.push({
        business_key: makeBusinessKey(scheduleVersion.id, utcIso),
        scheduled_at_utc: utcIso,
        scheduled_local_datetime: resolved.adjustedLocal,
        timezone: effectiveTz,
        utc_offset_minutes: resolved.offsetMinutes,
        dst_adjusted: resolved.dstAdjusted,
        dose_value: cy.dose_quantity_value,
        dose_unit: cy.dose_quantity_unit,
        schedule_version_id: scheduleVersion.id
      });
    }
  } else if (scheduleVersion.schedule_type === 'taper') {
    const steps = [...(scheduleVersion.taper_steps ?? [])].sort((a, b) => a.step_order - b.step_order);
    // Validate sequential non-overlapping: steps must not overlap and be sorted
    for (let i = 0; i < steps.length; i++) {
      const s = steps[i];
      if (!DATE_RE.test(s.starts_on) || !DATE_RE.test(s.ends_on)) throw new Error(`INVALID_TAPER_DATE: ${s.starts_on} - ${s.ends_on}`);
      if (s.starts_on > s.ends_on) throw new Error(`INVALID_TAPER_RANGE: starts_on > ends_on ${s.id}`);
      parseLocalTime(s.local_time ?? (s.fixed_times?.[0]?.local_time ?? '08:00'));
      if (i > 0) {
        const prev = steps[i - 1];
        if (s.starts_on <= prev.ends_on) {
          throw new Error(`TAPER_OVERLAP: step ${prev.id} (${prev.starts_on}-${prev.ends_on}) overlaps ${s.id} (${s.starts_on}-${s.ends_on})`);
        }
      }
    }

    for (const step of steps) {
      const localDates = enumerateLocalDates(step.starts_on, addDaysLocal(step.ends_on, 1));
      // Determine times for this step
      let times: { local_time: string; dose_value: string; dose_unit: string; policy: 'earlier' | 'later'; days_of_week?: number[] | null }[] = [];
      if (step.fixed_times && step.fixed_times.length > 0) {
        times = step.fixed_times.map(ft => ({
          local_time: ft.local_time,
          dose_value: ft.dose_quantity_value,
          dose_unit: ft.dose_quantity_unit,
          policy: (ft.ambiguous_time_policy ?? step.ambiguous_time_policy ?? 'earlier') as 'earlier' | 'later',
          days_of_week: ft.days_of_week ?? step.days_of_week ?? null
        }));
      } else {
        const lt = step.local_time ?? '08:00';
        times = [
          {
            local_time: lt,
            dose_value: step.dose_quantity_value,
            dose_unit: step.dose_quantity_unit,
            policy: (step.ambiguous_time_policy ?? 'earlier') as 'earlier' | 'later',
            days_of_week: step.days_of_week ?? null
          }
        ];
      }

      for (const ld of localDates) {
        const [yy, mm, dd] = ld.split('-').map(Number);
        const wd = new Date(Date.UTC(yy, mm - 1, dd)).getUTCDay();
        for (const t of times) {
          if (t.days_of_week && t.days_of_week.length > 0 && !t.days_of_week.includes(wd)) continue;
          const resolved = resolveLocalToUtc(ld, t.local_time, effectiveTz, t.policy);
          const utc = resolved.utc;
          if (!isWithinRange(utc, fromDate, toDate)) continue;
          if (!isWithinEffectiveWindow(utc, scheduleVersion)) continue;
          if (!isWithinMedicationDates(utc, effectiveTz, medicationStartEnd)) continue;
          if (isPaused(utc, pausePeriods)) continue;
          const utcIso = utc.toISOString();
          out.push({
            business_key: makeBusinessKey(scheduleVersion.id, utcIso),
            scheduled_at_utc: utcIso,
            scheduled_local_datetime: resolved.adjustedLocal,
            timezone: effectiveTz,
            utc_offset_minutes: resolved.offsetMinutes,
            dst_adjusted: resolved.dstAdjusted,
            dose_value: t.dose_value,
            dose_unit: t.dose_unit,
            schedule_version_id: scheduleVersion.id
          });
        }
      }
    }
  } else {
    throw new Error(`UNSUPPORTED_SCHEDULE_TYPE: ${scheduleVersion.schedule_type}`);
  }

  // deterministic sort by utc, then business_key
  out.sort((a, b) => {
    if (a.scheduled_at_utc < b.scheduled_at_utc) return -1;
    if (a.scheduled_at_utc > b.scheduled_at_utc) return 1;
    return a.business_key.localeCompare(b.business_key);
  });

  return out;
}

// Single calculation entry point alias for preview/persistence/reporting (same function)
export const previewOccurrences = generateOccurrences;
export const calculateOccurrences = generateOccurrences;
