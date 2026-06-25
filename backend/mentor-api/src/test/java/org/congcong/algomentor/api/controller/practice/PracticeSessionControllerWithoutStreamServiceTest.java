package org.congcong.algomentor.api.controller.practice;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.congcong.algomentor.api.config.ApiSseProperties;
import org.congcong.algomentor.auth.model.AuthUserStatus;
import org.congcong.algomentor.auth.security.AuthenticatedUserPrincipal;
import org.congcong.algomentor.auth.security.CurrentUserIdProvider;
import org.congcong.algomentor.mentor.application.practice.PracticeChatProblemDetail;
import org.congcong.algomentor.mentor.application.practice.PracticeChatReference;
import org.congcong.algomentor.mentor.application.practice.PracticeProgressStatus;
import org.congcong.algomentor.mentor.application.practice.PracticeSession;
import org.congcong.algomentor.mentor.application.practice.PracticeSessionResult;
import org.congcong.algomentor.mentor.application.practice.PracticeSessionService;
import org.congcong.algomentor.mentor.application.practice.PracticeSessionStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.beans.factory.annotation.Autowired;

@WebMvcTest(controllers = PracticeSessionController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({
    PracticeSessionControllerWithoutStreamServiceTest.TestConfig.class,
    PracticeSessionExceptionHandler.class
})
class PracticeSessionControllerWithoutStreamServiceTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private PracticeSessionService practiceSessionService;

  @Test
  void createPracticeSessionRouteIsRegisteredWithoutStreamService() throws Exception {
    when(practiceSessionService.createOrReuse(eq(42L), any(PracticeChatReference.class)))
        .thenReturn(new PracticeSessionResult(
            new PracticeSession(
                50L,
                42L,
                4L,
                1,
                "maximum-subarray",
                PracticeSessionStatus.ACTIVE,
                80L,
                70L,
                PracticeProgressStatus.IN_PROGRESS,
                null,
                Instant.parse("2026-06-25T00:00:00Z"),
                Instant.parse("2026-06-25T00:00:00Z"),
                "zh-CN"),
            new PracticeChatProblemDetail(
                "maximum-subarray",
                53,
                "Maximum Subarray",
                "MEDIUM",
                List.of("Array", "Dynamic Programming"),
                "# Maximum Subarray",
                "https://leetcode.com/problems/maximum-subarray/"),
            List.of(),
            Optional.empty()));

    mockMvc.perform(post("/api/learning-plans/4/phases/1/problems/maximum-subarray/practice-session"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.session.id").value(50));
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class TestConfig {

    @Bean
    PracticeSessionService practiceSessionService() {
      return mock(PracticeSessionService.class);
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

    @Bean
    ApiSseProperties apiSseProperties() {
      return new ApiSseProperties();
    }

    @Bean
    PracticeSessionController practiceSessionController(
        ObjectProvider<PracticeSessionService> practiceSessionService,
        ObjectProvider<org.congcong.algomentor.mentor.application.practice.PracticeMessageStreamService> streamService,
        CurrentUserIdProvider currentUserIdProvider,
        ObjectProvider<org.congcong.algomentor.api.service.AiActorResolver> actorResolver,
        ObjectProvider<org.congcong.algomentor.ai.governance.admission.AiRunAdmissionService> admissionService,
        ObjectProvider<org.congcong.algomentor.api.service.LlmStreamSseMapper> sseMapper,
        ApiSseProperties sseProperties
    ) {
      return new PracticeSessionController(
          practiceSessionService,
          streamService,
          currentUserIdProvider,
          actorResolver,
          admissionService,
          sseMapper,
          sseProperties);
    }
  }
}
