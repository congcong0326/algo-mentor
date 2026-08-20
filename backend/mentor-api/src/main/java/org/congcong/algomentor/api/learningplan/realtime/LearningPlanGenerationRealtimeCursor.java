package org.congcong.algomentor.api.learningplan.realtime;

import java.util.regex.Pattern;

/** 学习计划首次草案公开事件游标的语法校验。 */
public final class LearningPlanGenerationRealtimeCursor {

  private static final Pattern CONTINUOUS_ID = Pattern.compile("(?:0|[1-9][0-9]*)-0");

  private LearningPlanGenerationRealtimeCursor() {
  }

  public static String normalizeAfter(String after) {
    if (after == null || after.isBlank()) {
      return LearningPlanGenerationRealtimeProtocol.INITIAL_AFTER;
    }
    String value = after.trim();
    if (!CONTINUOUS_ID.matcher(value).matches()) {
      throw new LearningPlanGenerationRealtimeCursorInvalidException(
          "Learning plan generation after cursor is invalid");
    }
    return value;
  }
}
