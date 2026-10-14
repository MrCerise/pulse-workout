# Workout JSON / AI import (v1.6.4)

Open **Workouts → + → Import JSON / AI workout**, or the same entry under
**Settings → Create workouts**. This is separate from the legacy text parser and full backup restore.

1. Optionally describe the workout you want (time, experience, equipment, goals).
2. Tap **Copy AI prompt + JSON format**. Paste it into ChatGPT, Claude, Gemini or any other
   text-generating AI. PULSE does not call a provider, manage API keys or send data automatically.
   The prompt contains only your request and public format instructions, not your workout history.
   Avoid personal health information; the provider's privacy policy applies to whatever you share.
3. Paste the response into **Workout JSON**, or choose a UTF-8 `.json`/text file using Android's file
   picker. A single outer `json` Markdown fence is accepted; surrounding chat prose is not.
   **Use sample JSON** fills a runnable example without an AI account.
4. **Validate & preview** shows each workout's category, tags, equipment, expanded intervals,
   coaching notes and timed duration. Use **Show every interval** for longer workouts.
   Manual steps are explicitly marked: their time is not included in the timed duration.
5. **Import N workout(s)** saves the complete batch in one database transaction. Existing workouts
   are never overwritten. **View saved workouts** opens the new workout or the library.

Nothing is saved just by choosing a file or validating. Editing the JSON clears the preview.
Invalid files fail with field paths where possible. A save failure is retryable; repeated taps while
saving are ignored. Inputs and preview survive rotation (not OS process death). There is no automatic
execution of imported workouts. Review AI suggestions for suitability before starting them.

## Public format

- [Machine-readable JSON Schema](workout-import.schema.json), draft 2020-12.
- [Runnable example](examples/beginner-workout.json).
- Root: `format: "pulse.workouts"`, `version: 1`, `workouts: [...]` (1–50 workouts).
- Workout: required `name` and `steps`; optional `description`, `type`, `equipment`, `tags`.
- An interval has `name`, `kind`, and integer `seconds` (not milliseconds).
- A repeat group has integer `repeat` and `steps`, plus an optional `name`.
- Optional interval coaching fields: `notes`, `speechText`, `reps`, `equipment`, `sideLabel`.
- For a manual interval use `manual: true`; omit `seconds` or set it to `0`.
- A type is a library category, **not a workout generator**. All EMOM, Tabata, etc. timings must
  appear explicitly in `steps`. The timer executes those steps in order.
- Every repetition includes every child, including a trailing rest. Omit a last rest by writing
  the final round separately. Nested groups are supported; ladders/shuffle/media are not part of v1.
- Unknown fields, unsupported versions, `null`, numeric strings and fractional seconds are rejected,
  never silently defaulted. Missing optional fields use their documented defaults. Text is trimmed;
  blank names/tags/equipment are rejected. Repeated tags within a workout are deduplicated.
- IDs, folder links, local audio/media URIs, favourites and history are never accepted from a file.
  New workout/node/interval IDs are generated on every import. Importing twice makes two copies.
- This format is not the internal `WorkoutPlan` JSON or a `pulse.backup` file. Use Settings → Backup
  for existing backup files. v1 does not export arbitrary saved workouts into this exchange format.

### Limits

256 KiB UTF-8 (including fences), 50 workouts, 8 step levels (root steps are level 1), 1–1,000
repeats per group, 10,000 expanded intervals **across the batch**, and 24 hours timed content per
workout. The symbolic count/duration check runs **before** timeline expansion, so nested repeats
cannot create an unbounded allocation. Individual timed intervals allow 1–86,400 whole seconds.

Names: 1–120 characters; description/notes: up to 2,000; speech: up to 500; equipment: 1–200;
side labels: 1–40; tags: up to 20 strings of 1–40 characters; reps: 1–10,000. Optional type defaults
to `CUSTOM_SEQUENCE`. The schema covers the structure; the app additionally enforces byte size,
trimmed text, nesting depth, expanded count and duration limits. Parser diagnostics are in English;
screen controls and guidance are translated into English and French.

## Verification

Automated: `WorkoutImportTest` covers sample timing, nested rounds, manual intervals, metadata,
fresh identities, AI fences, malformed documents, field errors, UTF-8 and size limits, batch limits,
and pre-expansion repeat limits. `JsonImportViewModelTest` covers stale previews, serialized actions,
double-save prevention, file errors and retry after storage failure.

Device acceptance checklist (requires an emulator/phone):

- Open both library and Settings entry points in English/French, portrait/landscape, light/dark.
- Copy a customized prompt; paste in an AI app and bring JSON back. Verify no automatic networking.
- Choose the example through the file picker; confirm 10 intervals and 8:00 before importing.
- Cancel the file picker, revoke a document's access, select malformed/oversized files: no crash/save.
- Import two workouts, reopen library, edit/run one, restart the app: plans, tags and cues persist.
- Reimport the same file: original favourites/history/edits stay intact; new copies appear.
- Rotate during preview/save; tap import twice: one save operation, success state remains visible.
- Import a manual step and verify Next advances it. Inspect all intervals in a long nested workout.
- Trigger a storage failure during a multi-workout save: the outer Room transaction must roll back
  workouts and tag links together (transactional guarantee; device integration check outstanding).
