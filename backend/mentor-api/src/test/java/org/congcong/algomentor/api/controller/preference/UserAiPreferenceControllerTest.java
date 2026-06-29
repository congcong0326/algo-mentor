package org.congcong.algomentor.api.controller.preference;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.congcong.algomentor.api.controller.LocalizedApiExceptionHandler;
import org.congcong.algomentor.auth.model.AuthUserStatus;
import org.congcong.algomentor.auth.security.AuthenticatedUserPrincipal;
import org.congcong.algomentor.auth.security.CurrentUserIdProvider;
import org.congcong.algomentor.mentor.application.preference.UserAiPreference;
import org.congcong.algomentor.mentor.application.preference.UserAiPreferenceService;
import org.congcong.algomentor.mentor.application.preference.UserAiPreferenceUpdate;
import org.congcong.algomentor.mentor.application.practice.PracticeCoachStyle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = UserAiPreferenceController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({
    UserAiPreferenceControllerTest.TestConfig.class,
    LocalizedApiExceptionHandler.class
})
class UserAiPreferenceControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private UserAiPreferenceService preferenceService;

  @Autowired
  private CurrentUserIdProvider currentUserIdProvider;

  @BeforeEach
  void resetMocks() {
    reset(preferenceService, currentUserIdProvider);
  }

  @Test
  void getPreferenceUsesCurrentUser() throws Exception {
    when(currentUserIdProvider.currentUser()).thenReturn(Optional.of(currentUser()));
    when(preferenceService.get(42L)).thenReturn(preference(PracticeCoachStyle.SOCRATIC_GUIDE));

    mockMvc.perform(get("/api/me/ai-preferences"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.coachStyle").value("SOCRATIC_GUIDE"))
        .andExpect(jsonPath("$.data.coachStyleLabel").value("启发型教练"))
        .andExpect(jsonPath("$.data.responseLanguage").doesNotExist())
        .andExpect(jsonPath("$.data.responseLanguageLabel").doesNotExist());

    verify(preferenceService).get(42L);
  }

  @Test
  void patchPreferenceSavesSupportedValues() throws Exception {
    when(currentUserIdProvider.currentUser()).thenReturn(Optional.of(currentUser()));
    when(preferenceService.update(org.mockito.ArgumentMatchers.eq(42L), org.mockito.ArgumentMatchers.any()))
        .thenReturn(preference(PracticeCoachStyle.INTERVIEWER));

    mockMvc.perform(patch("/api/me/ai-preferences")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"coachStyle\":\"INTERVIEWER\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.coachStyle").value("INTERVIEWER"))
        .andExpect(jsonPath("$.data.coachStyleLabel").value("面试官教练"))
        .andExpect(jsonPath("$.data.responseLanguage").doesNotExist());

    ArgumentCaptor<UserAiPreferenceUpdate> updateCaptor = ArgumentCaptor.forClass(UserAiPreferenceUpdate.class);
    verify(preferenceService).update(org.mockito.ArgumentMatchers.eq(42L), updateCaptor.capture());
    org.assertj.core.api.Assertions.assertThat(updateCaptor.getValue().coachStyle()).isEqualTo(PracticeCoachStyle.INTERVIEWER);
  }

  @Test
  void preferenceRequiresAuthentication() throws Exception {
    when(currentUserIdProvider.currentUser()).thenReturn(Optional.empty());

    mockMvc.perform(get("/api/me/ai-preferences"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("AUTH_UNAUTHENTICATED"));

    verifyNoInteractions(preferenceService);
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

  private UserAiPreference preference(PracticeCoachStyle style) {
    return new UserAiPreference(
        42L,
        style,
        Instant.parse("2026-06-28T00:00:00Z"),
        Instant.parse("2026-06-28T00:00:00Z"));
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class TestConfig {

    @Bean
    UserAiPreferenceService preferenceService() {
      return mock(UserAiPreferenceService.class);
    }

    @Bean
    CurrentUserIdProvider currentUserIdProvider() {
      return mock(CurrentUserIdProvider.class);
    }
  }
}
