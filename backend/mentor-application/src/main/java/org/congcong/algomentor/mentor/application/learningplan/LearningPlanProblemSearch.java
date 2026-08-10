package org.congcong.algomentor.mentor.application.learningplan;

public record LearningPlanProblemSearch(String keyword, String difficulty, String tag, int limit) {

  public LearningPlanProblemSearch(String keyword, String difficulty, int limit) {
    this(keyword, difficulty, null, limit);
  }

  public LearningPlanProblemSearch {
    keyword = keyword == null || keyword.isBlank() ? null : keyword.trim();
    difficulty = difficulty == null || difficulty.isBlank() ? null : difficulty.trim();
    tag = tag == null || tag.isBlank() ? null : tag.trim();
    limit = limit < 1 ? 5 : limit;
  }
}
