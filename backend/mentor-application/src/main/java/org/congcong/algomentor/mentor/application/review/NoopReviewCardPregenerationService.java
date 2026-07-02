package org.congcong.algomentor.mentor.application.review;

import java.time.Clock;
import org.congcong.algomentor.ai.governance.model.AiUsage;
import org.congcong.algomentor.ai.governance.usage.AiDailyUsageStore;

public final class NoopReviewCardPregenerationService extends ReviewCardPregenerationService {

  public NoopReviewCardPregenerationService(
      MistakeNoteRepository repository,
      ReviewCardService cardService
  ) {
    super(
        Runnable::run,
        new NoopAiDailyUsageStore(),
        repository,
        cardService,
        ReviewCardProperties.defaults(),
        MistakeReviewMetrics.NOOP,
        Clock.systemUTC());
  }

  @Override
  public void enqueue(long noteId) {
  }

  private static final class NoopAiDailyUsageStore implements AiDailyUsageStore {
    @Override
    public boolean tryConsumeRequest(long userId, java.time.LocalDate quotaDate, String scope, long limitCount) {
      return false;
    }

    @Override
    public void addUsage(long userId, java.time.LocalDate quotaDate, String scope, AiUsage usage) {
    }
  }
}
