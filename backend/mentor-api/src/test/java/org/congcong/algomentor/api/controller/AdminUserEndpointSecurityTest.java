package org.congcong.algomentor.api.controller;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Set;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import org.congcong.algomentor.agent.core.runtime.api.AgentRuntime;
import org.congcong.algomentor.api.MentorApiApplication;
import org.congcong.algomentor.api.config.MentorConfigurationKeys;
import org.congcong.algomentor.api.controller.admin.ai.AdminAiApiContractConstants;
import org.congcong.algomentor.api.controller.admin.feedback.AdminFeedbackApiContractConstants;
import org.congcong.algomentor.api.controller.admin.overview.AdminOverviewApiContractConstants;
import org.congcong.algomentor.api.controller.feedback.FeedbackApiContractConstants;
import org.congcong.algomentor.api.databasebackup.controller.DatabaseBackupApiContractConstants;
import org.congcong.algomentor.api.databasebackup.service.DatabaseBackupErrorCode;
import org.congcong.algomentor.auth.config.AuthSecurityPaths;
import org.congcong.algomentor.auth.controller.admin.AdminPasswordResetApiContractConstants;
import org.congcong.algomentor.auth.controller.admin.BetaAccessApiContractConstants;
import org.congcong.algomentor.identity.controller.AdminUserApiContractConstants;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

@SpringBootTest(
    classes = {MentorApiApplication.class, AdminUserEndpointSecurityTest.TestConfig.class},
    properties = {
        "spring.datasource.url=jdbc:postgresql://localhost/algo_mentor_test",
        MentorConfigurationKeys.LEARNING_PLAN_GENERATION_RECOVERY_ENABLED + "=false"
    })
