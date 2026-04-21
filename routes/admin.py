from flask import Blueprint, render_template, request, redirect, url_for, session, flash
from functools import wraps
from models.database import get_db

admin_bp = Blueprint('admin', __name__, url_prefix='/admin')

PER_PAGE = 10


def admin_required(f):
    @wraps(f)
    def decorated(*args, **kwargs):
        if 'user_id' not in session or session.get('role') != 'admin':
            return redirect(url_for('auth.login'))
        return f(*args, **kwargs)
    return decorated


# ─────────────────────────── 控制台 ───────────────────────────
@admin_bp.route('/')
@admin_required
def index():
    db = get_db()
    stats = {
        'phones':   db.execute("SELECT COUNT(*) FROM phones").fetchone()[0],
        'comments': db.execute("SELECT COUNT(*) FROM comments").fetchone()[0],
        'users':    db.execute("SELECT COUNT(*) FROM users").fetchone()[0],
        'behaviors':db.execute("SELECT COUNT(*) FROM user_behavior").fetchone()[0],
    }
    brands = db.execute(
        "SELECT brand, COUNT(*) as cnt FROM phones GROUP BY brand ORDER BY cnt DESC LIMIT 5"
    ).fetchall()
    recent_comments = db.execute(
        "SELECT nickname,brand,rating,sentiment_label,comment_date FROM comments ORDER BY comment_date DESC LIMIT 5"
    ).fetchall()
    db.close()
    return render_template('admin/index.html', stats=stats,
                           brands=[dict(r) for r in brands],
                           recent_comments=[dict(r) for r in recent_comments])


# ─────────────────────────── 商品管理 ───────────────────────────
@admin_bp.route('/phones')
@admin_required
def phones_list():
    page = request.args.get('page', 1, type=int)
    kw   = request.args.get('q', '').strip()
    db   = get_db()
    cond = "WHERE title LIKE ? OR brand LIKE ?" if kw else ""
    params = [f'%{kw}%', f'%{kw}%'] if kw else []
    total = db.execute(f"SELECT COUNT(*) FROM phones {cond}", params).fetchone()[0]
    rows  = db.execute(
        f"SELECT * FROM phones {cond} ORDER BY id DESC LIMIT ? OFFSET ?",
        params + [PER_PAGE, (page - 1) * PER_PAGE]
    ).fetchall()
    db.close()
    return render_template('admin/phones/list.html',
                           rows=[dict(r) for r in rows],
                           page=page, total_pages=(total + PER_PAGE - 1) // PER_PAGE,
                           total=total, kw=kw)


@admin_bp.route('/phones/add', methods=['GET', 'POST'])
@admin_required
def phones_add():
    if request.method == 'POST':
        f = request.form
        db = get_db()
        try:
            db.execute('''INSERT INTO phones
                (brand,title,current_price,original_price,discount_price,
                 sales,shop_name,image_url,gov_subsidy,self_operated,product_id,link_url)
                VALUES(?,?,?,?,?,?,?,?,?,?,?,?)''',
                (f['brand'], f['title'], _fp(f.get('current_price')),
                 _fp(f.get('original_price')), _fp(f.get('discount_price')),
                 f.get('sales'), f.get('shop_name'), f.get('image_url'),
                 f.get('gov_subsidy'), f.get('self_operated'),
                 f.get('product_id'), f.get('link_url')))
            db.commit()
            flash('商品添加成功', 'success')
        except Exception as e:
            flash(f'添加失败：{e}', 'danger')
        finally:
            db.close()
        return redirect(url_for('admin.phones_list'))
    return render_template('admin/phones/form.html', phone=None, action='add')


