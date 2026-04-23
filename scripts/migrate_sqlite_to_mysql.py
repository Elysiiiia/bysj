import argparse
import sqlite3
from pathlib import Path

import pymysql


ROOT = Path(__file__).resolve().parents[1]
SQLITE_PATH = ROOT / "database" / "phone_recommend.db"
SCHEMA_PATH = ROOT / "database" / "schema.sql"


TABLES = ("users", "phones", "comments", "user_behavior")


def sqlite_rows(conn: sqlite3.Connection, table: str) -> list[dict]:
    conn.row_factory = sqlite3.Row
    rows = conn.execute(f"SELECT * FROM {table}").fetchall()
    return [dict(row) for row in rows]


def insert_rows(cursor, table: str, rows: list[dict]) -> None:
    if not rows:
        return
    columns = list(rows[0].keys())
    quoted_columns = ", ".join(f"`{column}`" for column in columns)
    placeholders = ", ".join(["%s"] * len(columns))
    updates = ", ".join(f"`{column}` = VALUES(`{column}`)" for column in columns if column != "id")
    sql = (
        f"INSERT INTO `{table}` ({quoted_columns}) VALUES ({placeholders}) "
        f"ON DUPLICATE KEY UPDATE {updates}"
    )
    values = [tuple(row.get(column) for column in columns) for row in rows]
    cursor.executemany(sql, values)


def reset_tables(cursor) -> None:
    cursor.execute("SET FOREIGN_KEY_CHECKS = 0")
    for table in reversed(TABLES):
        cursor.execute(f"TRUNCATE TABLE `{table}`")
    cursor.execute("SET FOREIGN_KEY_CHECKS = 1")


def ensure_schema(cursor) -> None:
    schema = SCHEMA_PATH.read_text(encoding="utf-8")
    statements = []
    for raw_statement in schema.split(";"):
        statement = raw_statement.strip()
        if not statement:
            continue
        upper = statement.upper()
        if upper.startswith("CREATE DATABASE") or upper.startswith("USE "):
            continue
        statements.append(statement)
    for statement in statements:
        cursor.execute(statement)


def migrate(args) -> None:
    if not SQLITE_PATH.exists():
        raise FileNotFoundError(f"SQLite database not found: {SQLITE_PATH}")

    sqlite_conn = sqlite3.connect(SQLITE_PATH)
    server_conn = pymysql.connect(
        host=args.host,
        port=args.port,
        user=args.user,
        password=args.password,
        charset="utf8mb4",
        autocommit=False,
    )
    with server_conn.cursor() as cursor:
        cursor.execute(
            f"CREATE DATABASE IF NOT EXISTS `{args.database}` "
            "DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci"
        )
    server_conn.commit()
    server_conn.close()

    mysql_conn = pymysql.connect(
        host=args.host,
        port=args.port,
        user=args.user,
        password=args.password,
        database=args.database,
        charset="utf8mb4",
        autocommit=False,
    )

    try:
        with mysql_conn.cursor() as cursor:
            ensure_schema(cursor)
            if args.reset:
                reset_tables(cursor)

            summary = {}
            for table in TABLES:
                rows = sqlite_rows(sqlite_conn, table)
                insert_rows(cursor, table, rows)
                summary[table] = len(rows)

            mysql_conn.commit()
            print("Migration completed.")
            for table, count in summary.items():
                print(f"{table}: {count}")
    except Exception:
        mysql_conn.rollback()
        raise
    finally:
        mysql_conn.close()
        sqlite_conn.close()


def main() -> None:
    parser = argparse.ArgumentParser(description="Migrate original SQLite data to MySQL.")
    parser.add_argument("--host", default="localhost")
    parser.add_argument("--port", type=int, default=3306)
    parser.add_argument("--user", default="root")
    parser.add_argument("--password", default="sun123456")
    parser.add_argument("--database", default="sys")
    parser.add_argument("--reset", action="store_true", help="Truncate target tables before import.")
    migrate(parser.parse_args())


if __name__ == "__main__":
    main()
