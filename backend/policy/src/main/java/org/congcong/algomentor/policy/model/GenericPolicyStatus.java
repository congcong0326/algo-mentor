package org.congcong.algomentor.policy.model;

/** 策略生命周期状态；只有 ENABLED 进入运行时快照。 */
public enum GenericPolicyStatus {
  ENABLED,
  DISABLED,
  DELETED
}
