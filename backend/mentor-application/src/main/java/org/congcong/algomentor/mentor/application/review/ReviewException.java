package org.congcong.algomentor.mentor.application.review;

public class ReviewException extends RuntimeException {

  private final String code;

  public ReviewException(String code, String message) {
    super(message);
    this.code = code;
  }

  public String code() {
    return code;
  }
}
