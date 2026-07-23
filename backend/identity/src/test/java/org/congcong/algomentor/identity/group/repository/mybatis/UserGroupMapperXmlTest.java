package org.congcong.algomentor.identity.group.repository.mybatis;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class UserGroupMapperXmlTest {

  @Test
  void mapperDefinesEffectiveMembershipAndLogicalDeleteQueries() throws Exception {
    ClassPathResource resource = new ClassPathResource(
        "mapper/identity/group/UserGroupMapper.xml");

    assertThat(resource.exists()).isTrue();
    assertThat(resource.getContentAsString(StandardCharsets.UTF_8))
        .contains("id=\"lockGroupById\"")
        .contains("for update")
        .contains("id=\"markGroupDeleted\"")
        .contains("status = 'DELETED'")
        .contains("id=\"deleteAllMemberships\"")
        .contains("id=\"upsertMembership\"")
        .contains("on conflict (user_id, group_id) do update")
        .contains("m.joined_at &lt;= #{now}")
        .contains("m.expires_at is null or m.expires_at &gt; #{now}")
        .contains("id=\"findActiveMembershipsByUserIds\"");
  }
}
