package org.congcong.algomentor.policy.service;

/** 仅用于保护通用底座资源占用的固定上限，不表达业务内容语义。 */
public final class GenericPolicyConstraints {

  public static final int MAX_POLICIES_PER_TYPE = 100;
  public static final int MAX_NAME_LENGTH = 120;
  public static final int MAX_DESCRIPTION_LENGTH = 500;
  public static final int MAX_TYPE_CODE_LENGTH = 64;
  public static final int MAX_SUBJECTS_PER_POLICY = 1_000;
  public static final int MAX_CONTENT_BYTES = 262_144;

  private GenericPolicyConstraints() {
  }
}
