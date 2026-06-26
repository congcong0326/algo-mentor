package org.congcong.algomentor.mentor.application.learningplan;

public class LearningPlanException extends RuntimeException {

  private final String code;
  private final String messageKey;

  public LearningPlanException(String code, String message) {
    this(code, null, message);
  }

  public LearningPlanException(String code, String messageKey, String message) {
    super(message);
    this.code = code;
    this.messageKey = messageKey;
  }

  public String code() {
    return code;
  }

  public String messageKey() {
    return messageKey;
  }
}
