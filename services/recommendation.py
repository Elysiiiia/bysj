# 个性化推荐算法：融合情感特征与协同过滤
import math
from models.database import get_db


# ───────────────────────────────────────────────
# 1. 记录用户行为
# ───────────────────────────────────────────────
def record_behavior(user_id: int, product_id: str, action: str = 'view'):
    db = get_db()
    db.execute(
        "INSERT INTO user_behavior (user_id, product_id, action) VALUES (?,?,?)",
        (user_id, product_id, action)
    )
    db.commit()
    db.close()


# ───────────────────────────────────────────────
# 2. 获取用户历史偏好
# ───────────────────────────────────────────────
def get_user_history(user_id: int) -> list[str]:
    """返回用户浏览/交互过的 product_id 列表（去重，按最近时间倒序）"""
    db = get_db()
    rows = db.execute(
        """SELECT DISTINCT product_id FROM user_behavior
           WHERE user_id=? ORDER BY created_at DESC LIMIT 30""",
        (user_id,)
    ).fetchall()
    db.close()
    return [r['product_id'] for r in rows]


def get_user_preference(user_id: int) -> dict:
    """推断用户品牌/价格偏好"""
    db = get_db()
    rows = db.execute(
        """SELECT p.brand, p.current_price
           FROM user_behavior ub
           JOIN phones p ON p.product_id = ub.product_id
           WHERE ub.user_id=? ORDER BY ub.created_at DESC LIMIT 20""",
        (user_id,)
    ).fetchall()
    db.close()

    brand_cnt: dict[str, int] = {}
    prices = []
    for r in rows:
        if r['brand']:
            brand_cnt[r['brand']] = brand_cnt.get(r['brand'], 0) + 1
        if r['current_price']:
            prices.append(float(r['current_price']))

    pref_brands = sorted(brand_cnt, key=lambda b: -brand_cnt[b])[:3]
    avg_price = sum(prices) / len(prices) if prices else None
    return {'brands': pref_brands, 'avg_price': avg_price}


# ───────────────────────────────────────────────
# 3. 构建用户-商品评分矩阵（来自 comments 表）
# ───────────────────────────────────────────────
def _build_matrix() -> tuple[dict, dict, dict]:
    """
    返回:
      user_items  {nickname: {product_id: sentiment_score}}
      item_users  {product_id: {nickname: sentiment_score}}
      item_info   {product_id: {brand, sentiment_score, avg_rating, ...}}
    """
    db = get_db()
    rows = db.execute(
        "SELECT nickname, product_id, sentiment_score FROM comments"
    ).fetchall()
    phones = db.execute(
        "SELECT product_id, brand, sentiment_score, avg_rating, review_count FROM phones"
    ).fetchall()
    db.close()

    user_items: dict[str, dict[str, float]] = {}
    item_users: dict[str, dict[str, float]] = {}
    for r in rows:
        u, p, s = r['nickname'], r['product_id'], r['sentiment_score']
        user_items.setdefault(u, {})[p] = s
        item_users.setdefault(p, {})[u] = s

    item_info: dict[str, dict] = {}
    for p in phones:
        item_info[p['product_id']] = dict(p)

    return user_items, item_users, item_info


def _cosine_sim(a: dict, b: dict) -> float:
    """两个稀疏向量的余弦相似度"""
    keys = set(a) & set(b)
    if not keys:
        return 0.0
    dot = sum(a[k] * b[k] for k in keys)
    norm_a = math.sqrt(sum(v * v for v in a.values()))
    norm_b = math.sqrt(sum(v * v for v in b.values()))
    if norm_a == 0 or norm_b == 0:
        return 0.0
    return dot / (norm_a * norm_b)


# ───────────────────────────────────────────────
# 4. 基于物品的协同过滤
# ───────────────────────────────────────────────
def item_cf_recommend(history: list[str], item_users: dict,
                      item_info: dict, exclude: set,
                      top_n: int = 8) -> list[dict]:
    """给定已交互商品列表，找相似商品（Item-CF）"""
    scores: dict[str, float] = {}
    for pid in history:
        if pid not in item_users:
            continue
        for candidate, cu in item_users.items():
            if candidate in exclude:
                continue
            sim = _cosine_sim(item_users[pid], cu)
            if sim > 0:
                scores[candidate] = scores.get(candidate, 0) + sim

    # 融合情感分数动态调整排序
    for pid in list(scores):
        info = item_info.get(pid, {})
        sent = float(info.get('sentiment_score') or 0.5)
        rating = float(info.get('avg_rating') or 0.0)
        # CF相似度 60% + 情感分 25% + 评分 15%
        scores[pid] = scores[pid] * 0.6 + sent * 0.25 + (rating / 5.0) * 0.15

    ranked = sorted(scores, key=lambda x: -scores[x])
    return _fetch_phones(ranked[:top_n])


