package org.congcong.algomentor.auth.session.admin.repository.mybatis;

import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.congcong.algomentor.auth.session.admin.model.AuthSessionAdminQuery;
import org.congcong.algomentor.auth.session.admin.repository.mybatis.model.AuthSessionAdminRow;
import org.congcong.algomentor.auth.session.admin.repository.mybatis.model.AuthSessionAdminSummaryRow;

public interface AuthSessionAdminMapper {

  List<AuthSessionAdminRow> findPage(
      @Param("query") AuthSessionAdminQuery query,
      @Param("nowEpochMillis") long nowEpochMillis,
      @Param("activeSinceEpochMillis") long activeSinceEpochMillis);

  long count(
      @Param("query") AuthSessionAdminQuery query,
      @Param("nowEpochMillis") long nowEpochMillis,
      @Param("activeSinceEpochMillis") long activeSinceEpochMillis);

  AuthSessionAdminSummaryRow summary(
      @Param("nowEpochMillis") long nowEpochMillis,
      @Param("activeSinceEpochMillis") long activeSinceEpochMillis);

  AuthSessionAdminRow findValidBySessionRef(
      @Param("sessionRef") String sessionRef,
      @Param("nowEpochMillis") long nowEpochMillis);
}
