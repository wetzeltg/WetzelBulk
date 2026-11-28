import sqlite3
import pandas as pd
import os

def dump_to_csv(db_path, exercise_label, output_csv):
    if not os.path.exists(db_path):
        print(f"Error: Database file not found at {db_path}")
        return

    try:
        conn = sqlite3.connect(db_path)
    except sqlite3.Error as e:
        print(f"Error connecting to database: {e}")
        return

    # Detailed query to see every set
    query = """
    SELECT
        s.dateString,
        s.dayName,
        l.exerciseLabel,
        l.setNum,
        l.weightAchieved,
        l.repsAchieved,
        (l.weightAchieved * l.repsAchieved) as setVolumeLoad
    FROM workout_sessions s
    JOIN set_logs l ON s.sessionId = l.sessionId
    WHERE l.exerciseLabel = ?
    ORDER BY s.dateString ASC, l.setNum ASC
    """

    df = pd.read_sql_query(query, conn, params=(exercise_label,))
    conn.close()

    if df.empty:
        print(f"No data found for exercise: {exercise_label}")
        return

    # Calculate session total for verification
    session_totals = df.groupby('dateString')['setVolumeLoad'].sum().rename('sessionTotalVolume').reset_index()
    df = df.merge(session_totals, on='dateString')

    df.to_csv(output_csv, index=False)
    print(f"Raw data for '{exercise_label}' dumped to {output_csv}")
    print("\nFirst few rows of data:")
    print(df.head(10))

if __name__ == "__main__":
    script_dir = os.path.dirname(os.path.abspath(__file__))
    db_file = os.path.join(script_dir, "wetzel_bulk_db.sqlite")
    output_file = os.path.join(script_dir, "inclined_press_raw.csv")

    dump_to_csv(db_file, "Inc. Chest", output_file)
