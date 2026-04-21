from flask import Flask, redirect, url_for
from config import Config
from models.database import init_db
from routes import register_varm_middleware
from routes.auth import auth_bp
from routes.frontend import frontend_bp
from routes.admin import admin_bp
from routes.analysis import analysis_bp

app = Flask(__name__)
app.config.from_object(Config)
app.secret_key = Config.SECRET_KEY

register_varm_middleware(app)


app.register_blueprint(auth_bp)
app.register_blueprint(frontend_bp)
app.register_blueprint(admin_bp)
app.register_blueprint(analysis_bp)


@app.route('/')
def root():
    return redirect(url_for('frontend.index'))


# 模板过滤器：情感标签中文化
@app.template_filter('sentiment_zh')
def sentiment_zh_filter(label):
    return {'positive': '正面', 'neutral': '中性', 'negative': '负面'}.get(label, '未知')


@app.template_filter('sentiment_color')
def sentiment_color_filter(label):
    return {'positive': 'success', 'neutral': 'warning', 'negative': 'danger'}.get(label, 'secondary')


if __name__ == '__main__':
    init_db()
    app.run(debug=True, port=5000)
