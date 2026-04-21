"""
数据导入脚本：将 数据集/ 下的 CSV 文件导入 SQLite 数据库，
同时对评论进行情感分析，更新商品情感均值与平均评分。
运行方式：python import_data.py
"""
import csv
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from config import Config
from models.database import get_db, init_db
from services.sentiment import calculate_sentiment_score


def clean_price(val: str) -> float | None:
    if not val:
        return None
    val = str(val).strip().replace(',', '').replace('¥', '').replace('￥', '')
    try:
        return float(val)
    except ValueError:
        return None


def import_phones(cursor, filepath: str):
    print(f"[导入商品] {filepath}")
    count = 0
    with open(filepath, encoding='utf-8-sig') as f:
        reader = csv.DictReader(f)
        for row in reader:
            cursor.execute('''
                INSERT OR REPLACE INTO phones
                  (brand, title, current_price, original_price, discount_price,
                   sales, shop_name, image_url, gov_subsidy, self_operated,
                   product_id, link_url)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?)
            ''', (
                row.get('品牌', '').strip(),
                row.get('商品标题', '').strip(),
                clean_price(row.get('当前价格', '')),
                clean_price(row.get('原价', '')),
                clean_price(row.get('优惠力度价', '')),
                row.get('销售量', '').strip(),
                row.get('店铺名称', '').strip(),
                row.get('图片地址', '').strip(),
                row.get('政府补贴', '').strip(),
                row.get('自营', '').strip(),
                str(row.get('商品ID', '')).strip(),
                row.get('链接地址', '').strip(),
            ))
            count += 1
    print(f"  ✓ 导入 {count} 条商品记录")


def import_comments(cursor, filepath: str):
    print(f"[导入评论] {filepath}")
    count = 0
    with open(filepath, encoding='utf-8-sig') as f:
        reader = csv.DictReader(f)
        for row in reader:
            try:
                rating = float(row.get('评分', 3))
            except (ValueError, TypeError):
                rating = 3.0
            content = row.get('评论内容', '').strip()
            score, label = calculate_sentiment_score(rating, content)
            cursor.execute('''
                INSERT INTO comments
                  (product_id, brand, title, nickname, rating, spec, comment_date,
                   content, sentiment_score, sentiment_label)
                VALUES (?,?,?,?,?,?,?,?,?,?)
            ''', (
                str(row.get('商品ID', '')).strip(),
                row.get('品牌', '').strip(),
                row.get('商品标题', '').strip(),
                row.get('用户昵称', '').strip(),
                rating,
                row.get('商品规格', '').strip(),
                row.get('评论日期', '').strip(),
                content,
                score,
                label,
            ))
            count += 1
    print(f"  ✓ 导入 {count} 条评论记录")


def update_phone_stats(cursor):
    """更新商品的平均评分、平均情感分和评论数"""
    print("[更新] 商品统计数据...")
    cursor.execute('''
        UPDATE phones SET
          avg_rating      = (SELECT AVG(c.rating) FROM comments c WHERE c.product_id = phones.product_id),
          sentiment_score = (SELECT AVG(c.sentiment_score) FROM comments c WHERE c.product_id = phones.product_id),
          review_count    = (SELECT COUNT(*) FROM comments c WHERE c.product_id = phones.product_id)
        WHERE EXISTS (SELECT 1 FROM comments c WHERE c.product_id = phones.product_id)
    ''')
    print("  ✓ 商品统计数据更新完成")


def main():
    phones_path   = os.path.join(Config.DATASET_DIR, 'phones.csv')
    comments_path = os.path.join(Config.DATASET_DIR, 'comments.csv')

    if not os.path.exists(phones_path):
        print(f"[错误] 找不到文件：{phones_path}")
        sys.exit(1)
    if not os.path.exists(comments_path):
        print(f"[错误] 找不到文件：{comments_path}")
        sys.exit(1)

    print("=" * 50)
    print("  基于用户评论情感分析的商品个性化推荐系统")
    print("  数据导入脚本")
    print("=" * 50)

    # 初始化数据库表结构
    init_db()

    db = get_db()
    cur = db.cursor()

    # 清空旧数据（保留用户表）
    cur.execute("DELETE FROM phones")
    cur.execute("DELETE FROM comments")
    cur.execute("DELETE FROM user_behavior")

    try:
        import_phones(cur, phones_path)
        import_comments(cur, comments_path)
        update_phone_stats(cur)
        db.commit()
        print("\n✅ 数据导入完成！")

        # 统计摘要
        total_phones   = cur.execute("SELECT COUNT(*) FROM phones").fetchone()[0]
        total_comments = cur.execute("SELECT COUNT(*) FROM comments").fetchone()[0]
        pos = cur.execute("SELECT COUNT(*) FROM comments WHERE sentiment_label='positive'").fetchone()[0]
        neu = cur.execute("SELECT COUNT(*) FROM comments WHERE sentiment_label='neutral'").fetchone()[0]
        neg = cur.execute("SELECT COUNT(*) FROM comments WHERE sentiment_label='negative'").fetchone()[0]

        print(f"\n  商品总数：{total_phones}")
        print(f"  评论总数：{total_comments}")
        print(f"  情感分布：正面 {pos} | 中性 {neu} | 负面 {neg}")
    except Exception as e:
        db.rollback()
        print(f"\n❌ 导入失败：{e}")
        raise
    finally:
        db.close()


if __name__ == '__main__':
    main()
