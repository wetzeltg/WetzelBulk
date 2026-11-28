# WetzelBulk
A high-performance, precision-engineered Android workout tracking application built natively using Jetpack Compose and the Room SQLite database architecture. Engineered for elite-level tracking efficiency, hyper-scannable metrics, and seamless gym-floor execution.

---

## 🚀 Core Architecture & Features (v2.0)

### 1. Body Weight (removed in v2.0)
Body weight is no longer logged in the app; it's tracked in `food-log/weight-calories.csv`. The `bodyWeight` column and all past values remain in the database and `workouts.json` (new sessions store `0.0` = not recorded).

### 2. Tailored 4-Day Routines with Integrated Supersets (Nov 2026 program)
Engineered around specific compound movements, targeted rep ranges, and rest intervals. In a superset the rest timer runs after each completed pair (A then B):
* **PUSH DAY:**
  1. Incline Press (4 × 8–12, 120s rest)
  2. Flat Press (3 × 8–12, 120s rest)
  3. Superset: DB Fly (3 × 10–15) / Rope Pushdown (3 × 8–15), 75s rest
  4. Superset: Lateral Raise (3 × 12–20) / Skull Crusher (3 × 10–15), 75s rest
* **LEGS DAY:**
  1. Leg Press (4 × 8–12, 120s rest)
  2. Romanian Dead Lift (`RDL` rows) (3 × 8–12, 120s rest)
  3. Superset: Calf Raise (3 × 12–20) / Lateral Raise (3 × 12–20), 90s rest
  4. Superset: Crunch (2 × 10–15) / Back Extension (2 × 10–15), 60s rest
* **PULL DAY:**
  1. Lat Pulldown (4 × 8–12, 120s rest)
  2. Seated Cable Row - Neutral V-Grip (4 × 8–12, 120s rest)
  3. Superset: Straight-Arm Pulldown (3 × 10–15) / Incline DB Curl (3 × 8–12), 75s rest
  4. Superset: Face Pull (3 × 12–20) / Hammer Curl (3 × 8–12), 75s rest
* **CARDIO DAY:**
  * Treadmill Cardio session tracking with target speed, incline, and calorie targets.

### 3. Exercise-Specific Rest Timers (Pure Seconds)
* **Dynamic Interval Scaling:** Rest timers are tuned per exercise block (e.g., `120s` for heavy compounds, `75s` or `60s` for supersets and accessories, `0s` mid-superset).
* **Static Card Rest Badges:** Each card header displays its configured rest target clearly (e.g., `⏱ 120s`), eliminating redundant on-card ticking timers.
* **HUD Countdown & Alerting:** The persistent top bar HUD exclusively displays the live countdown in seconds with persistent background timer execution and haptic vibration signaling.

### 4. Continuous Rep Target Guidance (Color Cues)
* **Rep Ceiling Indicators:** Target rep ranges (e.g. `(8–12)`, `(10–15)`, `(12–20)`) are displayed inline right beside each exercise label.
* **Persistent Visual Feedback:**
  * 🔴 **Bold Red:** Displayed when reps are below the minimum rep target (`< minReps`), serving as a persistent cue that the weight is too heavy / drop weight.
  * 🟢 **Bold Green:** Displayed when reps reach or exceed the maximum target (`≥ maxReps`), serving as a cue to increase weight.
  * **Neutral:** Displayed when reps are within the target range.

### 5. Smart Exercise Aliases & Notes Carry-Over Engine
* **Automated Baseline Pre-Population:** Smart fuzzy alias matching (`isLabelMatch`) bridges previous exercise labels to new ones (e.g. `Inc. Chest` / `Inc. DB` ⟷ `Inc. Press`, `Flat Chest` / `Chest Press` ⟷ `Flat Press`, `DB RDL` ⟷ `RDL`).
* **Fallback Weight Carry-Over:** If an exact set number (e.g., Set 4) did not exist in an older 3-set routine, it automatically inherits weight and reps from the most recent set of that exercise.
* **Machine Notch & Pin Settings Retention:** `getNotesAliases` automatically searches past superset notes for machine notch settings, seat positions, and form cues, carrying them forward into the new blocks.

