-- A payment that is held by sanctions screening has NOT been executed.
-- Until it is released it must carry no executed_at, so it never enters an AML rule window.

ALTER TABLE txn ADD COLUMN status VARCHAR(32) NOT NULL DEFAULT 'RELEASED';
ALTER TABLE txn ALTER COLUMN executed_at DROP NOT NULL;

CREATE INDEX idx_txn_status ON txn (status);