# ───────────────────────────────────────────────
# 5. 情感特征推荐（偏好品牌 + 高情感分）
# ───────────────────────────────────────────────
def sentiment_recommend(pref: dict, item_info: dict,
                        exclude: set, top_n: int = 8) -> list[dict]:
    """基于用户品牌偏好与情感分数推荐"""
    pref_brands = set(pref.get('brands', []))
    avg_price = pref.get('avg_price')

    scored: list[tuple[float, str]] = []
    for pid, info in item_info.items():
        if pid in exclude:
            continue
        s = float(info.get('sentiment_score') or 0.5)
        r = float(info.get('avg_rating') or 0.0) / 5.0
        rc = min(float(info.get('review_count') or 0) / 200.0, 1.0)

        brand_bonus = 0.2 if info.get('brand') in pref_brands else 0.0
        price_bonus = 0.0
        if avg_price and info.get('current_price'):
            diff = abs(float(info['current_price']) - avg_price) / avg_price
            price_bonus = max(0.0, 0.1 - diff * 0.1)

        total = s * 0.4 + r * 0.2 + rc * 0.1 + brand_bonus + price_bonus
        scored.append((total, pid))

    ranked = [pid for _, pid in sorted(scored, reverse=True)]
    return _fetch_phones(ranked[:top_n])


# ───────────────────────────────────────────────
# 6. 冷启动推荐（新用户）
# ───────────────────────────────────────────────
def cold_start_recommend(top_n: int = 8) -> list[dict]:
    """新用户冷启动：综合情感分 + 评分 + 评论数排序"""
    db = get_db()
    rows = db.execute(
        """SELECT *, (sentiment_score*0.5 + (avg_rating/5.0)*0.3 +
           MIN(CAST(review_count AS REAL)/200.0,1.0)*0.2) AS score
           FROM phones ORDER BY score DESC LIMIT ?""",
        (top_n,)
    ).fetchall()
    db.close()
    return [dict(r) for r in rows]


# ───────────────────────────────────────────────
# 7. 相似商品推荐（商品详情页）
# ───────────────────────────────────────────────
def similar_products(product_id: str, top_n: int = 4) -> list[dict]:
    """基于同品牌 + 价格相近 + 情感相似 推荐相关商品"""
    db = get_db()
    target = db.execute(
        "SELECT brand, current_price, sentiment_score FROM phones WHERE product_id=?",
        (product_id,)
    ).fetchone()
    if not target:
        db.close()
        return []

    brand = target['brand']
    price = float(target['current_price'] or 0)
    sent = float(target['sentiment_score'] or 0.5)

    rows = db.execute(
        "SELECT * FROM phones WHERE product_id != ?", (product_id,)
    ).fetchall()
    db.close()

    scored = []
    for r in rows:
        p_price = float(r['current_price'] or 0)
        p_sent = float(r['sentiment_score'] or 0.5)
        brand_sim = 1.0 if r['brand'] == brand else 0.0
        price_sim = 1.0 - min(abs(p_price - price) / max(price, 1), 1.0)
        sent_sim = 1.0 - abs(p_sent - sent)
        score = brand_sim * 0.4 + price_sim * 0.3 + sent_sim * 0.3
        scored.append((score, dict(r)))

    scored.sort(key=lambda x: -x[0])
    return [item for _, item in scored[:top_n]]


# ───────────────────────────────────────────────
# 8. 主推荐入口（混合推荐）
# ───────────────────────────────────────────────
def get_recommendations(user_id: int, top_n: int = 8) -> list[dict]:
    """
    混合推荐主函数：
    - 有历史行为 → Item-CF + 情感推荐融合
    - 无历史行为 → 冷启动
    """
    history = get_user_history(user_id)

    if not history:
        return cold_start_recommend(top_n)

    user_items, item_users, item_info = _build_matrix()
    exclude = set(history)
    pref = get_user_preference(user_id)

    cf_recs = item_cf_recommend(history, item_users, item_info, exclude, top_n)
    sent_recs = sentiment_recommend(pref, item_info, exclude, top_n)

    # 合并去重，CF 结果优先（已融合情感分）
    merged: list[dict] = []
    seen: set[str] = set()
    for item in cf_recs + sent_recs:
        pid = item.get('product_id')
        if pid and pid not in seen:
            seen.add(pid)
            merged.append(item)
        if len(merged) >= top_n:
            break

    # 不足则用冷启动补全
    if len(merged) < top_n:
        cold = cold_start_recommend(top_n)
        for item in cold:
            pid = item.get('product_id')
            if pid and pid not in seen:
                seen.add(pid)
                merged.append(item)
            if len(merged) >= top_n:
                break

    return merged[:top_n]


def _fetch_phones(product_ids: list[str]) -> list[dict]:
    if not product_ids:
        return []
    db = get_db()
    placeholders = ','.join('?' * len(product_ids))
    rows = db.execute(
        f"SELECT * FROM phones WHERE product_id IN ({placeholders})",
        product_ids
    ).fetchall()
    db.close()
    # 按原顺序返回
    order = {pid: i for i, pid in enumerate(product_ids)}
    result = sorted([dict(r) for r in rows],
                    key=lambda r: order.get(r['product_id'], 999))
    return result
