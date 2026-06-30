package org.congcong.algomentor.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.congcong.algomentor.identity.controller.AdminUserController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;

@SpringBootTest(properties = "spring.datasource.url=jdbc:postgresql://localhost/algo_mentor_test")
class MentorApiApplicationTest {

  @Autowired
  private ClientRegistrationRepository clientRegistrationRepository;

  @Autowired
  private AdminUserController adminUserController;

  @Test
  void contextLoads() {
  }

  @Test
  void applicationContextLoadsAdminUserControllerFromIdentity() {
    assertThat(adminUserController).isNotNull();
  }

  @Test
  void googleOidcProviderHasJwkSetUri() {
    ClientRegistration google = clientRegistrationRepository.findByRegistrationId("google");

    assertThat(google).isNotNull();
    assertThat(google.getProviderDetails().getJwkSetUri())
        .isEqualTo("https://www.googleapis.com/oauth2/v3/certs");
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
