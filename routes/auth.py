from flask import Blueprint, render_template, request, redirect, url_for, session, flash
from models.database import get_db

auth_bp = Blueprint('auth', __name__)


@auth_bp.route('/login', methods=['GET', 'POST'])
def login():
    if request.method == 'POST':
        username = request.form.get('username', '').strip()
        password = request.form.get('password', '').strip()
        role_want = request.form.get('role', 'user')

        db = get_db()
        user = db.execute(
            "SELECT * FROM users WHERE username=? AND password=?",
            (username, password)
        ).fetchone()
        db.close()

        if user:
            if role_want == 'admin' and user['role'] != 'admin':
                flash('该账号没有管理员权限', 'danger')
                return redirect(url_for('auth.login'))
            session['user_id']  = user['id']
            session['username'] = user['username']
            session['role']     = user['role']
            if user['role'] == 'admin':
                return redirect(url_for('admin.index'))
            return redirect(url_for('frontend.index'))
        else:
            flash('用户名或密码错误', 'danger')
    return render_template('login.html')


@auth_bp.route('/logout')
def logout():
    session.clear()
    return redirect(url_for('auth.login'))
