package org.congcong.algomentor.mentor.application.profile;

/** 模型的最小画像判断，不含服务端身份或版本字段。 */
public record ProfileUpdateDecision(ProfileUpdateAction action, String content, String reason) {
  public ProfileUpdateDecision {
    if (action == null || (action == ProfileUpdateAction.REPLACE && (content == null || content.isBlank()))) {
      throw new IllegalArgumentException("Invalid profile update decision");
    }
    content = content == null ? null : content.trim();
    reason = reason == null ? null : reason.trim();
  }
}
