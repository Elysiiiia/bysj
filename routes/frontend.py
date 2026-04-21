from flask import Blueprint, render_template, request, session, redirect, url_for, jsonify
from functools import wraps
from models.database import get_db
from services.recommendation import (
    get_recommendations, similar_products, cold_start_recommend, record_behavior
)
from config import Config

frontend_bp = Blueprint('frontend', __name__)


def login_required(f):
    @wraps(f)
    def decorated(*args, **kwargs):
        if 'user_id' not in session:
            return redirect(url_for('auth.login'))
        return f(*args, **kwargs)
    return decorated


@frontend_bp.route('/')
@login_required
def index():
    user_id = session['user_id']
    recs = get_recommendations(user_id, top_n=8)
    db = get_db()
    featured = db.execute(
        """SELECT * FROM phones ORDER BY sentiment_score DESC, avg_rating DESC LIMIT 4"""
    ).fetchall()
    total_phones    = db.execute("SELECT COUNT(*) FROM phones").fetchone()[0]
    total_comments  = db.execute("SELECT COUNT(*) FROM comments").fetchone()[0]
    pos_count       = db.execute(
        "SELECT COUNT(*) FROM comments WHERE sentiment_label='positive'"
    ).fetchone()[0]
    db.close()
    pos_rate = round(pos_count / total_comments * 100, 1) if total_comments else 0
    return render_template(
        'frontend/index.html',
        recommendations=recs,
        featured=[dict(r) for r in featured],
        total_phones=total_phones,
        total_comments=total_comments,
        pos_rate=pos_rate,
    )


@frontend_bp.route('/products')
@login_required
def products():
    page    = request.args.get('page', 1, type=int)
    keyword = request.args.get('q', '').strip()
    brand   = request.args.get('brand', '').strip()
    per_page = Config.PER_PAGE

    db = get_db()
    conditions = []
    params: list = []
    if keyword:
        conditions.append("(title LIKE ? OR brand LIKE ?)")
        params += [f'%{keyword}%', f'%{keyword}%']
    if brand:
        conditions.append("brand=?")
        params.append(brand)
    where = ('WHERE ' + ' AND '.join(conditions)) if conditions else ''

    total = db.execute(f"SELECT COUNT(*) FROM phones {where}", params).fetchone()[0]
    offset = (page - 1) * per_page
    rows = db.execute(
        f"SELECT * FROM phones {where} ORDER BY sentiment_score DESC, avg_rating DESC LIMIT ? OFFSET ?",
        params + [per_page, offset]
    ).fetchall()
    brands = db.execute("SELECT DISTINCT brand FROM phones WHERE brand IS NOT NULL ORDER BY brand").fetchall()
    db.close()

    total_pages = (total + per_page - 1) // per_page
    return render_template(
        'frontend/products.html',
        phones=[dict(r) for r in rows],
        page=page,
        total_pages=total_pages,
        total=total,
        keyword=keyword,
        selected_brand=brand,
        brands=[r['brand'] for r in brands],
    )


@frontend_bp.route('/product/<product_id>')
@login_required
def product_detail(product_id):
    user_id = session['user_id']
    record_behavior(user_id, product_id, 'view')

    db = get_db()
    phone = db.execute("SELECT * FROM phones WHERE product_id=?", (product_id,)).fetchone()
    if not phone:
        db.close()
        return redirect(url_for('frontend.products'))

    page = request.args.get('page', 1, type=int)
    per_page = 5
    total_c = db.execute(
        "SELECT COUNT(*) FROM comments WHERE product_id=?", (product_id,)
    ).fetchone()[0]
    offset = (page - 1) * per_page
    comments = db.execute(
        """SELECT * FROM comments WHERE product_id=?
           ORDER BY comment_date DESC LIMIT ? OFFSET ?""",
        (product_id, per_page, offset)
    ).fetchall()

    # 情感分布
    sent_dist = db.execute(
        """SELECT sentiment_label, COUNT(*) as cnt FROM comments
           WHERE product_id=? GROUP BY sentiment_label""",
        (product_id,)
    ).fetchall()
    db.close()

    related = similar_products(product_id, top_n=4)
    total_pages = (total_c + per_page - 1) // per_page
    sent_map = {r['sentiment_label']: r['cnt'] for r in sent_dist}
    return render_template(
        'frontend/product_detail.html',
        phone=dict(phone),
        comments=[dict(c) for c in comments],
        related=related,
        page=page,
        total_pages=total_pages,
        total_comments=total_c,
        sent_map=sent_map,
    )


@frontend_bp.route('/recommend')
@login_required
def recommend():
    user_id = session['user_id']
    recs = get_recommendations(user_id, top_n=16)

    db = get_db()
    from models.database import get_db as _gdb
    history_ids = db.execute(
        """SELECT DISTINCT product_id, MAX(created_at) as ts
           FROM user_behavior WHERE user_id=?
           GROUP BY product_id ORDER BY ts DESC LIMIT 8""",
        (user_id,)
    ).fetchall()
    history_phones = []
    for h in history_ids:
        p = db.execute("SELECT * FROM phones WHERE product_id=?", (h['product_id'],)).fetchone()
        if p:
            history_phones.append(dict(p))
    db.close()

    return render_template(
        'frontend/recommend.html',
        recommendations=recs,
        history=history_phones,
    )


@frontend_bp.route('/api/track', methods=['POST'])
@login_required
def track_behavior():
    data = request.get_json()
    product_id = data.get('product_id')
    action = data.get('action', 'view')
    if product_id:
        record_behavior(session['user_id'], product_id, action)
    return jsonify({'ok': True})
