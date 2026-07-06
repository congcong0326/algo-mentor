package org.congcong.algomentor.api.controller.review;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.congcong.algomentor.api.controller.LocalizedApiExceptionHandler;
import org.congcong.algomentor.api.review.model.MistakeNoteDisplayInfo;
import org.congcong.algomentor.api.review.service.MistakeNoteDisplayInfoResolver;
import org.congcong.algomentor.auth.security.AuthenticatedUserPrincipal;
import org.congcong.algomentor.auth.security.CurrentUserIdProvider;
import org.congcong.algomentor.identity.model.AuthUserStatus;
import org.congcong.algomentor.mentor.application.review.FsrsReviewSchedulerService;
import org.congcong.algomentor.mentor.application.review.MistakeNote;
import org.congcong.algomentor.mentor.application.review.MistakeNoteService;
import org.congcong.algomentor.mentor.application.review.MistakeSource;
import org.congcong.algomentor.mentor.application.review.RecallConfirmResult;
import org.congcong.algomentor.mentor.application.review.ReviewProblemCatalog;
import org.congcong.algomentor.mentor.application.review.ReviewRating;
import org.congcong.algomentor.mentor.application.review.ReviewSessionService;
import org.congcong.algomentor.mentor.application.review.SchedulingState;
import org.junit.jupiter.api.AfterEach;
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

  @Autowired
  private MistakeNoteService mistakeNoteService;

  @Autowired
  private MistakeNoteDisplayInfoResolver displayInfoResolver;

  @AfterEach
  void resetMocks() {
    reset(mistakeNoteService, reviewSessionService, displayInfoResolver);
  }

  @Test
  void listDoesNotExposeOrUseMasteryStateFilter() throws Exception {
    MistakeNote note = note();
    when(mistakeNoteService.list(eq(42L), isNull(), eq(false), isNull(), eq(50), eq(0)))
        .thenReturn(List.of(note));
    when(displayInfoResolver.resolve(eq(note), org.mockito.ArgumentMatchers.any()))
        .thenReturn(new MistakeNoteDisplayInfo("两数之和", "zh-CN", "EASY"));

    mockMvc.perform(get("/api/mistake-notes")
            .param("masteryState", "MASTERED"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].masteryState").doesNotExist())
        .andExpect(jsonPath("$.data[0].problemTitle").value("两数之和"));

    verify(mistakeNoteService).list(42L, null, false, null, 50, 0);
  }

  @Test
  void rateRecallParsesRatingAndReturnsConfirmResponse() throws Exception {
    when(reviewSessionService.rateRecall(eq(42L), eq(88L), eq(ReviewRating.HARD)))
        .thenReturn(new RecallConfirmResult(
            ReviewRating.HARD,
            null,
            Instant.parse("2026-07-03T00:00:00Z"),
            new SchedulingState(2, 1, 0),
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
  void recallIntervalsReturnsRatingPreviews() throws Exception {
    when(reviewSessionService.intervalPreviews(eq(42L), eq(88L)))
        .thenReturn(List.of(
            new FsrsReviewSchedulerService.ReviewIntervalPreview(
                ReviewRating.AGAIN, Instant.parse("2026-07-02T00:10:00Z"), 0),
            new FsrsReviewSchedulerService.ReviewIntervalPreview(
                ReviewRating.HARD, Instant.parse("2026-07-03T00:00:00Z"), 1),
            new FsrsReviewSchedulerService.ReviewIntervalPreview(
                ReviewRating.GOOD, Instant.parse("2026-07-05T00:00:00Z"), 3),
            new FsrsReviewSchedulerService.ReviewIntervalPreview(
                ReviewRating.EASY, Instant.parse("2026-07-09T00:00:00Z"), 7)));

    mockMvc.perform(get("/api/mistake-notes/88/recall/intervals"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].rating").value("AGAIN"))
        .andExpect(jsonPath("$.data[0].intervalDays").value(0))
        .andExpect(jsonPath("$.data[1].rating").value("HARD"))
        .andExpect(jsonPath("$.data[1].intervalDays").value(1))
        .andExpect(jsonPath("$.data[2].rating").value("GOOD"))
        .andExpect(jsonPath("$.data[2].intervalDays").value(3))
        .andExpect(jsonPath("$.data[3].rating").value("EASY"))
        .andExpect(jsonPath("$.data[3].intervalDays").value(7));

    verify(reviewSessionService).intervalPreviews(42L, 88L);
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

  private MistakeNote note() {
    Instant now = Instant.parse("2026-07-02T00:00:00Z");
    return new MistakeNote(
        88L,
        42L,
        "two-sum",
        MistakeSource.REVIEW_PASSED,
        Map.of(),
        null,
        null,
        null,
        new SchedulingState(3, 30, 0),
        now,
        now,
        null,
        false,
        null,
        null,
        now,
        now);
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
