# 关联分析服务
from models.database import get_db
from services.sentiment import POSITIVE_KEYWORDS, NEGATIVE_KEYWORDS


# ────────────────────────────────────────────
# 关联分析1：品牌与情感倾向关联
# ────────────────────────────────────────────
def brand_sentiment_association() -> dict:
    """
    品牌 × 情感标签 交叉统计，返回弦图所需数据。
    输出: brands, labels, matrix, brand_stats
    """
    db = get_db()
    rows = db.execute(
        "SELECT brand, sentiment_label FROM comments WHERE brand IS NOT NULL"
    ).fetchall()
    db.close()

    brands: list[str] = []
    label_map = {'positive': '正面', 'neutral': '中性', 'negative': '负面'}
    labels = ['正面', '中性', '负面']
    cnt: dict[str, dict[str, int]] = {}

    for r in rows:
        b = r['brand'] or '未知'
        lbl = label_map.get(r['sentiment_label'], '中性')
        if b not in cnt:
            cnt[b] = {'正面': 0, '中性': 0, '负面': 0}
        cnt[b][lbl] += 1

    # 取评论数最多的 8 个品牌
    top_brands = sorted(cnt, key=lambda b: -sum(cnt[b].values()))[:8]

    series = []
    for lbl in labels:
        series.append({
            'name': lbl,
            'data': [cnt.get(b, {}).get(lbl, 0) for b in top_brands]
        })

    # 品牌情感满意度统计
    brand_stats = []
    for b in top_brands:
        total = sum(cnt[b].values())
        pos_rate = round(cnt[b]['正面'] / total * 100, 1) if total else 0
        brand_stats.append({
            'brand': b,
            'total': total,
            'positive': cnt[b]['正面'],
            'neutral': cnt[b]['中性'],
            'negative': cnt[b]['负面'],
            'pos_rate': pos_rate,
        })

    return {
        'brands': top_brands,
        'labels': labels,
        'series': series,
        'brand_stats': brand_stats,
    }


# ────────────────────────────────────────────
# 关联分析2：商品规格与满意度关联
# ────────────────────────────────────────────
def spec_satisfaction_association() -> dict:
    """
    分析不同规格（存储/RAM）与情感分数的关联关系。
    """
    db = get_db()
    rows = db.execute(
        "SELECT spec, rating, sentiment_score, sentiment_label FROM comments WHERE spec IS NOT NULL"
    ).fetchall()
    db.close()

    # 提取存储规格关键词
    import re
    storage_pat = re.compile(r'(\d+)(GB|TB)', re.IGNORECASE)
    ram_storage: dict[str, list[float]] = {}

    for r in rows:
        spec = r['spec'] or ''
        matches = storage_pat.findall(spec)
        if not matches:
            continue
        # 取最后一个作为存储（通常格式 RAM+Storage）
        key = '+'.join(f"{v}{u.upper()}" for v, u in matches[-min(2, len(matches)):])
        if key not in ram_storage:
            ram_storage[key] = []
        ram_storage[key].append(float(r['sentiment_score'] or 0.5))

    # 取出现次数>=5 的规格
    spec_data = []
    for spec, scores in ram_storage.items():
        if len(scores) < 5:
            continue
        avg = sum(scores) / len(scores)
        spec_data.append({
            'spec': spec,
            'count': len(scores),
            'avg_sentiment': round(avg, 3),
            'avg_sentiment_pct': round(avg * 100, 1),
        })
    spec_data.sort(key=lambda x: -x['count'])
    spec_data = spec_data[:15]

    # 价格区间 × 情感分布
    db = get_db()
    phones = db.execute("SELECT current_price, sentiment_score FROM phones").fetchall()
    db.close()

    price_bins = [(0, 2000, '2000以下'), (2000, 3000, '2000-3000'),
                  (3000, 4000, '3000-4000'), (4000, 5000, '4000-5000'),
                  (5000, 9999, '5000以上')]
    price_sentiment: list[dict] = []
    for lo, hi, label in price_bins:
        scores = [float(p['sentiment_score'] or 0.5)
                  for p in phones
                  if p['current_price'] and lo <= float(p['current_price']) < hi]
        if scores:
            price_sentiment.append({
                'range': label,
                'avg': round(sum(scores) / len(scores), 3),
                'count': len(scores),
            })

    # 评分 × 情感标签气泡数据
    db = get_db()
    scatter_rows = db.execute(
        """SELECT rating, sentiment_score, brand
           FROM comments WHERE rating IS NOT NULL LIMIT 500"""
    ).fetchall()
    db.close()
    scatter = [
        {'x': float(r['rating']), 'y': round(float(r['sentiment_score'] or 0.5), 3),
         'brand': r['brand']}
        for r in scatter_rows
    ]

    return {
        'spec_data': spec_data,
        'price_sentiment': price_sentiment,
        'scatter': scatter,
    }


