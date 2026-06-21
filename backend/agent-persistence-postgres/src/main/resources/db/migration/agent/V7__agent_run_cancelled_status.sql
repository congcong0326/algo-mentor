-- Agent run 取消状态由应用层写入；历史表未声明状态 CHECK，这里保留迁移节点用于版本追踪。
COMMENT ON COLUMN agent_run.status IS 'Agent run status: running, succeeded, failed, cancelled.';
