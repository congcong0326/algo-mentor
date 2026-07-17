package org.congcong.algomentor.auth.betaaccess.repository.mybatis;

import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.congcong.algomentor.auth.betaaccess.repository.mybatis.model.BetaAccessSettingsRow;
import org.congcong.algomentor.auth.betaaccess.repository.mybatis.model.BetaAllowedEmailRow;
import org.congcong.algomentor.auth.betaaccess.repository.mybatis.model.BetaAccessOverviewSummaryRow;
import org.congcong.algomentor.auth.betaaccess.repository.mybatis.model.BetaAccessUserMembershipRow;

public interface BetaAccessMapper {

  BetaAccessSettingsRow findSettings();

  int updateSettings(
      @Param("emailAllowlistEnabled") boolean emailAllowlistEnabled,
      @Param("updatedBy") long updatedBy,
      @Param("updatedAt") Instant updatedAt);

  boolean isAllowedEmail(@Param("emailNormalized") String emailNormalized);

  BetaAllowedEmailRow findAllowedEmailById(@Param("allowedEmailId") long allowedEmailId);

  BetaAllowedEmailRow findAllowedEmailByNormalized(@Param("emailNormalized") String emailNormalized);

  Long insertAllowedEmail(
      @Param("email") String email,
      @Param("emailNormalized") String emailNormalized,
      @Param("createdBy") long createdBy,
      @Param("createdAt") Instant createdAt);

  int deleteAllowedEmail(@Param("allowedEmailId") long allowedEmailId);

  List<BetaAllowedEmailRow> findAllowedEmails(
      @Param("keyword") String keyword,
      @Param("limit") int limit,
      @Param("offset") int offset);

  long countAllowedEmails(@Param("keyword") String keyword);

  BetaAccessOverviewSummaryRow overviewSummary();

  BetaAccessUserMembershipRow userMembership(@Param("userId") long userId);
}
