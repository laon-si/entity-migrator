CREATE TABLE IF NOT EXISTS customer (
  id BIGINT PRIMARY KEY,
  username VARCHAR(100),
  email VARCHAR(200),
  created_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS product (
  id BIGINT PRIMARY KEY,
  name VARCHAR(150),
  price DECIMAL(13,2),
  stock INT
);