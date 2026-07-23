package org.congcong.algomentor.identity.controller.model;

import java.time.Instant;

public record AdminUserGroupMembershipResponse(
    long id,
    String code,
    String name,
    Instant joinedAt,
    Instant expiresAt
) {
}
