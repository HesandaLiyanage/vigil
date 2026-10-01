-- users
CREATE USER 'repl'@'%' IDENTIFIED BY 'replpw';
GRANT REPLICATION SLAVE ON *.* TO 'repl'@'%';

CREATE USER 'vigil_monitor'@'%' IDENTIFIED BY 'monpw';
GRANT PROCESS, REPLICATION CLIENT, SELECT ON *.* TO 'vigil_monitor'@'%';

CREATE USER 'app_rw'@'%' IDENTIFIED BY 'apppw';

-- metadata schema (heartbeat + canary)
CREATE DATABASE IF NOT EXISTS vigil_meta;
CREATE TABLE vigil_meta.heartbeat (
  id TINYINT PRIMARY KEY,
  ts DATETIME(6) NOT NULL
);
CREATE TABLE vigil_meta.canary (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  token VARCHAR(64) NOT NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
);
GRANT INSERT, UPDATE ON vigil_meta.* TO 'vigil_monitor'@'%';

-- demo data
CREATE DATABASE vigil_demo;
CREATE TABLE vigil_demo.customers (
  id INT AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(100) NOT NULL,
  email VARCHAR(150) NOT NULL
);
CREATE TABLE vigil_demo.orders (
  id INT AUTO_INCREMENT PRIMARY KEY,
  customer_id INT NOT NULL,
  total_cents INT NOT NULL,
  status VARCHAR(16) NOT NULL,
  created_at DATETIME NOT NULL
  -- deliberately NO index on total_cents / created_at so load_gen.sh produces slow queries
);
GRANT SELECT, INSERT, UPDATE, DELETE ON vigil_demo.* TO 'app_rw'@'%';

SET SESSION cte_max_recursion_depth = 200000;
INSERT INTO vigil_demo.customers (name, email)
  WITH RECURSIVE seq AS (SELECT 1 AS n UNION ALL SELECT n + 1 FROM seq WHERE n < 1000)
  SELECT CONCAT('customer-', n), CONCAT('c', n, '@example.com') FROM seq;
INSERT INTO vigil_demo.orders (customer_id, total_cents, status, created_at)
  WITH RECURSIVE seq AS (SELECT 1 AS n UNION ALL SELECT n + 1 FROM seq WHERE n < 200000)
  SELECT 1 + (n % 1000), (n * 37) % 100000, ELT(1 + n % 3, 'NEW', 'PAID', 'SHIPPED'),
         NOW() - INTERVAL (n % 365) DAY FROM seq;
