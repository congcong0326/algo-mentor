package org.congcong.algomentor.mentor.application.learningplan;

public record LearningPlanProblemSearch(String keyword, String difficulty, int limit) {

  public LearningPlanProblemSearch {
    limit = limit < 1 ? 5 : limit;
  }
}
