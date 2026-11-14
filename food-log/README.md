# Food Log

Daily food log for Todd. Updated throughout the day as meals are logged.

## What's here

- **`log/YYYY-MM-DD.md`** — the raw inputs: every item eaten that day, described as reported, with per-item calories / protein / carbs / fat / fiber. A `~` prefix means the number is an estimate (photo estimate, recipe estimate, or a placeholder); exact numbers come from nutrition labels or Todd's own figures. `f` = fat (grams).
- **`weight-calories.csv`** — one row per day: date, morning weight (lbs), and that day's totals — calories, protein_g, carbs_g, fat_g, fiber_g — recomputed from the daily logs. A blank macro row means the day is still in progress (or predates the food logs, Sep 7–11). This is the single summary file for correlating intake vs weight.

## Notes

- Only food confirmed eaten is logged — never planned or hypothetical food.
- Zero-calorie drinks are not logged.
- Fiber tracking started 2026-09-17; values before that are rough estimates.
- Daily calorie target is 2,850 / 170g protein (for reference only — logging is the goal, not hitting the target).
