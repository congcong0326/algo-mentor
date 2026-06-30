package org.congcong.algomentor.api.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
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
            .with(user("42").roles("USER")))
        .andExpect(status().isForbidden());
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class TestConfig {

    @Bean
    DataSource dataSource() throws SQLException {
      DataSource dataSource = mock(DataSource.class);
      Connection connection = mock(Connection.class);
      DatabaseMetaData metaData = mock(DatabaseMetaData.class);
      when(dataSource.getConnection()).thenReturn(connection);
      when(connection.getMetaData()).thenReturn(metaData);
      when(metaData.getDatabaseProductName()).thenReturn("PostgreSQL");
      return dataSource;
    }
  }
}
