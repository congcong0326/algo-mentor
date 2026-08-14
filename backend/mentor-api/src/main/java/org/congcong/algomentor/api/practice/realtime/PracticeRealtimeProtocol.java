package org.congcong.algomentor.api.practice.realtime;

/** Practice Chat Redis Stream key 与 envelope 的稳定协议常量。 */
public final class PracticeRealtimeProtocol {

  public static final String STREAM_KEY_PREFIX = "agent:realtime:run:";
  public static final String STREAM_KEY_SUFFIX = ":events";
  public static final String ENVELOPE_VERSION_FIELD = "version";
  public static final String ENVELOPE_EVENT_NAME_FIELD = "eventName";
  public static final String ENVELOPE_DATA_FIELD = "data";
  public static final int ENVELOPE_VERSION = 1;
  public static final String INITIAL_AFTER = "0-0";

  private PracticeRealtimeProtocol() {
  }

  public static String streamKey(String runUuid) {
    if (runUuid == null || runUuid.isBlank()) {
      throw new IllegalArgumentException("Practice realtime run uuid must not be blank");
    }
    return STREAM_KEY_PREFIX + runUuid + STREAM_KEY_SUFFIX;
  }
}
