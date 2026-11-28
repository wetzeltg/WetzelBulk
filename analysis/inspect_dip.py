import sqlite3
import pandas as pd
import os

def inspect_data(db_path):
    conn = sqlite3.connect(db_path)

    # Get all Inclined Chest data after July 15th
    query = """
    SELECT
        s.dateString,
        l.setNum,
        l.weightAchieved,
        l.repsAchieved
    FROM workout_sessions s
    JOIN set_logs l ON s.sessionId = l.sessionId
    WHERE l.exerciseLabel = 'Inc. Chest'
      AND s.dateString > '2026-07-15'
    ORDER BY s.dateString ASC, l.setNum ASC
    """

    df = pd.read_sql_query(query, conn)
    conn.close()

    if df.empty:
        print("No data found after 2026-07-15.")
        return

    # Filter for just the first two unique dates
    unique_dates = df['dateString'].unique()[:2]
    result = df[df['dateString'].isin(unique_dates)]

    print(f"Data for the two sessions after July 15, 2026:")
    print(result.to_string(index=False))

if __name__ == "__main__":
    script_dir = os.path.dirname(os.path.abspath(__file__))
    db_file = os.path.join(script_dir, "wetzel_bulk_db.sqlite")
    inspect_data(db_file)