# ────────────────────────────────────────────
# 关联分析3：协同评论关联（共同评论商品关系）
# ────────────────────────────────────────────
def copurchase_association() -> dict:
    """
    挖掘同一用户评论过的商品对，计算共现次数，
    输出前 N 对强关联商品对（类 Apriori 共现挖掘）。
    """
    db = get_db()
    rows = db.execute(
        "SELECT nickname, product_id FROM comments WHERE nickname IS NOT NULL"
    ).fetchall()
    phones_rows = db.execute(
        "SELECT product_id, brand, title FROM phones"
    ).fetchall()
    db.close()

    phone_map = {r['product_id']: r for r in phones_rows}
    user_products: dict[str, set[str]] = {}
    for r in rows:
        u = r['nickname']
        p = r['product_id']
        user_products.setdefault(u, set()).add(p)

    # 计算商品对共现次数
    pair_cnt: dict[tuple[str, str], int] = {}
    for products in user_products.values():
        lst = sorted(products)
        for i in range(len(lst)):
            for j in range(i + 1, len(lst)):
                pair = (lst[i], lst[j])
                pair_cnt[pair] = pair_cnt.get(pair, 0) + 1

    # 取前 20 对
    top_pairs = sorted(pair_cnt, key=lambda k: -pair_cnt[k])[:20]

    # 构建力导向图节点和边（去掉自环，去重方向）
    nodes: dict[str, dict] = {}
    links = []
    seen_pairs: set[tuple[str, str]] = set()

    def node_label(pid: str) -> str:
        info = phone_map.get(pid)
        if not info:
            return pid[:10]
        brand = info['brand'] or ''
        title = info['title'] or ''
        # 取标题前8个字（去掉品牌前缀避免重复）
        t = title[:10].strip()
        return f"{brand}·{t}" if brand else t

    for (p1, p2) in top_pairs:
        if p1 == p2:          # 去除自环
            continue
        cnt = pair_cnt[(p1, p2)]
        name1 = node_label(p1)
        name2 = node_label(p2)
        if name1 == name2:    # 名称碰撞也跳过
            continue
        # 去除方向重复
        key = tuple(sorted([name1, name2]))
        if key in seen_pairs:
            continue
        seen_pairs.add(key)

        info1 = phone_map.get(p1)
        info2 = phone_map.get(p2)
        nodes[name1] = {'name': name1, 'brand': info1['brand'] if info1 else ''}
        nodes[name2] = {'name': name2, 'brand': info2['brand'] if info2 else ''}
        links.append({'source': name1, 'target': name2, 'value': cnt})

    # 品牌共现热力图
    brand_pair_cnt: dict[tuple[str, str], int] = {}
    for r in rows:
        p = r['product_id']
        info = phone_map.get(p)
        if info:
            brand = info['brand']
            user_products.setdefault(r['nickname'], set())
    # 重新遍历计算品牌对
    user_brands: dict[str, set[str]] = {}
    for r in rows:
        u = r['nickname']
        info = phone_map.get(r['product_id'])
        if info and info['brand']:
            user_brands.setdefault(u, set()).add(info['brand'])

    for brands in user_brands.values():
        lst = sorted(brands)
        for i in range(len(lst)):
            for j in range(i + 1, len(lst)):
                pair = (lst[i], lst[j])
                brand_pair_cnt[pair] = brand_pair_cnt.get(pair, 0) + 1

    all_brands = sorted({b for pair in brand_pair_cnt for b in pair})
    heat_data = []
    for i, b1 in enumerate(all_brands):
        for j, b2 in enumerate(all_brands):
            if i < j:
                v = brand_pair_cnt.get((b1, b2), brand_pair_cnt.get((b2, b1), 0))
                heat_data.append([i, j, v])
                heat_data.append([j, i, v])

    # 关联规则（支持度/置信度）
    total_users = len(user_brands)
    rules = []
    for (p1, p2), cnt in sorted(pair_cnt.items(), key=lambda x: -x[1])[:10]:
        support = round(cnt / total_users, 4)
        info1 = phone_map.get(p1)
        info2 = phone_map.get(p2)
        rules.append({
            'antecedent': (info1['brand'] if info1 else p1[:8]),
            'consequent': (info2['brand'] if info2 else p2[:8]),
            'support': support,
            'count': cnt,
        })

    return {
        'nodes': list(nodes.values()),
        'links': links,
        'brands': all_brands,
        'heat_data': heat_data,
        'rules': rules,
    }


