package org.congcong.algomentor.identity.group.model;

import java.time.Instant;
import org.congcong.algomentor.identity.model.AuthUserStatus;

public record UserGroupMember(
    long userId,
    String email,
    String displayName,
    String avatarUrl,
    AuthUserStatus status,
    Instant joinedAt,
    Instant expiresAt
) {
}
