package org.congcong.algomentor.mentor.application.learningplan;

import java.util.List;
import org.congcong.algomentor.mentor.application.practice.PracticeProgress;

/**
 * 今日题包页面组装期间复用的计划快照，避免重复读取计划和练习进度。
 */
public record TodayPackWorkspace(
    TodayPack pack,
    LearningPlan plan,
    List<PracticeProgress> progress
) {

  public TodayPackWorkspace {
    if (pack == null || plan == null) {
      throw new IllegalArgumentException("Today pack workspace requires pack and plan");
    }
    progress = progress == null ? List.of() : List.copyOf(progress);
  }
}