# ────────────────────────────────────────────
# 数据大屏 API 数据
# ────────────────────────────────────────────
def dashboard_data() -> dict:
    db = get_db()

    # 1. 品牌销量分布
    brand_sales = db.execute(
        """SELECT brand,
                  SUM(CASE WHEN sales GLOB '*万+*' THEN CAST(REPLACE(sales,'万+','') AS REAL)*10000
                           WHEN sales GLOB '*+*' THEN CAST(REPLACE(sales,'+','') AS REAL)
                           ELSE CAST(sales AS REAL) END) as total_sales
           FROM phones GROUP BY brand ORDER BY total_sales DESC LIMIT 8"""
    ).fetchall()

    # 2. 价格区间分布
    price_ranges = db.execute(
        """SELECT
             CASE WHEN current_price<2000 THEN '2000以下'
                  WHEN current_price<3000 THEN '2000-3000'
                  WHEN current_price<4000 THEN '3000-4000'
                  WHEN current_price<5000 THEN '4000-5000'
                  ELSE '5000以上' END as price_range,
             COUNT(*) as cnt
           FROM phones GROUP BY price_range ORDER BY current_price"""
    ).fetchall()

    # 3. 情感倾向分布
    sentiment_dist = db.execute(
        """SELECT sentiment_label, COUNT(*) as cnt FROM comments GROUP BY sentiment_label"""
    ).fetchall()

    # 4. 月度评论趋势
    monthly_trend = db.execute(
        """SELECT substr(comment_date,1,7) as month, COUNT(*) as cnt,
                  AVG(rating) as avg_rating
           FROM comments WHERE comment_date IS NOT NULL
           GROUP BY month ORDER BY month DESC LIMIT 12"""
    ).fetchall()

    # 5. 品牌情感雷达（品牌 × 5个维度）
    brands_top = db.execute(
        "SELECT brand FROM phones GROUP BY brand ORDER BY COUNT(*) DESC LIMIT 6"
    ).fetchall()
    radar_brands = [r['brand'] for r in brands_top]
    radar_data = []
    for b in radar_brands:
        stats = db.execute(
            """SELECT AVG(sentiment_score) as avg_sent, AVG(rating) as avg_rating,
                      COUNT(*) as cnt, MAX(sentiment_score) as max_sent,
                      MIN(sentiment_score) as min_sent
               FROM comments WHERE brand=?""", (b,)
        ).fetchone()
        radar_data.append({
            'brand': b,
            'avg_sentiment': round(float(stats['avg_sent'] or 0.5) * 100, 1),
            'avg_rating': round(float(stats['avg_rating'] or 3) * 20, 1),
            'review_cnt': min(int(stats['cnt'] or 0) / 5, 100),
            'max_sentiment': round(float(stats['max_sent'] or 1) * 100, 1),
            'min_sentiment': round(float(stats['min_sent'] or 0) * 100, 1),
        })

    # 6. 评论词云
    contents = db.execute("SELECT content FROM comments LIMIT 500").fetchall()
    from services.sentiment import extract_keywords
    wordcloud = extract_keywords([r['content'] for r in contents], top_n=60)

    db.close()

    return {
        'brand_sales': [{'brand': r['brand'], 'sales': int(r['total_sales'] or 0)}
                        for r in brand_sales],
        'price_ranges': [{'range': r['price_range'], 'count': r['cnt']}
                         for r in price_ranges],
        'sentiment_dist': [{'label': r['sentiment_label'], 'count': r['cnt']}
                           for r in sentiment_dist],
        'monthly_trend': [{'month': r['month'], 'count': r['cnt'],
                           'avg_rating': round(float(r['avg_rating'] or 0), 2)}
                          for r in reversed(monthly_trend)],
        'radar_brands': radar_brands,
        'radar_data': radar_data,
        'wordcloud': wordcloud,
    }
