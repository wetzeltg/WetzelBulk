import sqlite3
import pandas as pd
import matplotlib.pyplot as plt
import numpy as np
from datetime import datetime
import sys
import os

def analyze_exercise(db_path, exercise_label):
    # Connect to the database
    if not os.path.exists(db_path):
        print(f"Error: Database file not found at {db_path}")
        return

    try:
        conn = sqlite3.connect(db_path)
    except sqlite3.Error as e:
        print(f"Error connecting to database: {e}")
        return

    # Query to get session dates and performance metrics
    query = """
    SELECT
        s.dateString,
        l.weightAchieved,
        l.repsAchieved
    FROM workout_sessions s
    JOIN set_logs l ON s.sessionId = l.sessionId
    WHERE l.exerciseLabel = ?
    ORDER BY s.dateString ASC
    """

    df = pd.read_sql_query(query, conn, params=(exercise_label,))
    conn.close()

    if df.empty:
        print(f"No data found for exercise: {exercise_label}")
        return

    # 1. Apply Weight Conversion Logic for Bench Press
    # Logic: < 20 = Plates (x10), >= 20 = Dumbbells (x2 for both arms)
    is_bench = "Chest" in exercise_label or "Press" in exercise_label

    def calculate_set_load(row):
        w = row['weightAchieved']
        r = row['repsAchieved']
        if is_bench:
            if w < 20:
                # Plates: 10lbs each, already represents total machine load
                return w * 10 * r, "Plates"
            else:
                # Dumbbells: per arm, multiply by 2
                return w * 2 * r, "Dumbbells"
        else:
            # For non-bench, assume raw weight is accurate (or standard)
            # We still need to distinguish for visual markers
            set_type = "Plates" if w < 20 else "Dumbbells"
            return w * r, set_type

    # Apply calculation
    results = df.apply(calculate_set_load, axis=1, result_type='expand')
    df['volume_load'] = results[0]
    df['set_type'] = results[1]

    # Group by date to get total volume load per session
    # We also keep track of the dominant type for plotting markers
    daily_stats = df.groupby('dateString').agg({
        'volume_load': 'sum',
        'set_type': lambda x: x.mode()[0] if not x.empty else "Plates"
    }).reset_index()

    # Convert date strings to datetime objects for plotting
    daily_stats['date'] = pd.to_datetime(daily_stats['dateString'])
    daily_stats = daily_stats.sort_values('date')

    # Prepare for regression
    dates_numeric = (daily_stats['date'] - daily_stats['date'].min()).dt.days.values
    volumes = daily_stats['volume_load'].values

    if len(dates_numeric) > 1:
        z = np.polyfit(dates_numeric, volumes, 1)
        p = np.poly1d(z)
        regression_line = p(dates_numeric)
    else:
        regression_line = None

    # Plotting
    plt.figure(figsize=(12, 6))

    # Separate data for different markers
    plate_data = daily_stats[daily_stats['set_type'] == "Plates"]
    db_data = daily_stats[daily_stats['set_type'] == "Dumbbells"]

    # 1. Scatter plots
    # Dumbbells = Solid
    plt.scatter(db_data['date'], db_data['volume_load'],
                color='royalblue', marker='o', label='Dumbbells (Solid)', zorder=3)

    # Plates = White-filled (hollow)
    plt.scatter(plate_data['date'], plate_data['volume_load'],
                color='royalblue', marker='o', facecolors='none', edgecolors='royalblue',
                linewidth=1.5, label='Plates (Hollow)', zorder=3)

    # 2. Regression line
    if regression_line is not None:
        plt.plot(daily_stats['date'], regression_line,
                 color='royalblue', alpha=0.3, linewidth=2, label='Trend Line', zorder=2)

    title_suffix = " (Corrected Volume)" if is_bench else ""
    plt.title(f'Progressive Overload Trend: {exercise_label}{title_suffix}', fontsize=14, fontweight='bold')
    plt.xlabel('Date', fontsize=12)
    plt.ylabel('Total Corrected Volume Load (Lbs)', fontsize=12)
    plt.grid(True, linestyle='--', alpha=0.6)
    plt.xticks(rotation=45)
    plt.legend()
    plt.tight_layout()

    # Save the plot
    filename = f"overload_{exercise_label.replace(' ', '_').lower()}.png"
    plt.savefig(filename)
    print(f"Analysis complete. Plot saved as {filename}")
    plt.show()

if __name__ == "__main__":
    # Look for database in the same directory as the script
    script_dir = os.path.dirname(os.path.abspath(__file__))
    db_file = os.path.join(script_dir, "wetzel_bulk_db.sqlite")

    target_exercise = "Inc. Chest" # Default as requested

    if len(sys.argv) > 1:
        target_exercise = sys.argv[1]

    print(f"Analyzing progressive overload for: {target_exercise}")
    analyze_exercise(db_file, target_exercise)
