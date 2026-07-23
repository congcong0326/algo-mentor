package org.congcong.algomentor.auth.session.admin.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.congcong.algomentor.auth.session.admin.model.AuthSessionAdminQuery;
import org.congcong.algomentor.auth.session.admin.model.AuthSessionAdminRecord;
import org.congcong.algomentor.auth.session.admin.model.AuthSessionAdminSummary;

public interface AuthSessionAdminRepository {

  List<AuthSessionAdminRecord> findPage(
      AuthSessionAdminQuery query,
      Instant checkedAt,
      Instant activeSince);

  long count(AuthSessionAdminQuery query, Instant checkedAt, Instant activeSince);

  AuthSessionAdminSummary summary(Instant checkedAt, Instant activeSince);

  Optional<AuthSessionAdminRecord> findValidBySessionRef(String sessionRef, Instant checkedAt);
}
