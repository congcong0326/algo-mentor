package org.congcong.algomentor.api.learningplan.realtime;

/** 客户端提供的首次草案事件游标不符合公开连续 ID 协议。 */
public final class LearningPlanGenerationRealtimeCursorInvalidException extends RuntimeException {

  public LearningPlanGenerationRealtimeCursorInvalidException(String message) {
    super(message);
  }
}
