package org.congcong.algomentor.auth.betaaccess.repository.mybatis;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.congcong.algomentor.auth.betaaccess.model.BetaAccessSettings;
import org.congcong.algomentor.auth.betaaccess.model.BetaAllowedEmail;
import org.congcong.algomentor.auth.betaaccess.repository.BetaAccessRepository;
import org.congcong.algomentor.auth.betaaccess.repository.mybatis.model.BetaAccessSettingsRow;
import org.congcong.algomentor.auth.betaaccess.repository.mybatis.model.BetaAllowedEmailRow;

public class MyBatisBetaAccessRepository implements BetaAccessRepository {

  private final BetaAccessMapper mapper;

  public MyBatisBetaAccessRepository(BetaAccessMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public Optional<BetaAccessSettings> findSettings() {
    return Optional.ofNullable(mapper.findSettings()).map(BetaAccessSettingsRow::toDomain);
  }

  @Override
  public boolean updateSettings(boolean emailAllowlistEnabled, long updatedBy, Instant updatedAt) {
    return mapper.updateSettings(emailAllowlistEnabled, updatedBy, updatedAt) == 1;
  }

  @Override
  public boolean isAllowedEmail(String emailNormalized) {
    return mapper.isAllowedEmail(emailNormalized);
  }

  @Override
  public Optional<BetaAllowedEmail> findAllowedEmailById(long allowedEmailId) {
    return Optional.ofNullable(mapper.findAllowedEmailById(allowedEmailId)).map(BetaAllowedEmailRow::toDomain);
  }

  @Override
  public Optional<BetaAllowedEmail> findAllowedEmailByNormalized(String emailNormalized) {
    return Optional.ofNullable(mapper.findAllowedEmailByNormalized(emailNormalized))
        .map(BetaAllowedEmailRow::toDomain);
  }

  @Override
  public Optional<Long> insertAllowedEmail(
      String email,
      String emailNormalized,
      long createdBy,
      Instant createdAt
  ) {
    return Optional.ofNullable(mapper.insertAllowedEmail(email, emailNormalized, createdBy, createdAt));
  }

  @Override
  public boolean deleteAllowedEmail(long allowedEmailId) {
    return mapper.deleteAllowedEmail(allowedEmailId) == 1;
  }

  @Override
  public List<BetaAllowedEmail> findAllowedEmails(String keyword, int limit, int offset) {
    return mapper.findAllowedEmails(keyword, limit, offset).stream()
        .map(BetaAllowedEmailRow::toDomain)
        .toList();
  }

  @Override
  public long countAllowedEmails(String keyword) {
    return mapper.countAllowedEmails(keyword);
  }
}
