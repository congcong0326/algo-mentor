package org.congcong.algomentor.identity.group.model;

import java.time.Instant;

public record UserGroupMembershipSummary(
    long userId,
    long groupId,
    String code,
    String name,
    Instant joinedAt,
    Instant expiresAt
) {
}
