CREATE TABLE IF NOT EXISTS watchlist (
  id SERIAL PRIMARY KEY,
  symbol VARCHAR(50) NOT NULL,
  product_id BIGINT NOT NULL,
  description TEXT,
  user_id VARCHAR(50) NOT NULL
);
