package org.congcong.algomentor.api.controller.review;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.ZoneId;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.congcong.algomentor.api.controller.LocalizedApiExceptionHandler;
import org.congcong.algomentor.auth.security.AuthenticatedUserPrincipal;
import org.congcong.algomentor.auth.security.CurrentUserIdProvider;
import org.congcong.algomentor.identity.model.AuthUserStatus;
import org.congcong.algomentor.mentor.application.review.attempt.ProblemReviewAttempt;
import org.congcong.algomentor.mentor.application.review.attempt.ReviewAttemptResult;
import org.congcong.algomentor.mentor.application.review.attempt.ReviewAttemptService;
import org.congcong.algomentor.mentor.application.review.attempt.ReviewSchedulingSnapshot;
import org.congcong.algomentor.mentor.application.review.card.ProblemReviewCard;
import org.congcong.algomentor.mentor.application.review.card.ReviewCardContext;
import org.congcong.algomentor.mentor.application.review.card.ReviewCardService;
import org.congcong.algomentor.mentor.application.review.card.ReviewCardOverview;
import org.congcong.algomentor.mentor.application.review.card.ReviewCardOverviewService;
import org.congcong.algomentor.mentor.application.review.card.ReviewCardSource;
import org.congcong.algomentor.mentor.application.review.card.ReviewQueueService;
import org.congcong.algomentor.mentor.application.review.catalog.ReviewProblemSnapshot;
import org.congcong.algomentor.mentor.application.review.note.UserProblemNote;
import org.congcong.algomentor.mentor.application.review.schedule.FsrsState;
import org.congcong.algomentor.mentor.application.review.schedule.ReviewRating;
import org.congcong.algomentor.mentor.application.review.schedule.SchedulingState;
import org.congcong.algomentor.mentor.application.practice.PracticeCodeReviewIndexEntry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ReviewCardControllerTest {

  private static final UUID ATTEMPT_ID = UUID.fromString("d42b6f40-5535-4fc4-bc07-6004bd758b25");
  private final ReviewAttemptService attemptService = mock(ReviewAttemptService.class);
  private final ReviewCardService cardService = mock(ReviewCardService.class);
  private final ReviewCardOverviewService cardOverviewService = mock(ReviewCardOverviewService.class);
  private final ReviewQueueService queueService = mock(ReviewQueueService.class);
  private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new ReviewCardController(
          provider(cardService),
          provider(cardOverviewService),
          provider(queueService),
          provider(attemptService),
          currentUserIdProvider()))
      .setControllerAdvice(new LocalizedApiExceptionHandler())
      .build();

  @Test
  void listsCardsWithRecentCodeReviewIndexEntries() throws Exception {
    when(cardOverviewService.list(42L, null, true, "two", 80, 2)).thenReturn(List.of(overview()));

    mockMvc.perform(get("/api/review-cards")
            .queryParam("mistakeOnly", "true")
            .queryParam("keyword", "two")
            .queryParam("limit", "80")
            .queryParam("offset", "2"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].card.id").value(88))
        .andExpect(jsonPath("$.data[0].recentCodeReviews[0].reviewId").value(301))
        .andExpect(jsonPath("$.data[0].recentCodeReviews[0].planId").value(12))
        .andExpect(jsonPath("$.data[0].recentCodeReviews[0].primaryFeedback").value("边界条件处理不完整"));

    verify(cardOverviewService).list(42L, null, true, "two", 80, 2);
  }

  @Test
  void submitsDirectRatingWithClientAttemptId() throws Exception {
    ZoneId timezone = ZoneId.of("Asia/Shanghai");
    when(attemptService.submit(42L, 88L, ATTEMPT_ID, ReviewRating.GOOD, timezone))
        .thenReturn(new ReviewAttemptResult(attempt(), false));

    mockMvc.perform(post("/api/review-cards/88/attempts")
            .queryParam("timezone", "Asia/Shanghai")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"clientAttemptId\":\"" + ATTEMPT_ID + "\",\"rating\":\"GOOD\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.clientAttemptId").value(ATTEMPT_ID.toString()))
        .andExpect(jsonPath("$.data.rating").value("GOOD"))
        .andExpect(jsonPath("$.data.duplicate").value(false));

    verify(attemptService).submit(42L, 88L, ATTEMPT_ID, ReviewRating.GOOD, timezone);
  }

  @Test
  void rejectsUnknownRatingBeforeCallingTheService() throws Exception {
    mockMvc.perform(post("/api/review-cards/88/attempts")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"clientAttemptId\":\"" + ATTEMPT_ID + "\",\"rating\":\"UNKNOWN\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("REVIEW_RATING_INVALID"));

    verifyNoInteractions(attemptService);
  }

  @Test
  void returnsLocalizedEnglishContextFromAcceptLanguage() throws Exception {
    ZoneId timezone = ZoneId.of("America/Los_Angeles");
    when(queueService.context(42L, 88L, "en-US", timezone)).thenReturn(context());

    mockMvc.perform(get("/api/review-cards/88/context")
            .queryParam("timezone", "America/Los_Angeles")
            .header("Accept-Language", "en-US,en;q=0.9"))
        .andExpect(status().isOk())
        .andExpect(header().string("Vary", "Accept-Language"))
        .andExpect(jsonPath("$.data.card.problemTitle").value("Two Sum"))
        .andExpect(jsonPath("$.data.problem.title").value("Two Sum"))
        .andExpect(jsonPath("$.data.problem.titleCn").doesNotExist())
        .andExpect(jsonPath("$.data.problem.contentMarkdown").value("# Two Sum\n\nEnglish statement."));

    verify(queueService).context(42L, 88L, "en-US", timezone);
  }

  private ProblemReviewAttempt attempt() {
    Instant reviewedAt = Instant.parse("2026-07-24T08:00:00Z");
    ReviewSchedulingSnapshot before = new ReviewSchedulingSnapshot(
        0, 0, 0, FsrsState.LEARNING, 0, null, null, reviewedAt, null, null);
    ReviewSchedulingSnapshot after = new ReviewSchedulingSnapshot(
        1, 3, 0, FsrsState.REVIEW, null, null, null,
        reviewedAt.plusSeconds(3L * 24 * 60 * 60), reviewedAt, ReviewRating.GOOD);
    return new ProblemReviewAttempt(501L, 88L, 42L, ATTEMPT_ID, ReviewRating.GOOD, before, after, reviewedAt);
  }

  private ReviewCardOverview overview() {
    Instant now = Instant.parse("2026-07-24T08:00:00Z");
    return new ReviewCardOverview(context().card(), List.of(new PracticeCodeReviewIndexEntry(
        301L,
        12L,
        1,
        "two-sum",
        50L,
        3,
        "java",
        "zh-CN",
        new BigDecimal("7.5"),
        true,
        "边界条件处理不完整",
        now)));
  }

  private ReviewCardContext context() {
    Instant now = Instant.parse("2026-07-24T08:00:00Z");
    ProblemReviewCard card = new ProblemReviewCard(
        88L,
        42L,
        "two-sum",
        ReviewCardSource.REVIEW_FAILED,
        Map.of(),
        SchedulingState.initial(),
        now,
        null,
        null,
        false,
        now,
        now);
    return new ReviewCardContext(
        card,
        new ReviewProblemSnapshot(
            "two-sum",
            "Two Sum",
            "EASY",
            "English statement.",
            "# Two Sum\n\nEnglish statement."),
        UserProblemNote.empty(42L, "two-sum"),
        List.of(),
        List.of());
  }

  private CurrentUserIdProvider currentUserIdProvider() {
    return () -> Optional.of(new AuthenticatedUserPrincipal(
        42L,
        "learner@example.com",
        "Learner",
        null,
        List.of(),
        AuthUserStatus.ACTIVE));
  }

  @SuppressWarnings("unchecked")
  private static <T> ObjectProvider<T> provider(T bean) {
    ObjectProvider<T> provider = mock(ObjectProvider.class);
    when(provider.getIfAvailable(org.mockito.ArgumentMatchers.any())).thenReturn(bean);
    when(provider.getIfAvailable()).thenReturn(bean);
    return provider;
  }
}
