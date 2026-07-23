package org.congcong.algomentor.identity.group.model;

import java.time.Instant;

public record UserGroup(
    Long id,
    String code,
    String name,
    String description,
    UserGroupStatus status,
    long activeMemberCount,
    Instant createdAt,
    Instant updatedAt,
    Instant deletedAt,
    Long deletedBy
) {

  public UserGroup {
    if (id != null && id < 1) {
      throw new IllegalArgumentException("id must be positive when present.");
    }
    if (activeMemberCount < 0) {
      throw new IllegalArgumentException("activeMemberCount must not be negative.");
    }
  }
}
