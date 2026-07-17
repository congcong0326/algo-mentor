package org.congcong.algomentor.api.feedback.service;

public class FeedbackUnauthenticatedException extends RuntimeException {
  public FeedbackUnauthenticatedException() {
    super("当前请求未登录或无法解析当前用户。");
  }
}
