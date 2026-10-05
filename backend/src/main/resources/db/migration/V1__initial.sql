CREATE EXTENSION IF NOT EXISTS pgcrypto;
CREATE TABLE users (id uuid PRIMARY KEY DEFAULT gen_random_uuid(), email varchar(320) NOT NULL UNIQUE, password_hash text NOT NULL, role varchar(16) NOT NULL CHECK (role IN ('ADMIN','USER')), created_at timestamptz NOT NULL DEFAULT now());
INSERT INTO users(email,password_hash,role) VALUES ('demo-user','disabled-login','USER');
CREATE TABLE payments (id uuid PRIMARY KEY, client_id varchar(320) NOT NULL REFERENCES users(email) DEFERRABLE INITIALLY DEFERRED, idempotency_key varchar(128) NOT NULL, request_hash varchar(64) NOT NULL, amount numeric(19,4) NOT NULL CHECK (amount > 0), currency varchar(3) NOT NULL, description varchar(240) NOT NULL, status varchar(16) NOT NULL CHECK (status IN ('CREATED','PENDING','PROCESSING','APPROVED','DECLINED','FAILED','REFUNDED')), created_at timestamptz NOT NULL, updated_at timestamptz NOT NULL, UNIQUE(client_id,idempotency_key));
CREATE INDEX payments_client_created_idx ON payments(client_id,created_at DESC);
CREATE TABLE payment_attempts (id uuid PRIMARY KEY DEFAULT gen_random_uuid(), payment_id uuid NOT NULL REFERENCES payments(id), attempt_no integer NOT NULL, gateway_key varchar(500) NOT NULL UNIQUE, outcome varchar(32), created_at timestamptz NOT NULL DEFAULT now(), UNIQUE(payment_id,attempt_no));
CREATE TABLE idempotency_records (id uuid PRIMARY KEY DEFAULT gen_random_uuid(), client_id varchar(320) NOT NULL, idempotency_key varchar(128) NOT NULL, request_hash varchar(64) NOT NULL, payment_id uuid REFERENCES payments(id), expires_at timestamptz NOT NULL, created_at timestamptz NOT NULL DEFAULT now(), UNIQUE(client_id,idempotency_key));
CREATE TABLE outbox_events (id uuid PRIMARY KEY, aggregate_id uuid NOT NULL, event_type varchar(100) NOT NULL, payload text NOT NULL, created_at timestamptz NOT NULL, published_at timestamptz);
CREATE INDEX outbox_pending_idx ON outbox_events(created_at) WHERE published_at IS NULL;
CREATE TABLE processed_messages (message_id uuid PRIMARY KEY, processed_at timestamptz NOT NULL DEFAULT now());
CREATE TABLE gateway_charges (gateway_key varchar(500) PRIMARY KEY, payment_id uuid NOT NULL UNIQUE REFERENCES payments(id), result varchar(16) NOT NULL CHECK (result IN ('APPROVED','DECLINED')), created_at timestamptz NOT NULL DEFAULT now());
CREATE TABLE refunds (id uuid PRIMARY KEY DEFAULT gen_random_uuid(), payment_id uuid NOT NULL UNIQUE REFERENCES payments(id), amount numeric(19,4) NOT NULL CHECK(amount > 0), status varchar(16) NOT NULL, created_at timestamptz NOT NULL DEFAULT now());
CREATE TABLE audit_logs (id uuid PRIMARY KEY DEFAULT gen_random_uuid(), actor varchar(320) NOT NULL, action varchar(100) NOT NULL, entity_id uuid, correlation_id varchar(128), created_at timestamptz NOT NULL DEFAULT now());


