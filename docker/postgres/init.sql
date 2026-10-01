CREATE EXTENSION IF NOT EXISTS pg_stat_statements;

CREATE SCHEMA vigil_meta;
CREATE TABLE vigil_meta.canary (
  id BIGSERIAL PRIMARY KEY,
  token TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE public.customers (id SERIAL PRIMARY KEY, name TEXT NOT NULL, email TEXT NOT NULL);
CREATE TABLE public.orders (
  id SERIAL PRIMARY KEY, customer_id INT NOT NULL, total_cents INT NOT NULL,
  status TEXT NOT NULL, created_at TIMESTAMPTZ NOT NULL
);
INSERT INTO public.customers (name, email)
  SELECT 'customer-' || g, 'c' || g || '@example.com' FROM generate_series(1, 1000) g;
INSERT INTO public.orders (customer_id, total_cents, status, created_at)
  SELECT 1 + (g % 1000), (g * 37) % 100000,
         (ARRAY['NEW','PAID','SHIPPED'])[1 + g % 3], now() - (g % 365) * interval '1 day'
  FROM generate_series(1, 200000) g;

CREATE ROLE vigil_monitor LOGIN PASSWORD 'monpw';
GRANT pg_monitor TO vigil_monitor;
GRANT CONNECT ON DATABASE vigil_demo TO vigil_monitor;
GRANT USAGE ON SCHEMA public, vigil_meta TO vigil_monitor;
GRANT SELECT ON ALL TABLES IN SCHEMA public TO vigil_monitor;
GRANT SELECT, INSERT ON vigil_meta.canary TO vigil_monitor;
GRANT USAGE ON SEQUENCE vigil_meta.canary_id_seq TO vigil_monitor;

CREATE ROLE app_rw LOGIN PASSWORD 'apppw';
GRANT USAGE ON SCHEMA public TO app_rw;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO app_rw;
