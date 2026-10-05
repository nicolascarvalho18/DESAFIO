CREATE TABLE dev_locks (lock_name VARCHAR(64) PRIMARY KEY);
INSERT INTO dev_locks(lock_name) VALUES ('payment-idempotency');
