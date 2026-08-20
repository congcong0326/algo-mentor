package org.congcong.algomentor.api.controller.activity;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.congcong.algomentor.api.activity.model.ActivityContributionDailyCountResponse;
import org.congcong.algomentor.api.activity.model.ActivityContributionResponse;
import org.congcong.algomentor.api.activity.service.ActivityContributionService;
import org.congcong.algomentor.api.controller.LocalizedApiExceptionHandler;
import org.congcong.algomentor.auth.security.AuthenticatedUserPrincipal;
import org.congcong.algomentor.auth.security.CurrentUserIdProvider;
import org.congcong.algomentor.identity.model.AuthUserStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = ActivityContributionController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({ActivityContributionControllerTest.TestConfig.class, LocalizedApiExceptionHandler.class})
class ActivityContributionControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ActivityContributionService activityContributionService;

  @Autowired
  private CurrentUserIdProvider currentUserIdProvider;

  @BeforeEach
  void clearMockInvocations() {
    clearInvocations(activityContributionService, currentUserIdProvider);
  }

  @Test
  void contributionsUseCurrentUserAndTimezone() throws Exception {
    when(currentUserIdProvider.currentUser()).thenReturn(Optional.of(currentUser()));
    when(activityContributionService.getContributions(42L, "Asia/Shanghai")).thenReturn(response());

    mockMvc.perform(get("/api/activity/contributions?timezone=Asia/Shanghai"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.timezone").value("Asia/Shanghai"))
        .andExpect(jsonPath("$.data.totalCount").value(8))
        .andExpect(jsonPath("$.data.dailyCounts[0][0]").value(364))
        .andExpect(jsonPath("$.data.dailyCounts[0][1]").value(5))
        .andExpect(jsonPath("$.data.days").doesNotExist());

    verify(activityContributionService).getContributions(42L, "Asia/Shanghai");
  }

  @Test
  void contributionsRequireAuthentication() throws Exception {
    when(currentUserIdProvider.currentUser()).thenReturn(Optional.empty());

    mockMvc.perform(get("/api/activity/contributions"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("AUTH_UNAUTHENTICATED"));

    verifyNoInteractions(activityContributionService);
  }

  @Test
  void invalidTimezoneIsReportedAsBadRequest() throws Exception {
    when(currentUserIdProvider.currentUser()).thenReturn(Optional.of(currentUser()));
    when(activityContributionService.getContributions(42L, "bad-zone"))
        .thenThrow(new ActivityContributionService.ActivityTimezoneInvalidException());

    mockMvc.perform(get("/api/activity/contributions?timezone=bad-zone"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("ACTIVITY_TIMEZONE_INVALID"));
  }

  private AuthenticatedUserPrincipal currentUser() {
    return new AuthenticatedUserPrincipal(42L, "learner@example.com", "Learner", null, List.of(), AuthUserStatus.ACTIVE);
  }

  private ActivityContributionResponse response() {
    return new ActivityContributionResponse(
        "Asia/Shanghai",
        LocalDate.of(2025, 8, 20),
        LocalDate.of(2026, 8, 19),
        8,
        3,
        3,
        3,
        List.of(new ActivityContributionDailyCountResponse(364, 5)));
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class TestConfig {

    @Bean
    ActivityContributionService activityContributionService() {
      return mock(ActivityContributionService.class);
    }

    @Bean
    CurrentUserIdProvider currentUserIdProvider() {
      return mock(CurrentUserIdProvider.class);
    }
  }
}