@admin_bp.route('/phones/edit/<int:pid>', methods=['GET', 'POST'])
@admin_required
def phones_edit(pid):
    db = get_db()
    phone = db.execute("SELECT * FROM phones WHERE id=?", (pid,)).fetchone()
    if not phone:
        db.close()
        flash('记录不存在', 'warning')
        return redirect(url_for('admin.phones_list'))
    if request.method == 'POST':
        f = request.form
        try:
            db.execute('''UPDATE phones SET
                brand=?,title=?,current_price=?,original_price=?,discount_price=?,
                sales=?,shop_name=?,image_url=?,gov_subsidy=?,self_operated=?,
                product_id=?,link_url=? WHERE id=?''',
                (f['brand'], f['title'], _fp(f.get('current_price')),
                 _fp(f.get('original_price')), _fp(f.get('discount_price')),
                 f.get('sales'), f.get('shop_name'), f.get('image_url'),
                 f.get('gov_subsidy'), f.get('self_operated'),
                 f.get('product_id'), f.get('link_url'), pid))
            db.commit()
            flash('修改成功', 'success')
        except Exception as e:
            flash(f'修改失败：{e}', 'danger')
        finally:
            db.close()
        return redirect(url_for('admin.phones_list'))
    db.close()
    return render_template('admin/phones/form.html', phone=dict(phone), action='edit')


@admin_bp.route('/phones/delete/<int:pid>', methods=['POST'])
@admin_required
def phones_delete(pid):
    db = get_db()
    db.execute("DELETE FROM phones WHERE id=?", (pid,))
    db.commit()
    db.close()
    flash('删除成功', 'success')
    return redirect(url_for('admin.phones_list'))


# ─────────────────────────── 评论管理 ───────────────────────────
@admin_bp.route('/comments')
@admin_required
def comments_list():
    page = request.args.get('page', 1, type=int)
    kw   = request.args.get('q', '').strip()
    db   = get_db()
    cond = "WHERE nickname LIKE ? OR brand LIKE ? OR content LIKE ?" if kw else ""
    params = [f'%{kw}%'] * 3 if kw else []
    total = db.execute(f"SELECT COUNT(*) FROM comments {cond}", params).fetchone()[0]
    rows  = db.execute(
        f"SELECT * FROM comments {cond} ORDER BY id DESC LIMIT ? OFFSET ?",
        params + [PER_PAGE, (page - 1) * PER_PAGE]
    ).fetchall()
    db.close()
    return render_template('admin/comments/list.html',
                           rows=[dict(r) for r in rows],
                           page=page, total_pages=(total + PER_PAGE - 1) // PER_PAGE,
                           total=total, kw=kw)


@admin_bp.route('/comments/add', methods=['GET', 'POST'])
@admin_required
def comments_add():
    if request.method == 'POST':
        f = request.form
        try:
            rating  = float(f.get('rating', 3))
            content = f.get('content', '')
            from services.sentiment import calculate_sentiment_score
            score, label = calculate_sentiment_score(rating, content)
            db = get_db()
            db.execute('''INSERT INTO comments
                (product_id,brand,title,nickname,rating,spec,comment_date,
                 content,sentiment_score,sentiment_label)
                VALUES(?,?,?,?,?,?,?,?,?,?)''',
                (f.get('product_id'), f.get('brand'), f.get('title'),
                 f.get('nickname'), rating, f.get('spec'),
                 f.get('comment_date'), content, score, label))
            db.commit()
            db.close()
            flash('评论添加成功', 'success')
        except Exception as e:
            flash(f'添加失败：{e}', 'danger')
        return redirect(url_for('admin.comments_list'))
    return render_template('admin/comments/form.html', comment=None, action='add')


