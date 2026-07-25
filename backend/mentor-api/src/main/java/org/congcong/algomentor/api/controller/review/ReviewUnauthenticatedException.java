package org.congcong.algomentor.api.controller.review;

public class ReviewUnauthenticatedException extends RuntimeException {

  public ReviewUnauthenticatedException(String message) {
    super(message);
  }
}