@AutoConfigureMockMvc
class AdminUserEndpointSecurityTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private RequestMappingHandlerMapping requestMappingHandlerMapping;

  @Test
  void aiGovernanceEndpointsAreMappedWhenDataSourceIsConfigured() {
    Set<String> mappedPaths = requestMappingHandlerMapping.getHandlerMethods().keySet().stream()
        .flatMap(mapping -> mapping.getPatternValues().stream())
        .collect(Collectors.toSet());

    Set<String> expectedPaths = Set.of(
        AdminAiApiContractConstants.ADMIN_AI_BASE_PATH + AdminAiApiContractConstants.SETTINGS_PATH,
        AdminAiApiContractConstants.ADMIN_AI_BASE_PATH + AdminAiApiContractConstants.MODEL_PRICES_PATH,
        AdminAiApiContractConstants.ADMIN_AI_BASE_PATH + AdminAiApiContractConstants.MODEL_PRICE_ID_PATH,
        AdminAiApiContractConstants.ADMIN_AI_BASE_PATH + AdminAiApiContractConstants.USAGE_SUMMARY_PATH,
        AdminAiApiContractConstants.ADMIN_AI_BASE_PATH + AdminAiApiContractConstants.USAGE_BY_USER_PATH,
        AdminAiApiContractConstants.ADMIN_AI_BASE_PATH + AdminAiApiContractConstants.USAGE_BY_MODEL_PATH,
        AdminAiApiContractConstants.ADMIN_AI_BASE_PATH + AdminAiApiContractConstants.USAGE_BY_SOURCE_PATH,
        AdminAiApiContractConstants.ADMIN_AI_BASE_PATH + AdminAiApiContractConstants.PROVIDER_TYPES_PATH,
        AdminAiApiContractConstants.ADMIN_AI_BASE_PATH + AdminAiApiContractConstants.PROVIDERS_PATH,
        AdminAiApiContractConstants.ADMIN_AI_BASE_PATH + AdminAiApiContractConstants.PROVIDER_ID_PATH,
        AdminAiApiContractConstants.ADMIN_AI_BASE_PATH + AdminAiApiContractConstants.PROVIDER_MODELS_PATH,
        AdminAiApiContractConstants.ADMIN_AI_BASE_PATH + AdminAiApiContractConstants.MODEL_ID_PATH,
        AdminAiApiContractConstants.ADMIN_AI_BASE_PATH + AdminAiApiContractConstants.MODEL_ROUTING_SCENARIOS_PATH,
        AdminAiApiContractConstants.ADMIN_AI_BASE_PATH + AdminAiApiContractConstants.MODEL_ROUTING_EFFECTIVE_PATH,
        AdminAiApiContractConstants.ADMIN_AI_BASE_PATH + AdminAiApiContractConstants.AUDIT_RUNS_PATH,
        AdminAiApiContractConstants.ADMIN_AI_BASE_PATH + AdminAiApiContractConstants.AUDIT_RUN_PATH,
        AdminAiApiContractConstants.ADMIN_AI_BASE_PATH + AdminAiApiContractConstants.AUDIT_STEP_PATH,
        AdminAiApiContractConstants.ADMIN_AI_BASE_PATH + AdminAiApiContractConstants.AUDIT_TOOL_RESULT_PATH,
        AdminAiApiContractConstants.ADMIN_USERS_BASE_PATH + AdminAiApiContractConstants.USER_AI_POLICY_PATH);

    assertTrue(mappedPaths.containsAll(expectedPaths), () -> "Missing mappings: " + expectedPaths.stream()
        .filter(path -> !mappedPaths.contains(path))
        .toList());
  }

  @Test
  void feedbackEndpointsAreMappedWhenDataSourceIsConfigured() {
    Set<String> mappedPaths = requestMappingHandlerMapping.getHandlerMethods().keySet().stream()
        .flatMap(mapping -> mapping.getPatternValues().stream())
        .collect(Collectors.toSet());

    Set<String> expectedPaths = Set.of(
        FeedbackApiContractConstants.BASE_PATH,
        FeedbackApiContractConstants.BASE_PATH + FeedbackApiContractConstants.THREAD_ID_PATH,
        FeedbackApiContractConstants.BASE_PATH + FeedbackApiContractConstants.MESSAGES_PATH,
        FeedbackApiContractConstants.BASE_PATH + FeedbackApiContractConstants.READ_PATH,
        AdminFeedbackApiContractConstants.BASE_PATH,
        AdminFeedbackApiContractConstants.BASE_PATH + AdminFeedbackApiContractConstants.THREAD_ID_PATH,
        AdminFeedbackApiContractConstants.BASE_PATH + AdminFeedbackApiContractConstants.MESSAGES_PATH,
        AdminFeedbackApiContractConstants.BASE_PATH + AdminFeedbackApiContractConstants.READ_PATH,
        AdminFeedbackApiContractConstants.BASE_PATH + AdminFeedbackApiContractConstants.STATUS_PATH,
        AdminOverviewApiContractConstants.BASE_PATH);

    assertTrue(mappedPaths.containsAll(expectedPaths), () -> "Missing mappings: " + expectedPaths.stream()
        .filter(path -> !mappedPaths.contains(path))
        .toList());
  }

  @Test
  void databaseBackupEndpointsAreMappedWhenDataSourceIsConfigured() {
    Set<String> mappedPaths = requestMappingHandlerMapping.getHandlerMethods().keySet().stream()
        .flatMap(mapping -> mapping.getPatternValues().stream())
        .collect(Collectors.toSet());

    Set<String> expectedPaths = Set.of(
        DatabaseBackupApiContractConstants.ADMIN_DATABASE_BASE_PATH
            + DatabaseBackupApiContractConstants.BACKUP_PATH,
        DatabaseBackupApiContractConstants.ADMIN_DATABASE_BASE_PATH
            + DatabaseBackupApiContractConstants.RESTORE_PATH);

    assertTrue(mappedPaths.containsAll(expectedPaths), () -> "Missing mappings: " + expectedPaths.stream()
        .filter(path -> !mappedPaths.contains(path))
        .toList());
  }

  @Test
  void nonAdminCannotAccessAdminUsersEndpoint() throws Exception {
    mockMvc.perform(get(AdminUserApiContractConstants.ADMIN_USERS_BASE_PATH)
            .with(authentication(authenticationToken("ROLE_USER"))))
        .andExpect(status().isForbidden());
  }

  @Test
  void disabledDatabaseBackupReturnsItsControlledErrorCode() throws Exception {
    mockMvc.perform(get(DatabaseBackupApiContractConstants.ADMIN_DATABASE_BASE_PATH
            + DatabaseBackupApiContractConstants.BACKUP_PATH)
            .with(authentication(authenticationToken("ROLE_ADMIN"))))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.error.code").value(DatabaseBackupErrorCode.DATABASE_BACKUP_FAILED.name()));
  }

  @Test
  void nonAdminCannotAccessBetaAccessOrPasswordResetEndpoints() throws Exception {
    mockMvc.perform(get(BetaAccessApiContractConstants.BASE_PATH)
            .with(authentication(authenticationToken("ROLE_USER"))))
        .andExpect(status().isForbidden());

    mockMvc.perform(get(AdminAiApiContractConstants.ADMIN_AI_BASE_PATH + AdminAiApiContractConstants.SETTINGS_PATH)
            .with(authentication(authenticationToken("ROLE_USER"))))
        .andExpect(status().isForbidden());

    mockMvc.perform(get(AdminAiApiContractConstants.ADMIN_AI_BASE_PATH + AdminAiApiContractConstants.AUDIT_RUNS_PATH)
            .with(authentication(authenticationToken("ROLE_USER"))))
        .andExpect(status().isForbidden());

    mockMvc.perform(get(DatabaseBackupApiContractConstants.ADMIN_DATABASE_BASE_PATH
            + DatabaseBackupApiContractConstants.BACKUP_PATH)
            .with(authentication(authenticationToken("ROLE_USER"))))
        .andExpect(status().isForbidden());

    mockMvc.perform(post(AdminPasswordResetApiContractConstants.BASE_PATH + "/42/password-reset")
            .with(csrf())
            .with(authentication(authenticationToken("ROLE_USER"))))
        .andExpect(status().isForbidden());
  }

  @Test
  void aiGovernanceAuthorityDoesNotGrantReadOnlyAuditAccess() throws Exception {
    String auditBase = AdminAiApiContractConstants.ADMIN_AI_BASE_PATH;
    UsernamePasswordAuthenticationToken governanceManager = authenticationToken("ai-governance:manage");

    mockMvc.perform(get(auditBase + AdminAiApiContractConstants.AUDIT_RUNS_PATH)
            .with(authentication(governanceManager)))
        .andExpect(status().isForbidden());
    mockMvc.perform(get(auditBase + AdminAiApiContractConstants.AUDIT_RUN_PATH.replace("{runId}", "17"))
            .with(authentication(governanceManager)))
        .andExpect(status().isForbidden());
    mockMvc.perform(get(auditBase + AdminAiApiContractConstants.AUDIT_STEP_PATH
            .replace("{runId}", "17").replace("{stepIndex}", "1"))
            .with(authentication(governanceManager)))
        .andExpect(status().isForbidden());
    mockMvc.perform(get(auditBase + AdminAiApiContractConstants.AUDIT_TOOL_RESULT_PATH
            .replace("{runId}", "17").replace("{toolCallId}", "call-1"))
            .with(authentication(governanceManager)))
        .andExpect(status().isForbidden());
  }

  @Test
  void newMutatingEndpointsRequireCsrfToken() throws Exception {
    mockMvc.perform(patch(BetaAccessApiContractConstants.BASE_PATH + BetaAccessApiContractConstants.SETTINGS_PATH)
            .with(authentication(authenticationToken("ROLE_ADMIN"))))
        .andExpect(status().isForbidden());
    mockMvc.perform(post(BetaAccessApiContractConstants.BASE_PATH + BetaAccessApiContractConstants.EMAILS_PATH)
            .with(authentication(authenticationToken("ROLE_ADMIN"))))
        .andExpect(status().isForbidden());
    mockMvc.perform(delete(BetaAccessApiContractConstants.BASE_PATH + "/emails/7")
            .with(authentication(authenticationToken("ROLE_ADMIN"))))
        .andExpect(status().isForbidden());
    mockMvc.perform(post(AdminPasswordResetApiContractConstants.BASE_PATH + "/42/password-reset")
            .with(authentication(authenticationToken("ROLE_ADMIN"))))
        .andExpect(status().isForbidden());
    mockMvc.perform(post(AuthSecurityPaths.AUTH_COMPLETE_RESET_PATH)
            .with(authentication(authenticationToken("ROLE_USER"))))
        .andExpect(status().isForbidden());
    mockMvc.perform(patch(AdminAiApiContractConstants.ADMIN_AI_BASE_PATH + AdminAiApiContractConstants.SETTINGS_PATH)
            .with(authentication(authenticationToken("ROLE_ADMIN"))))
        .andExpect(status().isForbidden());
    mockMvc.perform(post(DatabaseBackupApiContractConstants.ADMIN_DATABASE_BASE_PATH
            + DatabaseBackupApiContractConstants.RESTORE_PATH)
            .with(authentication(authenticationToken("ROLE_ADMIN"))))
        .andExpect(status().isForbidden());
  }

  @Test
  void invalidAdminUserStatusQueryUsesAdminErrorCode() throws Exception {
    mockMvc.perform(get(AdminUserApiContractConstants.ADMIN_USERS_BASE_PATH)
            .param("status", "BOGUS")
            .with(authentication(authenticationToken("ROLE_ADMIN"))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value(AdminUserApiContractConstants.USER_STATUS_INVALID));
  }

  @Test
  void malformedAdminUserStatusBodyUsesGlobalRequestBodyErrorCode() throws Exception {
    mockMvc.perform(patch(AdminUserApiContractConstants.ADMIN_USERS_BASE_PATH + "/42/status")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{")
            .with(csrf())
            .with(authentication(authenticationToken("ROLE_ADMIN"))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value(LocalizedApiExceptionHandler.REQUEST_BODY_INVALID_CODE));
  }

  private static UsernamePasswordAuthenticationToken authenticationToken(String authority) {
    return new UsernamePasswordAuthenticationToken(
        "42",
        "n/a",
        java.util.List.of(new SimpleGrantedAuthority(authority)));
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class TestConfig {

    @Bean
    AgentRuntime agentRuntime() {
      return mock(AgentRuntime.class);
    }

    @Bean
    DataSource dataSource() throws SQLException {
      DataSource dataSource = mock(DataSource.class);
      Connection connection = mock(Connection.class);
      DatabaseMetaData metaData = mock(DatabaseMetaData.class);
      PreparedStatement statement = mock(PreparedStatement.class);
      when(dataSource.getConnection()).thenReturn(connection);
      when(connection.getMetaData()).thenReturn(metaData);
      when(connection.prepareStatement(anyString())).thenReturn(statement);
      when(metaData.getDatabaseProductName()).thenReturn("PostgreSQL");
      when(statement.executeUpdate()).thenReturn(1);
      return dataSource;
    }
  }
}
