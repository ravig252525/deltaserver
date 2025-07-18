CREATE TABLE IF NOT EXISTS watchlist (
  id SERIAL PRIMARY KEY,
  symbol VARCHAR(50) NOT NULL,
  productid BIGINT NOT NULL,
  description TEXT,
  userid VARCHAR(50) NOT NULL
);

