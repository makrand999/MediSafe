# Schedules — §5.5, §5.6, §6.4, §14

## Types
- `fixed_times` — wall-clock `local_time` HH:mm per `days_of_week` optional `src/modules/schedules/validation.ts:1` `FixedTimeInput`.
- `elapsed_interval` — `anchor_at` + `interval_minutes` `anchor_policy fixed_anchor|last_taken` `src/modules/schedules/engine.ts:36`.
- `prn` — as-needed, no occurrences unless `includePrnPreview`.
- `cyclic` — `cycle_anchor_date` `on_days|off_days` `src/modules/schedules/engine.ts:45`.
- `taper` — `taper_steps` sequential `starts_on|ends_on` non-overlap validated `server/src/modules/schedules/validation.ts:1` + engine `TAPER_OVERLAP`.

## Versioning
- Never mutate in place once occurrences exist `src/modules/schedules/service.ts:118` `createSchedule`.
- New version `version_number+1`, old `effective_until = new effective_from` `src/modules/schedules/service.ts:133`.
- `supersedeSchedule` only allows superseding the active (`effective_until IS NULL`) latest version `src/modules/schedules/service.ts:286`.

## Time
- Store IANA `timezone` e.g. `America/New_York`, never bare `-05:00` `assertIanaTimezone` `src/modules/schedules/engine.ts:122`.
- Occurrence stores `scheduled_at_utc` `scheduled_local_datetime` `timezone` `utc_offset_minutes` `dst_adjusted` `server/src/db/schema.ts:480`.

## DST deterministic
- Spring-forward nonexistent `02:30` on `2024-03-10` → first valid `03:00` `dst_adjusted=true` `resolveLocalToUtc` `server/src/modules/schedules/engine.ts:227`.
- Fall-back ambiguous `01:30` on `2024-11-03` → `earlier` default else `later` `ambiguous_time_policy`.

## Occurrences
- Pure `generateOccurrences` `server/src/modules/schedules/engine.ts:397` same for preview/persistence/reporting.
- Rolling window `30d past → 60d future` `server/src/modules/schedules/service.ts:235` `persist 30d`
- Idempotent `unique(schedule_version_id,scheduled_at_utc)` `onConflictDoNothing` `server/src/modules/schedules/service.ts:320`.

## Preview
- `POST /schedules` returns `preview` 30 next via same calculation `previewForVersion` `server/src/modules/schedules/service.ts:238`.
