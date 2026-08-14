package org.congcong.algomentor.api.practice.realtime;

/** Practice Chat Redis Stream cursor 参数格式不合法。 */
public final class PracticeRealtimeCursorInvalidException extends IllegalArgumentException {

  public PracticeRealtimeCursorInvalidException(String message) {
    super(message);
  }
}
