package org.congcong.algomentor.domain.learning;

public record LearningTopic(String title) {

  public LearningTopic {
    if (title == null || title.isBlank()) {
      throw new IllegalArgumentException("Learning topic title must not be blank");
    }
  }

  public static LearningTopic of(String title) {
    return new LearningTopic(title);
  }
}
