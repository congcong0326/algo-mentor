package org.congcong.algomentor.api.controller.review;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.congcong.algomentor.api.controller.LocalizedApiExceptionHandler;
import org.congcong.algomentor.api.review.service.MistakeNoteDisplayInfoResolver;
import org.congcong.algomentor.auth.security.AuthenticatedUserPrincipal;
import org.congcong.algomentor.auth.security.CurrentUserIdProvider;
import org.congcong.algomentor.identity.model.AuthUserStatus;
import org.congcong.algomentor.mentor.application.review.MasteryState;
import org.congcong.algomentor.mentor.application.review.MistakeNoteService;
import org.congcong.algomentor.mentor.application.review.RecallConfirmResult;
import org.congcong.algomentor.mentor.application.review.ReviewProblemCatalog;
import org.congcong.algomentor.mentor.application.review.ReviewRating;
import org.congcong.algomentor.mentor.application.review.ReviewSessionService;
import org.congcong.algomentor.mentor.application.review.SchedulingState;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = MistakeNoteController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({
    MistakeNoteControllerRecallRatingTest.TestConfig.class,
    LocalizedApiExceptionHandler.class
})
class MistakeNoteControllerRecallRatingTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ReviewSessionService reviewSessionService;

  @Test
  void rateRecallParsesRatingAndReturnsConfirmResponse() throws Exception {
    when(reviewSessionService.rateRecall(eq(42L), eq(88L), eq(ReviewRating.HARD)))
        .thenReturn(new RecallConfirmResult(
            ReviewRating.HARD,
            null,
            Instant.parse("2026-07-03T00:00:00Z"),
            new SchedulingState(2, new BigDecimal("2.35"), 1, MasteryState.LEARNING, 0),
            false));

    mockMvc.perform(post("/api/mistake-notes/88/recall/rating")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"rating\":\"HARD\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.rating").value("HARD"))
        .andExpect(jsonPath("$.data.suggestedRating").doesNotExist())
        .andExpect(jsonPath("$.data.intervalDays").value(1))
        .andExpect(jsonPath("$.data.aiSuggested").value(false));

    verify(reviewSessionService).rateRecall(42L, 88L, ReviewRating.HARD);
  }

  @Test
  void rateRecallRejectsInvalidRatingBeforeServiceCall() throws Exception {
    mockMvc.perform(post("/api/mistake-notes/88/recall/rating")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"rating\":\"UNKNOWN\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("REVIEW_RATING_INVALID"));

    verifyNoInteractions(reviewSessionService);
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class TestConfig {

    @Bean
    MistakeNoteService mistakeNoteService() {
      return mock(MistakeNoteService.class);
    }

    @Bean
    ReviewSessionService reviewSessionService() {
      return mock(ReviewSessionService.class);
    }

    @Bean
    ReviewProblemCatalog reviewProblemCatalog() {
      return mock(ReviewProblemCatalog.class);
    }

    @Bean
    MistakeNoteDisplayInfoResolver mistakeNoteDisplayInfoResolver() {
      return mock(MistakeNoteDisplayInfoResolver.class);
    }

    @Bean
    CurrentUserIdProvider currentUserIdProvider() {
      return () -> Optional.of(new AuthenticatedUserPrincipal(
          42L,
          "learner@example.com",
          "Learner",
          null,
          List.of(),
          AuthUserStatus.ACTIVE));
    }
  }
}
