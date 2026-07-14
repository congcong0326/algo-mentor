package org.congcong.algomentor.common.admin.audit;

public interface AdminOperationAuditRecorder {

  void record(AdminOperationAuditEvent event);
}
