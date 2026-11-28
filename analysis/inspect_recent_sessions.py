import sqlite3
import os

def inspect_recent(db_path):
    conn = sqlite3.connect(db_path)
    cur = conn.cursor()

    print("Recent Workout Sessions (August 2026):")
    query = """
    SELECT s.sessionId, s.dateString, s.dayName, COUNT(l.logId) as setCount
    FROM workout_sessions s
    LEFT JOIN set_logs l ON s.sessionId = l.sessionId
    WHERE s.dateString LIKE '2026-08-%'
    GROUP BY s.sessionId
    ORDER BY s.dateString DESC, s.sessionId DESC
    """

    cur.execute(query)
    rows = cur.fetchall()

    print(f"{'ID':<5} | {'Date':<10} | {'Day':<12} | {'Sets':<5}")
    print("-" * 40)
    for row in rows:
        print(f"{row[0]:<5} | {row[1]:<10} | {row[2]:<12} | {row[3]:<5}")

    conn.close()

if __name__ == "__main__":
    script_dir = os.path.dirname(os.path.abspath(__file__))
    db_file = os.path.join(script_dir, "wetzel_bulk_db.sqlite")
    inspect_recent(db_file)
