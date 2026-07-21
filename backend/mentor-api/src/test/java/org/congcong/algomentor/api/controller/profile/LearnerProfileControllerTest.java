package org.congcong.algomentor.api.controller.profile;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.congcong.algomentor.api.controller.LocalizedApiExceptionHandler;
import org.congcong.algomentor.api.profile.model.LearnerProfileEntryResponse;
import org.congcong.algomentor.api.profile.model.LearnerProfileResponse;
import org.congcong.algomentor.api.profile.model.LearnerProfileTagResponse;
import org.congcong.algomentor.api.profile.service.LearnerProfileViewService;
import org.congcong.algomentor.auth.security.AuthenticatedUserPrincipal;
import org.congcong.algomentor.auth.security.CurrentUserIdProvider;
import org.congcong.algomentor.identity.model.AuthUserStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = LearnerProfileController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({
    LearnerProfileControllerTest.TestConfig.class,
    LocalizedApiExceptionHandler.class
})
class LearnerProfileControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private LearnerProfileViewService profileService;

  @Autowired
  private CurrentUserIdProvider currentUserIdProvider;

  @Test
  void profileUsesCurrentUserAndReturnsOnlyDisplayFields() throws Exception {
    when(currentUserIdProvider.currentUser()).thenReturn(Optional.of(currentUser()));
    when(profileService.getProfile(42L)).thenReturn(profile());

    mockMvc.perform(get("/api/me/learner-profile?userId=99"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.declaredFacts[0].dimension").value("GOALS_AND_INTENTS"))
        .andExpect(jsonPath("$.data.declaredFacts[0].contentText").value("准备后端面试。"))
        .andExpect(jsonPath("$.data.generalObservations[0].dimension").value("PROBLEM_SOLVING_APPROACH"))
        .andExpect(jsonPath("$.data.tagAssessments[0].tag.value").value("binary-search"))
        .andExpect(jsonPath("$.data.tagAssessments[0].tag.labelZh").value("二分查找"))
        .andExpect(jsonPath("$.data.updatedAt").value("2026-07-20T12:00:00Z"))
        .andExpect(jsonPath("$.data.declaredFacts[0].modelName").doesNotExist())
        .andExpect(jsonPath("$.data.declaredFacts[0].promptVersion").doesNotExist());

    verify(profileService).getProfile(42L);
  }

  @Test
  void profileRequiresAuthentication() throws Exception {
    when(currentUserIdProvider.currentUser()).thenReturn(Optional.empty());

    mockMvc.perform(get("/api/me/learner-profile"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("AUTH_UNAUTHENTICATED"));

    verifyNoInteractions(profileService);
  }

  private AuthenticatedUserPrincipal currentUser() {
    return new AuthenticatedUserPrincipal(
        42L,
        "learner@example.com",
        "Learner",
        null,
        List.of(),
        AuthUserStatus.ACTIVE);
  }

  private LearnerProfileResponse profile() {
    Instant updatedAt = Instant.parse("2026-07-20T12:00:00Z");
    return new LearnerProfileResponse(
        List.of(new LearnerProfileEntryResponse(
            1L, "GOALS_AND_INTENTS", 2, "准备后端面试。", "USER_EXPLICIT", updatedAt, null)),
        List.of(new LearnerProfileEntryResponse(
            2L, "PROBLEM_SOLVING_APPROACH", 1, "能够先拆解状态。", "SYSTEM_DERIVED", updatedAt, null)),
        List.of(new LearnerProfileEntryResponse(
            3L,
            "TAG_MASTERY",
            1,
            "循环不变量仍需巩固。",
            "SYSTEM_DERIVED",
            updatedAt,
            new LearnerProfileTagResponse(7L, "binary-search", "Binary Search", "二分查找"))),
        updatedAt);
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class TestConfig {

    @Bean
    LearnerProfileViewService learnerProfileViewService() {
      return mock(LearnerProfileViewService.class);
    }

    @Bean
    CurrentUserIdProvider currentUserIdProvider() {
      return mock(CurrentUserIdProvider.class);
    }
  }
}