### 6. Finish Workout Safety Modal
* When pressing **"Finish Workout & Save Baseline"**, a safety confirmation dialog displays a summary of completed sets, exercises, and recorded body weight, requiring explicit user confirmation before archiving the workout to history.

### 7. Time Travel Player Engine
* **Chronological Tape-Deck Navigation:** Shift the entire application into read-only `REPLAY` mode using `⏮ Older` and `Newer ⏭` controls.
* **Decoupled Historical Payloads:** Reconstructs exact past workouts from SQLite `set_logs`, guaranteeing that updating workout templates never distorts or loses historical workout records.

### 8. High-Density, High-Readability UI Layout
* **Enlarged Typography:** Clean, readable font sizes across exercise labels, rep targets, weight/rep values, and minus/plus buttons.
* **Tight Row Spacing:** Minimized vertical margins and card gaps (`0.dp` row vertical padding, `2.dp` card spacing) maximizing data density so workouts fit comfortably on the device screen.

### 9. Custom Adaptive App Icon
* **Athletic Clasp "W" Emblem:** A bold, vibrant royal blue letter 'W' formed by two muscular flexing arms locked in a center grip on a crisp white background.
* **Modern Adaptive Icon Architecture:** Full implementation of Android's Adaptive Icon API (`mipmap-anydpi-v26`) featuring isolated vector background (`ic_launcher_background.xml`) and centered foreground drawables (`ic_launcher_foreground.png`).
* **Pixel-Safe 66dp Masking:** The emblem is scaled within the 66dp safe-zone boundary to prevent any clipping under Pixel Launcher's circular, squircle, or teardrop icon masks.
* **Legacy Density Fallbacks:** Full mipmap asset suite (`mdpi`, `hdpi`, `xhdpi`, `xxhdpi`, `xxxhdpi`) for universal launcher compatibility.

---

## 🛠️ Tech Stack & Dependencies

* **Language:** Kotlin
* **UI Framework:** Jetpack Compose (Material 3 Ecosystem)
* **Local Storage:** Room Persistence Library (SQLite ORM) with explicit schema migrations
* **Asynchronous Engine:** Kotlin Coroutines & Thread-Isolated Dispatchers
* **Service Architecture:** Core Android Background Service Layer with Foreground Notifications & Vibrator Manager

---

## 🗄️ Database Architecture Schema

* **`workout_sessions`**: Logs unique tracking IDs, ISO date strings, day names, start/end timestamps, and `bodyWeight` (Double; historical only since v2.0, `0.0` = not recorded).
* **`set_logs`**: Captures session-relational data payloads mapping exercise block titles, labels, set numbers, weight achieved, reps achieved, and (since v2.0, schema version 3) the intended `restSeconds` (`-1` = logged before rest was recorded).
* **`exercise_notes`**: High-availability text database block mapping machine notch settings, cues, and hyperlinked media assets directly onto exercise titles.

---

## 📝 Proposed Changes (Backlog — not yet approved)
Ideas logged for review. Nothing here is implemented until we decide which items to build.

> **Data protection (applies to every item):** the months of history in this repo — `data/` (workout SQLite + `workouts.json`) and `food-log/` (food logs, `weight-calories.csv`) — must never be deleted or rewritten. Changes are additive only. On-phone app data is expendable.

### P3. Personal Trainer Notes from Claude (still in backlog)
A Claude chat or Claude Agent analyzes results and leaves a note for a specific upcoming workout (e.g. "Move the incline press up one plate"); the app shows it.
- **Source of truth:** a file in this GitHub repo that Claude writes to and the app reads (e.g. `coach/notes.json`). Append-only: Claude adds new notes and never edits or deletes old ones, so the full note history is kept.
- **Targeting by workout type, never by date:** each note names one of `PUSH`, `LEGS`, `PULL`, `CARDIO` and gets a timestamp from when Claude wrote it.
- **Notes are never dismissed.** The most recent note for a workout type stays the "current" note until Claude writes a newer one for that type.
- **Trainer icon (always on screen):**
  - **New-note state:** when there's a note for today's workout type that I haven't opened yet, the icon looks different (attention state) so I notice it.
  - **First tap:** shows the note and the icon goes back to its normal state for good (until another new note arrives for that type). "Seen" is tracked on the phone.
  - **Later taps:** always show the most recent note for that workout type.
  - **No new note:** the icon stays normal, and tapping it still shows the most recent note for that workout type.
