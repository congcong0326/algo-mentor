package org.congcong.algomentor.identity.repository.mybatis;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class IdentityUserMapperXmlTest {

  @Test
  void mapperDefinesAdminSearchStatusUpdatesAndSoftDelete() throws Exception {
    ClassPathResource resource = new ClassPathResource("mapper/identity/IdentityUserMapper.xml");

    assertThat(resource.exists()).isTrue();

    String xml = resource.getContentAsString(StandardCharsets.UTF_8);
    assertThat(xml)
        .contains("id=\"searchUsers\"")
        .contains("id=\"countUsers\"")
        .contains("id=\"findUserById\"")
        .contains("id=\"findUserByEmailNormalized\"")
        .contains("id=\"insertUser\"")
        .contains("id=\"insertUserRole\"")
        .contains("id=\"findRoles\"")
        .contains("id=\"updateUserStatus\"")
        .contains("id=\"softDeleteUser\"")
        .contains("deleted_at")
        .contains("deleted_by");
  }
}
