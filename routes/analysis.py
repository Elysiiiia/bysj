from flask import Blueprint, render_template, jsonify, session, redirect, url_for
from functools import wraps
from services.association import (
    brand_sentiment_association,
    spec_satisfaction_association,
    copurchase_association,
    dashboard_data,
)

analysis_bp = Blueprint('analysis', __name__, url_prefix='/analysis')


def login_required(f):
    @wraps(f)
    def decorated(*args, **kwargs):
        if 'user_id' not in session:
            return redirect(url_for('auth.login'))
        return f(*args, **kwargs)
    return decorated


# ───────────── 数据分析大屏 ─────────────
@analysis_bp.route('/dashboard')
@login_required
def dashboard():
    return render_template('analysis/dashboard.html')


@analysis_bp.route('/api/dashboard')
@login_required
def api_dashboard():
    return jsonify(dashboard_data())


# ───────────── 关联分析 1：品牌情感关联 ─────────────
@analysis_bp.route('/association1')
@login_required
def association1():
    return render_template('analysis/association1.html')


@analysis_bp.route('/api/association1')
@login_required
def api_association1():
    return jsonify(brand_sentiment_association())


# ───────────── 关联分析 2：规格满意度关联 ─────────────
@analysis_bp.route('/association2')
@login_required
def association2():
    return render_template('analysis/association2.html')


@analysis_bp.route('/api/association2')
@login_required
def api_association2():
    return jsonify(spec_satisfaction_association())


# ───────────── 关联分析 3：协同评论关联 ─────────────
@analysis_bp.route('/association3')
@login_required
def association3():
    return render_template('analysis/association3.html')


@analysis_bp.route('/api/association3')
@login_required
def api_association3():
    return jsonify(copurchase_association())
