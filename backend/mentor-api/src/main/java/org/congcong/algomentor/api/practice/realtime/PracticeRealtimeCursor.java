package org.congcong.algomentor.api.practice.realtime;

import java.util.regex.Pattern;

/** Redis Stream cursor 的输入校验与 after 语义转换。 */
public final class PracticeRealtimeCursor {

  private static final Pattern REDIS_STREAM_ID = Pattern.compile("(?:0|[1-9][0-9]*)-(?:0|[1-9][0-9]*)");
  private static final Pattern V2_STREAM_ID = Pattern.compile("(?:0|[1-9][0-9]*)-0");

  private PracticeRealtimeCursor() {
  }

  public static String normalizeAfter(String after) {
    if (after == null || after.isBlank()) {
      return PracticeRealtimeProtocol.INITIAL_AFTER;
    }
    String value = after.trim();
    if (!REDIS_STREAM_ID.matcher(value).matches()) {
      throw new PracticeRealtimeCursorInvalidException("Practice realtime after cursor is invalid");
    }
    return value;
  }

  /** Redis XREAD 从给定 entry 的下一条返回，正好匹配 API 的严格 after 语义。 */
  public static String xreadOffset(String after) {
    return normalizeAfter(after);
  }

  /** v2 浏览器协议只允许初始游标或连续 sequence 产生的 {@code N-0}。 */
  public static String normalizeV2After(String after) {
    String value = normalizeAfter(after);
    if (!V2_STREAM_ID.matcher(value).matches()) {
      throw new PracticeRealtimeCursorInvalidException("Practice realtime v2 after cursor is invalid");
    }
    return value;
  }
}
