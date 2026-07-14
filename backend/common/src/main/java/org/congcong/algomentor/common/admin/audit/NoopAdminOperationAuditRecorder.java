package org.congcong.algomentor.common.admin.audit;

public class NoopAdminOperationAuditRecorder implements AdminOperationAuditRecorder {

  @Override
  public void record(AdminOperationAuditEvent event) {
    // Intentionally empty for non-database and isolated module contexts.
  }
}
