package org.congcong.algomentor.mentor.application.learningplan;

public class LearningPlanException extends RuntimeException {

  private final String code;

  public LearningPlanException(String code, String message) {
    super(message);
    this.code = code;
  }

  public String code() {
    return code;
  }
}
