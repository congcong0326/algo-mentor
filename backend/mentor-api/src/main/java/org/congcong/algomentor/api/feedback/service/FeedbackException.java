package org.congcong.algomentor.api.feedback.service;

public class FeedbackException extends RuntimeException {

  private final FeedbackErrorCode code;

  public FeedbackException(FeedbackErrorCode code, String message) {
    super(message);
    this.code = code;
  }

  public FeedbackErrorCode code() {
    return code;
  }
}
