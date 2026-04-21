import sqlite3
from config import Config


def get_db():
    conn = sqlite3.connect(Config.DATABASE)
    conn.row_factory = sqlite3.Row
    conn.execute("PRAGMA journal_mode=WAL")
    return conn


def init_db():
    conn = get_db()
    cur = conn.cursor()

    cur.executescript('''
        CREATE TABLE IF NOT EXISTS users (
            id          INTEGER PRIMARY KEY AUTOINCREMENT,
            username    TEXT UNIQUE NOT NULL,
            password    TEXT NOT NULL,
            role        TEXT NOT NULL DEFAULT 'user',
            created_at  TEXT DEFAULT (datetime('now','localtime'))
        );

        CREATE TABLE IF NOT EXISTS phones (
            id              INTEGER PRIMARY KEY AUTOINCREMENT,
            brand           TEXT,
            title           TEXT,
            current_price   REAL,
            original_price  REAL,
            discount_price  REAL,
            sales           TEXT,
            shop_name       TEXT,
            image_url       TEXT,
            gov_subsidy     TEXT,
            self_operated   TEXT,
            product_id      TEXT UNIQUE,
            link_url        TEXT,
            sentiment_score REAL DEFAULT 0.5,
            avg_rating      REAL DEFAULT 0.0,
            review_count    INTEGER DEFAULT 0
        );

        CREATE TABLE IF NOT EXISTS comments (
            id              INTEGER PRIMARY KEY AUTOINCREMENT,
            product_id      TEXT,
            brand           TEXT,
            title           TEXT,
            nickname        TEXT,
            rating          REAL,
            spec            TEXT,
            comment_date    TEXT,
            content         TEXT,
            sentiment_score REAL DEFAULT 0.5,
            sentiment_label TEXT DEFAULT 'neutral'
        );

        CREATE TABLE IF NOT EXISTS user_behavior (
            id          INTEGER PRIMARY KEY AUTOINCREMENT,
            user_id     INTEGER,
            product_id  TEXT,
            action      TEXT,
            created_at  TEXT DEFAULT (datetime('now','localtime'))
        );
    ''')

    # 默认用户
    cur.execute("SELECT id FROM users WHERE username='Test'")
    if not cur.fetchone():
        cur.execute("INSERT INTO users (username,password,role) VALUES (?,?,?)",
                    ('Test', '123456', 'user'))
    cur.execute("SELECT id FROM users WHERE username='admin'")
    if not cur.fetchone():
        cur.execute("INSERT INTO users (username,password,role) VALUES (?,?,?)",
                    ('admin', 'admin123', 'admin'))

    conn.commit()
    conn.close()
