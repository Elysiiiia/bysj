import os

BASE_DIR = os.path.dirname(os.path.abspath(__file__))

class Config:
    SECRET_KEY = 'phoneRecommendSystem2024SecretKey@#$'
    DATABASE = os.path.join(BASE_DIR, 'phone_recommend.db')
    PER_PAGE = 8
    DEBUG = True
    DATASET_DIR = os.path.join(BASE_DIR, '数据集')
