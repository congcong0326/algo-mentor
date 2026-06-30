package org.congcong.algomentor.api.controller;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.congcong.algomentor.api.MentorApiApplication;
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

@SpringBootTest(
    classes = {MentorApiApplication.class, AdminUserEndpointSecurityTest.TestConfig.class},
    properties = "spring.datasource.url=jdbc:postgresql://localhost/algo_mentor_test")
@AutoConfigureMockMvc
class AdminUserEndpointSecurityTest {

  @Autowired
  private MockMvc mockMvc;

  @Test
  void nonAdminCannotAccessAdminUsersEndpoint() throws Exception {
    mockMvc.perform(get(AdminUserApiContractConstants.ADMIN_USERS_BASE_PATH)
            .with(authentication(authenticationToken("ROLE_USER"))))
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
