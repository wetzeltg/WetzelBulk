# Coach Notes (for Claude)

The WetzelBulk app shows notes from Todd's AI personal trainer. Notes live in
[`notes.json`](notes.json) on the `main` branch. The app downloads this file when it opens.

## How to add a note
1. Read the current `coach/notes.json` from `main`.
2. **Append** one object to the `notes` array. Never edit or delete existing notes: they are
   the history, and past workouts in the app point at them.
3. Commit to `main` with a message like `Coach note: PULL - raise Lat Pull to 16`.

```json
{
  "notes": [
    {
      "id": "2026-11-29-pull-1",
      "workout": "PULL",
      "createdAt": "2026-11-29T07:30:00-05:00",
      "text": "Lat Pull: all 4 sets hit 12 last time. Move up one plate to 16."
    }
  ]
}
```

| Field | Required | Rules |
|---|---|---|
| `id` | yes | Unique, never reused. Suggested: `YYYY-MM-DD-<workout>-<n>` in lowercase. |
| `workout` | yes | One of `PUSH`, `LEGS`, `PULL`, `CARDIO`. **Never a date.** The note applies to the next time that workout comes up in the rotation. |
| `createdAt` | yes | ISO-8601 timestamp with a timezone offset or `Z`, e.g. `2026-11-29T07:30:00-05:00`. |
| `text` | yes | Plain text. Newlines (`\n`) are fine. Keep it short enough to read between sets. |

## How the app uses notes
- The **current** note for a workout type is the one with the latest `createdAt`.
- When a note is new (not yet opened on the phone), the trainer icon changes to get attention.
  The first tap opens it and the icon goes back to normal. Later taps keep showing the newest note.
- A newer note for the same workout type replaces the old one as "current".
- When a workout is finished, the app records which note was current, so history (`⏮ Older`)
  shows the note of record for that day. Workouts finished before a note existed show the
  latest note for that workout type created before the workout ended.

## Data to analyze
Workout history: `data/workouts.json` (and `data/wetzel_bulk_db.sqlite`).
Food and body weight: `food-log/`. Sessions in `workouts.json` include `coachNoteId` when a
note was current at the time.
