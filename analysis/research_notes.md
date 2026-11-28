# Workout Data Analysis Research

This folder contains tools and data extracted from the WetzelBulk application for analyzing progressive overload performance.

## Files
- `analyze_overload.py`: Python script for generating volume load plots.
- `wetzel_bulk_db.sqlite`: Main database file containing workout history.
- `wetzel_bulk_db-shm` / `wetzel_bulk_db-wal`: SQLite journal files.

## Analysis Methodology: Volume Load
Volume load is used as the primary metric for progressive overload.

**Formula per Session:**
$$ \text{Total Volume Load} = \sum_{i=1}^{n} (\text{Weight}_i \times \text{Reps}_i) $$
Where $n$ is the number of sets performed for a specific exercise in that session.

### Example (Inclined Benchpress)
For a session with 4 sets of "Inc. Chest":
- Set 1: 11 plates × 15 reps = 165
- Set 2: 11 plates × 12 reps = 132
- Set 3: 10 plates × 15 reps = 150
- Set 4: 10 plates × 12 reps = 120
- **Total Volume Load** = 567

## Running the Analysis
The script requires Python 3 with `pandas`, `matplotlib`, and `numpy`.

```bash
# Navigate to this directory
cd analysis

# Run for default exercise (Inc. Chest)
python analyze_overload.py

# Run for a specific exercise
python analyze_overload.py "Lat. Raise"
```
