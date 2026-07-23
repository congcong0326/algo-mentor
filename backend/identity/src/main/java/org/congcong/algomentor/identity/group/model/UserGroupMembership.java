package org.congcong.algomentor.identity.group.model;

import java.time.Instant;

public record UserGroupMembership(
    long userId,
    long groupId,
    Instant joinedAt,
    Instant expiresAt,
    Instant createdAt,
    Instant updatedAt
) {
}
