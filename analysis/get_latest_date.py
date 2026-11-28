import sqlite3
import os

def get_latest_date(db_path):
    if not os.path.exists(db_path):
        print(f"Error: Database file not found at {db_path}")
        return

    conn = sqlite3.connect(db_path)
    cur = conn.cursor()

    query = """
    SELECT MAX(s.dateString)
    FROM workout_sessions s
    JOIN set_logs l ON s.sessionId = l.sessionId
    WHERE l.exerciseLabel = 'Inc. Chest'
    """

    cur.execute(query)
    latest_date = cur.fetchone()[0]
    conn.close()

    print(latest_date)

if __name__ == "__main__":
    script_dir = os.path.dirname(os.path.abspath(__file__))
    db_file = os.path.join(script_dir, "wetzel_bulk_db.sqlite")
    get_latest_date(db_file)
