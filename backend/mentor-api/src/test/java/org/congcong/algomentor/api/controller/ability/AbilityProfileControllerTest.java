package org.congcong.algomentor.api.controller.ability;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.congcong.algomentor.api.ability.model.AbilityProfileResponse;
import org.congcong.algomentor.api.ability.model.AbilityProfileScopeResponse;
import org.congcong.algomentor.api.ability.model.AbilityTagScoreResponse;
import org.congcong.algomentor.api.ability.service.AbilityProfileService;
import org.congcong.algomentor.api.controller.LocalizedApiExceptionHandler;
import org.congcong.algomentor.identity.model.AuthUserStatus;
import org.congcong.algomentor.auth.security.AuthenticatedUserPrincipal;
import org.congcong.algomentor.auth.security.CurrentUserIdProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = AbilityProfileController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({
    AbilityProfileControllerTest.TestConfig.class,
    LocalizedApiExceptionHandler.class
})
class AbilityProfileControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private AbilityProfileService abilityProfileService;

  @Autowired
  private CurrentUserIdProvider currentUserIdProvider;

  @Test
  void profileUsesCurrentUserAndIgnoresRequestUserId() throws Exception {
    when(currentUserIdProvider.currentUser()).thenReturn(Optional.of(currentUser()));
    when(abilityProfileService.getProfile(42L)).thenReturn(profile());

    mockMvc.perform(get("/api/abilities/profile?userId=99"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.tags[0].tag").value("dynamic-programming"))
        .andExpect(jsonPath("$.data.tags[0].label").value("动态规划"))
        .andExpect(jsonPath("$.data.tags[0].problemCount").value(240))
        .andExpect(jsonPath("$.data.tags[0].reviewedProblemCount").value(3))
        .andExpect(jsonPath("$.data.tags[0].rawAverageScore").value(8.0))
        .andExpect(jsonPath("$.data.tags[0].abilityScore").value(3.4))
        .andExpect(jsonPath("$.data.scope.minProblemCount").value(20))
        .andExpect(jsonPath("$.data.scope.scorePrecision").value(1))
        .andExpect(jsonPath("$.data.scope.latestReviewOnly").value(true))
        .andExpect(jsonPath("$.data.scope.conservativeWeight").value(4));

    verify(abilityProfileService).getProfile(42L);
  }

  @Test
  void profileRequiresAuthentication() throws Exception {
    when(currentUserIdProvider.currentUser()).thenReturn(Optional.empty());

    mockMvc.perform(get("/api/abilities/profile"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("AUTH_UNAUTHENTICATED"));

    verifyNoInteractions(abilityProfileService);
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

  private AbilityProfileResponse profile() {
    return new AbilityProfileResponse(
        List.of(new AbilityTagScoreResponse(
            "dynamic-programming",
            "动态规划",
            240L,
            3L,
            new BigDecimal("8.0"),
            new BigDecimal("3.4"))),
        new AbilityProfileScopeResponse(20, 1, true, 4));
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class TestConfig {

    @Bean
    AbilityProfileService abilityProfileService() {
      return mock(AbilityProfileService.class);
    }

    @Bean
    CurrentUserIdProvider currentUserIdProvider() {
      return mock(CurrentUserIdProvider.class);
    }
  }
}
