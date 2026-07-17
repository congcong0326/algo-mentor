package org.congcong.algomentor.auth.betaaccess.repository.mybatis.model;

import org.congcong.algomentor.auth.betaaccess.model.BetaAccessUserMembership;

public record BetaAccessUserMembershipRow(long userId, Long allowedEmailId) {
  public BetaAccessUserMembership toDomain() {
    return new BetaAccessUserMembership(userId, allowedEmailId != null, allowedEmailId);
  }
}
