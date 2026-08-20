package org.congcong.algomentor.api.learningplan.realtime;

/** 草案修订 Redis Stream 与 SSE 的稳定公开协议常量。 */
public final class LearningPlanDraftRevisionRealtimeProtocol {

  public static final String STREAM_KEY_PREFIX = "learning-plan:revision:";
  public static final String STREAM_KEY_SUFFIX = ":events";
  public static final String STREAM_FIELD_ENVELOPE = "envelope";
  public static final String ENVELOPE_VERSION_FIELD = "version";
  public static final String ENVELOPE_EVENT_NAME_FIELD = "eventName";
  public static final String ENVELOPE_DATA_FIELD = "data";
  public static final int ENVELOPE_VERSION = 1;
  public static final int REALTIME_PROTOCOL_VERSION = 1;
  public static final String INITIAL_AFTER = "0-0";
  public static final String WORK_START = "work_start";
  public static final String WORK_PROGRESS = "work_progress";
  public static final String REVISION_COMPLETED = "revision_completed";
  public static final String REVISION_FAILED = "revision_failed";
  public static final String REVISION_SUPERSEDED = "revision_superseded";
  public static final String DATA_DRAFT_ID = "draftId";
  public static final String DATA_REVISION_ID = "revisionId";
  public static final String DATA_MESSAGE = "message";
  public static final String DATA_CODE = "code";
  public static final String CURSOR_INVALID_CODE = "LEARNING_PLAN_DRAFT_REVISION_CURSOR_INVALID";
  public static final String REALTIME_UNAVAILABLE_CODE = "LEARNING_PLAN_DRAFT_REVISION_REALTIME_UNAVAILABLE";

  private LearningPlanDraftRevisionRealtimeProtocol() {
  }

  public static String streamKey(long revisionId) {
    if (revisionId < 1) {
      throw new IllegalArgumentException("Learning plan draft revision id must be positive");
    }
    return STREAM_KEY_PREFIX + revisionId + STREAM_KEY_SUFFIX;
  }
}
