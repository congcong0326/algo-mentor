package org.congcong.algomentor.api.feedback.service;

/** 阶段三公开 run 查询端口接入前的安全边界。 */
@FunctionalInterface
public interface FeedbackRunOwnershipVerifier {
  boolean belongsToUser(String runId, long userId);
}
