package org.congcong.algomentor.auth.session.admin.repository.mybatis.model;

import org.congcong.algomentor.auth.session.admin.model.AuthSessionAdminSummary;

public record AuthSessionAdminSummaryRow(
    long validSessionCount,
    long activeSessionCount,
    long validUserCount
) {

  /**
   * 适配 MyBatis/JDBC 对 PostgreSQL 聚合结果使用 {@link Long} 的构造器解析。
   */
  public AuthSessionAdminSummaryRow(
      Long validSessionCount,
      Long activeSessionCount,
      Long validUserCount
  ) {
    this(
        validSessionCount.longValue(),
        activeSessionCount.longValue(),
        validUserCount.longValue());
  }

  public AuthSessionAdminSummary toDomain() {
    return new AuthSessionAdminSummary(validSessionCount, activeSessionCount, validUserCount);
  }
}