- **History (`⏮ Older`):** the icon shows the note of record for that past workout: the latest note for that workout type that existed when the workout was done. The note ID should be saved with each finished session so this stays exact.
- **Fetching:** the app checks GitHub for new notes when it opens (signal at the gym is reliable). If offline, it uses the last notes it downloaded.
- Avatar: an illustrated female personal-trainer character (not a real person's photo).

---

## ✅ Completed in v2.0
1. **P1 – Nov 2026 Workout Program:** New Push/Legs/Pull supersets (see Core Features §2). Exercise labels unchanged. Re-paired supersets got a one-time **copy** of their old blocks' notes (old notes kept for history). Pre-fill is now per day + label, so Legs Day `Lat. Raise` never inherits the Push Day weight. One-time starting loads for the first session of each day on the new program (Push: Flat Press 50, DB Fly 35 · Legs: Leg Press 18, Lat. Raise 20 · Pull: Lat Pull 15, Pulldown 6, Face Pull 6, Hammer 35); used up when that day is finished. Past workouts are untouched.
2. **P2 – Body Weight Removed from the App:** Weight bar, entry dialog and Finish-summary line removed. All historical `bodyWeight` data kept.
3. **P4 – History Replay Rest Times:** Each logged set now stores its intended rest (`set_logs.restSeconds`, DB migration 2→3, existing rows untouched). Replay shows recorded rest; for sets logged before v2.0 it uses the v1.x routine's rest per block (Sept–Nov 2026), and shows no badge for older (May–Sept) block titles whose intended rest isn't known. `workouts.json` sets now include `restSeconds` when recorded.

---

## ✅ Completed in v1.9
1. **Lat Pulldown vs. Straight-Arm Pulldown Disambiguation:** Hardened `getCanonicalExercise()` and `isLabelMatch()` so that vertical Lat Pulldowns (`Lat Pull`) and isolation Straight-Arm Pulldowns (`Pulldown`) resolve to distinct canonical exercise families (`LAT_PULLDOWN` vs `STRAIGHT_ARM_PULLDOWN`). Prevents reverse-chronological set matching from mistakenly populating compound Lat Pulldowns with accessory Straight-Arm Pulldown weights. Added comprehensive unit tests in `ExerciseMatchingTest.kt`.

---

## ✅ Completed in v1.8
1. **Rep Range Layout & Truncation Fix:** Shortened exercise labels (`Skull Crush`, `Hammer`) and integrated label + rep target into a unified annotated text layout with widened column weight (`3.4f`). Added `getExerciseRepRange()` fallback engine so rep targets (`[10–15]`, `[8–12]`) are consistently displayed across both live workouts and historical replay sessions without clipping.
2. **Live Workout Anti-Corruption & In-Memory Snapshot Engine:** Completely insulated live workout sessions against history navigation corruption. Created `liveBlocksBackup` state capture that deep-copies and restores the live workout when browsing historical sessions via `⏮ Older` and `Newer ⏭`.
3. **Stale Completion Preferences Cleansing:** Purges all lingering `set_*_completed` keys from storage upon finishing a workout session, and guards template restoration against inactive sessions so sets can never be mistakenly checked as complete upon returning to live mode.

---

## ✅ Completed in v1.6 & v1.7

1. **Database Sync to Git / Cloud (v1.6):** Export and sync the entire SQLite database (`wetzel_bulk_db.sqlite`) and structured JSON (`workouts.json`) to GitHub on workout finish.
2. **Skull Crushers Rep Range (v1.7):** Rep target set to `10–15`.
3. **Leg Day Superset Reordered (v1.7):** Final superset reordered to perform Crunches first (0s rest) and Back Extensions second (60s rest).
4. **Hammer Curl Rep Range (v1.7):** Target rep range set to `8–12`.
5. **Timer Streamlining (v1.7):** Redundant on-card countdown timers removed; card badges display static target rest interval, and the top-right HUD timer exclusively manages active countdown.