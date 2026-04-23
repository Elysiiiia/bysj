CREATE TABLE IF NOT EXISTS users (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  username VARCHAR(64) UNIQUE NOT NULL,
  password VARCHAR(128) NOT NULL,
  role VARCHAR(20) NOT NULL DEFAULT 'user',
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS phones (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  brand VARCHAR(100),
  title VARCHAR(500),
  current_price DECIMAL(10,2),
  original_price DECIMAL(10,2),
  discount_price DECIMAL(10,2),
  sales VARCHAR(50),
  shop_name VARCHAR(200),
  image_url VARCHAR(1000),
  gov_subsidy VARCHAR(100),
  self_operated VARCHAR(100),
  product_id VARCHAR(100) UNIQUE,
  link_url VARCHAR(1000),
  sentiment_score DOUBLE DEFAULT 0.5,
  avg_rating DOUBLE DEFAULT 0,
  review_count INT DEFAULT 0,
  INDEX idx_phones_brand (brand),
  INDEX idx_phones_product_id (product_id)
);

CREATE TABLE IF NOT EXISTS comments (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  product_id VARCHAR(100),
  brand VARCHAR(100),
  title VARCHAR(500),
  nickname VARCHAR(100),
  rating DOUBLE,
  spec VARCHAR(300),
  comment_date VARCHAR(50),
  content TEXT,
  sentiment_score DOUBLE DEFAULT 0.5,
  sentiment_label VARCHAR(20) DEFAULT 'neutral',
  INDEX idx_comments_product_id (product_id),
  INDEX idx_comments_brand (brand),
  INDEX idx_comments_sentiment_label (sentiment_label)
);

CREATE TABLE IF NOT EXISTS user_behavior (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT,
  product_id VARCHAR(100),
  action VARCHAR(50),
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_behavior_user_id (user_id),
  INDEX idx_behavior_product_id (product_id)
);

INSERT INTO users (username, password, role)
VALUES ('Test', '123456', 'user')
ON DUPLICATE KEY UPDATE username = VALUES(username);

INSERT INTO users (username, password, role)
VALUES ('admin', 'admin123', 'admin')
ON DUPLICATE KEY UPDATE username = VALUES(username);
