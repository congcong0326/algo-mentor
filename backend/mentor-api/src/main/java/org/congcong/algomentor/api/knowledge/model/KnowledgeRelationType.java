package org.congcong.algomentor.api.knowledge.model;

/** 元数据关系键；方向为当前卡片指向目标卡片。 */
public enum KnowledgeRelationType {
  PREREQUISITES("prerequisites"),
  FOLLOWUPS("followups"),
  CONTRASTS("contrasts"),
  RELATED("related");
  private final String key;

  KnowledgeRelationType(String key) {
    this.key = key;
  }

  public String key() {
    return key;
  }

  public static KnowledgeRelationType parse(String key) {
    for (var value : values()) if (value.key.equals(key)) return value;
    throw new IllegalArgumentException("未知关系类型：" + key);
  }
}
