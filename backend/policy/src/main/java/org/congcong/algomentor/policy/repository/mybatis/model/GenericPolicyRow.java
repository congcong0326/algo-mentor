package org.congcong.algomentor.policy.repository.mybatis.model;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import org.congcong.algomentor.policy.model.GenericPolicy;
import org.congcong.algomentor.policy.model.GenericPolicyStatus;
import org.congcong.algomentor.policy.model.PolicySubjectRange;

/**
 * MyBatis 行模型，保留 JSONB 的 JsonNode 表示。
 *
 * <p>id 保持可写，供 PostgreSQL generated key 在 insert 后回填。</p>
 */
public final class GenericPolicyRow {

  private Long id;
  private final String typeCode;
  private final String name;
  private final String description;
  private final String status;
  private final int priority;
  private final JsonNode subjectRange;
  private final JsonNode content;
  private final long version;
  private final long createdBy;
  private final Instant createdAt;
  private final long updatedBy;
  private final Instant updatedAt;

  public GenericPolicyRow(
      Long id,
      String typeCode,
      String name,
      String description,
      String status,
      int priority,
      JsonNode subjectRange,
      JsonNode content,
      long version,
      long createdBy,
      Instant createdAt,
      long updatedBy,
      Instant updatedAt
  ) {
    this.id = id;
    this.typeCode = typeCode;
    this.name = name;
    this.description = description;
    this.status = status;
    this.priority = priority;
    this.subjectRange = subjectRange;
    this.content = content;
    this.version = version;
    this.createdBy = createdBy;
    this.createdAt = createdAt;
    this.updatedBy = updatedBy;
    this.updatedAt = updatedAt;
  }

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getTypeCode() {
    return typeCode;
  }

  public String getName() {
    return name;
  }

  public String getDescription() {
    return description;
  }

  public String getStatus() {
    return status;
  }

  public int getPriority() {
    return priority;
  }

  public JsonNode getSubjectRange() {
    return subjectRange;
  }

  public JsonNode getContent() {
    return content;
  }

  public long getVersion() {
    return version;
  }

  public long getCreatedBy() {
    return createdBy;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public long getUpdatedBy() {
    return updatedBy;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public GenericPolicy toDomain(PolicySubjectRange range) {
    if (id == null) {
      throw new IllegalStateException("generic policy row id must be populated");
    }
    return new GenericPolicy(
        id,
        typeCode,
        name,
        description,
        GenericPolicyStatus.valueOf(status),
        priority,
        range,
        content,
        version,
        createdBy,
        createdAt,
        updatedBy,
        updatedAt);
  }
}
