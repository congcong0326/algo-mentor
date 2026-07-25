package org.congcong.algomentor.api.controller.review;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
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
import org.congcong.algomentor.mentor.application.review.card.ReviewCardService;
import org.congcong.algomentor.mentor.application.review.card.ReviewQueueService;
import org.congcong.algomentor.mentor.application.review.schedule.FsrsState;
import org.congcong.algomentor.mentor.application.review.schedule.ReviewRating;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ReviewCardControllerTest {

  private static final UUID ATTEMPT_ID = UUID.fromString("d42b6f40-5535-4fc4-bc07-6004bd758b25");
  private final ReviewAttemptService attemptService = mock(ReviewAttemptService.class);
  private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new ReviewCardController(
          provider(mock(ReviewCardService.class)),
          provider(mock(ReviewQueueService.class)),
          provider(attemptService),
          currentUserIdProvider()))
      .setControllerAdvice(new LocalizedApiExceptionHandler())
      .build();

  @Test
  void submitsDirectRatingWithClientAttemptId() throws Exception {
    when(attemptService.submit(42L, 88L, ATTEMPT_ID, ReviewRating.GOOD))
        .thenReturn(new ReviewAttemptResult(attempt(), false));

    mockMvc.perform(post("/api/review-cards/88/attempts")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"clientAttemptId\":\"" + ATTEMPT_ID + "\",\"rating\":\"GOOD\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.clientAttemptId").value(ATTEMPT_ID.toString()))
        .andExpect(jsonPath("$.data.rating").value("GOOD"))
        .andExpect(jsonPath("$.data.duplicate").value(false));

    verify(attemptService).submit(42L, 88L, ATTEMPT_ID, ReviewRating.GOOD);
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

  private ProblemReviewAttempt attempt() {
    Instant reviewedAt = Instant.parse("2026-07-24T08:00:00Z");
    ReviewSchedulingSnapshot before = new ReviewSchedulingSnapshot(
        0, 0, 0, FsrsState.LEARNING, 0, null, null, reviewedAt, null, null);
    ReviewSchedulingSnapshot after = new ReviewSchedulingSnapshot(
        1, 3, 0, FsrsState.REVIEW, null, null, null,
        reviewedAt.plusSeconds(3L * 24 * 60 * 60), reviewedAt, ReviewRating.GOOD);
    return new ProblemReviewAttempt(501L, 88L, 42L, ATTEMPT_ID, ReviewRating.GOOD, before, after, reviewedAt);
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
