package org.congcong.algomentor.identity.controller.group.model;

import java.time.Instant;
import java.util.List;

public record AdminUserGroupMemberAddRequest(
    List<Long> userIds,
    Instant expiresAt
) {
}
