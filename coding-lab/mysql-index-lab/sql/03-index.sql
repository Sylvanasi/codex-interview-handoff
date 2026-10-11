USE interview_lab;

-- 列顺序的依据：dept_id、status 是等值条件放前面，sign_date 是范围 + 排序放最后
CREATE INDEX idx_ledger_dept_status_date ON contract_ledger (dept_id, status, sign_date);

ANALYZE TABLE contract_ledger;

SHOW INDEX FROM contract_ledger;
