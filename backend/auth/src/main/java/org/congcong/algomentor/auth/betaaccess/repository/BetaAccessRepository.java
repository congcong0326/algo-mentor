package org.congcong.algomentor.auth.betaaccess.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.congcong.algomentor.auth.betaaccess.model.BetaAccessSettings;
import org.congcong.algomentor.auth.betaaccess.model.BetaAllowedEmail;

public interface BetaAccessRepository {

  Optional<BetaAccessSettings> findSettings();

  boolean updateSettings(boolean emailAllowlistEnabled, long updatedBy, Instant updatedAt);

  boolean isAllowedEmail(String emailNormalized);

  Optional<BetaAllowedEmail> findAllowedEmailById(long allowedEmailId);

  Optional<BetaAllowedEmail> findAllowedEmailByNormalized(String emailNormalized);

  Optional<Long> insertAllowedEmail(
      String email,
      String emailNormalized,
      long createdBy,
      Instant createdAt);

  boolean deleteAllowedEmail(long allowedEmailId);

  List<BetaAllowedEmail> findAllowedEmails(String keyword, int limit, int offset);

  long countAllowedEmails(String keyword);
}
