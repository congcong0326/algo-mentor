package org.congcong.algomentor.mentor.application.profile.claim.model;

/** 文档投影版本的专用类型，不能作为 claim hash 或更新快照使用。 */
public record LearnerMemoryDocumentRevision(String value) {

  public LearnerMemoryDocumentRevision {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("document revision 不能为空。");
    }
    value = value.trim();
  }
}
