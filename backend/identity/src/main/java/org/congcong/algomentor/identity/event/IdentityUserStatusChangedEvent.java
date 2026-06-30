package org.congcong.algomentor.identity.event;

import java.time.Instant;
import org.congcong.algomentor.identity.model.AuthUserStatus;

public record IdentityUserStatusChangedEvent(
    long userId,
    AuthUserStatus previousStatus,
    AuthUserStatus currentStatus,
    long operatorUserId,
    Instant occurredAt
) {
}
