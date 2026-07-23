package org.congcong.algomentor.auth.session.admin.repository.mybatis;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.congcong.algomentor.auth.session.admin.model.AuthSessionAdminQuery;
import org.congcong.algomentor.auth.session.admin.model.AuthSessionAdminRecord;
import org.congcong.algomentor.auth.session.admin.model.AuthSessionAdminSummary;
import org.congcong.algomentor.auth.session.admin.repository.AuthSessionAdminRepository;

public class MyBatisAuthSessionAdminRepository implements AuthSessionAdminRepository {

  private final AuthSessionAdminMapper mapper;

  public MyBatisAuthSessionAdminRepository(AuthSessionAdminMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public List<AuthSessionAdminRecord> findPage(
      AuthSessionAdminQuery query,
      Instant checkedAt,
      Instant activeSince
  ) {
    return mapper.findPage(query, checkedAt.toEpochMilli(), activeSince.toEpochMilli()).stream()
        .map(row -> row.toDomain(activeSince.toEpochMilli()))
        .toList();
  }

  @Override
  public long count(AuthSessionAdminQuery query, Instant checkedAt, Instant activeSince) {
    return mapper.count(query, checkedAt.toEpochMilli(), activeSince.toEpochMilli());
  }

  @Override
  public AuthSessionAdminSummary summary(Instant checkedAt, Instant activeSince) {
    return mapper.summary(checkedAt.toEpochMilli(), activeSince.toEpochMilli()).toDomain();
  }

  @Override
  public Optional<AuthSessionAdminRecord> findValidBySessionRef(String sessionRef, Instant checkedAt) {
    return Optional.ofNullable(mapper.findValidBySessionRef(sessionRef, checkedAt.toEpochMilli()))
        .map(row -> row.toDomain(Long.MAX_VALUE));
  }
}
