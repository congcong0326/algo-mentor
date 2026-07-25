package org.congcong.algomentor.api.controller.review;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.congcong.algomentor.auth.security.AuthenticatedUserPrincipal;
import org.congcong.algomentor.auth.security.CurrentUserIdProvider;
import org.congcong.algomentor.identity.model.AuthUserStatus;
import org.congcong.algomentor.mentor.application.review.card.ReviewQueueService;
import org.congcong.algomentor.mentor.application.review.card.ReviewSummary;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ReviewSessionControllerTest {

  @Test
  void returnsCurrentAndLaterTodayReviewCountsInTheRequestedTimezone() throws Exception {
    ReviewQueueService queueService = mock(ReviewQueueService.class);
    Instant nextDueAt = Instant.parse("2026-07-24T08:10:00Z");
    when(queueService.summary(42L, "Asia/Shanghai"))
        .thenReturn(new ReviewSummary(0, 1, nextDueAt));
    MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new ReviewSessionController(
        provider(queueService),
        currentUserIdProvider())).build();

    mockMvc.perform(get("/api/review-sessions/summary")
            .param("timezone", "Asia/Shanghai"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.dueCount").value(0))
        .andExpect(jsonPath("$.data.remainingTodayCount").value(1))
        .andExpect(jsonPath("$.data.nextDueAt").exists());

    verify(queueService).summary(42L, "Asia/Shanghai");
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
    return provider;
  }
}
