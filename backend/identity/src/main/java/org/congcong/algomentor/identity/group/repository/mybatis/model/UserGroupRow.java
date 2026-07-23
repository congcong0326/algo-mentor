package org.congcong.algomentor.identity.group.repository.mybatis.model;

import java.time.Instant;
import org.congcong.algomentor.identity.group.model.UserGroup;
import org.congcong.algomentor.identity.group.model.UserGroupStatus;

public final class UserGroupRow {

  private Long id;
  private final String code;
  private final String name;
  private final String description;
  private final String status;
  private final long activeMemberCount;
  private final Instant createdAt;
  private final Instant updatedAt;
  private final Instant deletedAt;
  private final Long deletedBy;

  public UserGroupRow(
      Long id,
      String code,
      String name,
      String description,
      String status,
      long activeMemberCount,
      Instant createdAt,
      Instant updatedAt,
      Instant deletedAt,
      Long deletedBy
  ) {
    this.id = id;
    this.code = code;
    this.name = name;
    this.description = description;
    this.status = status;
    this.activeMemberCount = activeMemberCount;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
    this.deletedAt = deletedAt;
    this.deletedBy = deletedBy;
  }

  public Long id() { return id; }
  public void setId(Long id) { this.id = id; }
  public String code() { return code; }
  public String name() { return name; }
  public String description() { return description; }
  public String status() { return status; }
  public long activeMemberCount() { return activeMemberCount; }
  public Instant createdAt() { return createdAt; }
  public Instant updatedAt() { return updatedAt; }
  public Instant deletedAt() { return deletedAt; }
  public Long deletedBy() { return deletedBy; }

  public UserGroup toDomain() {
    return new UserGroup(
        id,
        code,
        name,
        description,
        UserGroupStatus.valueOf(status),
        activeMemberCount,
        createdAt,
        updatedAt,
        deletedAt,
        deletedBy);
  }
}