@admin_bp.route('/comments/edit/<int:cid>', methods=['GET', 'POST'])
@admin_required
def comments_edit(cid):
    db = get_db()
    comment = db.execute("SELECT * FROM comments WHERE id=?", (cid,)).fetchone()
    if not comment:
        db.close()
        flash('记录不存在', 'warning')
        return redirect(url_for('admin.comments_list'))
    if request.method == 'POST':
        f = request.form
        try:
            rating  = float(f.get('rating', 3))
            content = f.get('content', '')
            from services.sentiment import calculate_sentiment_score
            score, label = calculate_sentiment_score(rating, content)
            db.execute('''UPDATE comments SET
                product_id=?,brand=?,title=?,nickname=?,rating=?,spec=?,
                comment_date=?,content=?,sentiment_score=?,sentiment_label=?
                WHERE id=?''',
                (f.get('product_id'), f.get('brand'), f.get('title'),
                 f.get('nickname'), rating, f.get('spec'),
                 f.get('comment_date'), content, score, label, cid))
            db.commit()
            flash('修改成功', 'success')
        except Exception as e:
            flash(f'修改失败：{e}', 'danger')
        finally:
            db.close()
        return redirect(url_for('admin.comments_list'))
    db.close()
    return render_template('admin/comments/form.html', comment=dict(comment), action='edit')


@admin_bp.route('/comments/delete/<int:cid>', methods=['POST'])
@admin_required
def comments_delete(cid):
    db = get_db()
    db.execute("DELETE FROM comments WHERE id=?", (cid,))
    db.commit()
    db.close()
    flash('删除成功', 'success')
    return redirect(url_for('admin.comments_list'))


# ─────────────────────────── 用户管理 ───────────────────────────
@admin_bp.route('/users')
@admin_required
def users_list():
    page = request.args.get('page', 1, type=int)
    kw   = request.args.get('q', '').strip()
    db   = get_db()
    cond = "WHERE username LIKE ?" if kw else ""
    params = [f'%{kw}%'] if kw else []
    total = db.execute(f"SELECT COUNT(*) FROM users {cond}", params).fetchone()[0]
    rows  = db.execute(
        f"SELECT * FROM users {cond} ORDER BY id DESC LIMIT ? OFFSET ?",
        params + [PER_PAGE, (page - 1) * PER_PAGE]
    ).fetchall()
    db.close()
    return render_template('admin/users/list.html',
                           rows=[dict(r) for r in rows],
                           page=page, total_pages=(total + PER_PAGE - 1) // PER_PAGE,
                           total=total, kw=kw)


@admin_bp.route('/users/add', methods=['GET', 'POST'])
@admin_required
def users_add():
    if request.method == 'POST':
        f = request.form
        db = get_db()
        try:
            db.execute("INSERT INTO users (username,password,role) VALUES(?,?,?)",
                       (f['username'], f['password'], f.get('role', 'user')))
            db.commit()
            flash('用户添加成功', 'success')
        except Exception as e:
            flash(f'添加失败（用户名可能重复）：{e}', 'danger')
        finally:
            db.close()
        return redirect(url_for('admin.users_list'))
    return render_template('admin/users/form.html', user=None, action='add')


@admin_bp.route('/users/edit/<int:uid>', methods=['GET', 'POST'])
@admin_required
def users_edit(uid):
    db = get_db()
    user = db.execute("SELECT * FROM users WHERE id=?", (uid,)).fetchone()
    if not user:
        db.close()
        flash('用户不存在', 'warning')
        return redirect(url_for('admin.users_list'))
    if request.method == 'POST':
        f = request.form
        try:
            db.execute("UPDATE users SET username=?,password=?,role=? WHERE id=?",
                       (f['username'], f['password'], f.get('role', 'user'), uid))
            db.commit()
            flash('修改成功', 'success')
        except Exception as e:
            flash(f'修改失败：{e}', 'danger')
        finally:
            db.close()
        return redirect(url_for('admin.users_list'))
    db.close()
    return render_template('admin/users/form.html', user=dict(user), action='edit')


@admin_bp.route('/users/delete/<int:uid>', methods=['POST'])
@admin_required
def users_delete(uid):
    if uid == session.get('user_id'):
        flash('不能删除当前登录账号', 'warning')
        return redirect(url_for('admin.users_list'))
    db = get_db()
    db.execute("DELETE FROM users WHERE id=?", (uid,))
    db.commit()
    db.close()
    flash('删除成功', 'success')
    return redirect(url_for('admin.users_list'))


def _fp(val):
    try:
        return float(val) if val else None
    except (ValueError, TypeError):
        return None
