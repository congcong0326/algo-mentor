package org.congcong.algomentor.mentor.application.review;

public class MistakeReviewException extends RuntimeException {

  private final String code;

  public MistakeReviewException(String code, String message) {
    super(message);
    this.code = code;
  }

  public String code() {
    return code;
  }
}
