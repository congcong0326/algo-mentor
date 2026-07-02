package org.congcong.algomentor.mentor.application.review;

public enum CardGenerationOutcome {
  GENERATED,
  CACHE_HIT,
  QUOTA_EXCEEDED,
  FAILED,
  FALLBACK_RULE
}
