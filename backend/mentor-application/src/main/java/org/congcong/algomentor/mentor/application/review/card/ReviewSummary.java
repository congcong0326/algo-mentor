package org.congcong.algomentor.mentor.application.review.card;

import java.time.Instant;

public record ReviewSummary(int dueCount, int remainingTodayCount, Instant nextDueAt) {
}
