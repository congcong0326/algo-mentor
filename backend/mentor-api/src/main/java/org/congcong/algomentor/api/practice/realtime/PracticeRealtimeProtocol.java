package org.congcong.algomentor.api.practice.realtime;

/** Practice Chat Redis Stream key 与 envelope 的稳定协议常量。 */
public final class PracticeRealtimeProtocol {

  public static final String STREAM_KEY_PREFIX = "agent:realtime:run:";
  public static final String STREAM_KEY_SUFFIX = ":events";
  public static final String ENVELOPE_VERSION_FIELD = "version";
  public static final String ENVELOPE_EVENT_NAME_FIELD = "eventName";
  public static final String ENVELOPE_DATA_FIELD = "data";
  /** 仅包含公开白名单 DTO 和连续 Stream ID 的 realtime envelope 版本。 */
  public static final int ENVELOPE_VERSION = 2;
  public static final String INITIAL_AFTER = "0-0";
  /** 新建 Practice Chat run 返回给浏览器的连续事件协议版本。 */
  public static final int REALTIME_PROTOCOL_VERSION = 2;
  /** 旧 run 或不带版本的控制面响应使用 PostgreSQL 回读收束。 */
  public static final int LEGACY_REALTIME_PROTOCOL_VERSION = 1;
  /** Redis 显式 Stream ID 的固定 generation，sequence 仅使用首段。 */
  public static final int STREAM_ID_GENERATION = 0;

  private PracticeRealtimeProtocol() {
  }

  public static String streamKey(String runUuid) {
    if (runUuid == null || runUuid.isBlank()) {
      throw new IllegalArgumentException("Practice realtime run uuid must not be blank");
    }
    return STREAM_KEY_PREFIX + runUuid + STREAM_KEY_SUFFIX;
  }
}
