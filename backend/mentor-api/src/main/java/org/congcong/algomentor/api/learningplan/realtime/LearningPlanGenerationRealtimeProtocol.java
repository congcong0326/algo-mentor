package org.congcong.algomentor.api.learningplan.realtime;

/** 学习计划首次草案 Redis Stream 与 SSE 的稳定公开协议常量。 */
public final class LearningPlanGenerationRealtimeProtocol {

  public static final String STREAM_KEY_PREFIX = "learning-plan:generation:";
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
  public static final String WORK_TOOL_START = "work_tool_start";
  public static final String WORK_TOOL_END = "work_tool_end";
  public static final String DRAFT_COMPLETED = "draft_completed";
  public static final String DRAFT_FAILED = "draft_failed";
  public static final String DATA_DRAFT_ID = "draftId";
  public static final String DATA_MESSAGE = "message";
  public static final String DATA_TOOL_NAME = "toolName";
  public static final String DATA_CODE = "code";
  public static final String CURSOR_INVALID_CODE = "LEARNING_PLAN_GENERATION_CURSOR_INVALID";
  public static final String REALTIME_UNAVAILABLE_CODE = "LEARNING_PLAN_GENERATION_REALTIME_UNAVAILABLE";

  private LearningPlanGenerationRealtimeProtocol() {
  }

  public static String streamKey(long draftId) {
    if (draftId < 1) {
      throw new IllegalArgumentException("Learning plan generation draft id must be positive");
    }
    return STREAM_KEY_PREFIX + draftId + STREAM_KEY_SUFFIX;
  }
}
