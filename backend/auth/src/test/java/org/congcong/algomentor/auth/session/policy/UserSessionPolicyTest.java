package org.congcong.algomentor.auth.session.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.congcong.algomentor.policy.type.GenericPolicyType;
import org.junit.jupiter.api.Test;

class UserSessionPolicyTest {

  @Test
  void acceptsPositiveValuesWithinServletSessionBounds() {
    UserSessionPolicy policy = new UserSessionPolicy(2, 86_400L);

    assertThat(policy.maxSessions()).isEqualTo(2);
    assertThat(policy.absoluteTimeoutSeconds()).isEqualTo(86_400L);
  }

  @Test
  void rejectsNonPositiveOrNonRepresentableValues() {
    assertThatThrownBy(() -> new UserSessionPolicy(0, 1))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(UserSessionPolicyConstraints.MAX_SESSIONS_FIELD);
    assertThatThrownBy(() -> new UserSessionPolicy(1, 0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(UserSessionPolicyConstraints.ABSOLUTE_TIMEOUT_SECONDS_FIELD);
    assertThatThrownBy(() -> new UserSessionPolicy(
        1, UserSessionPolicyConstraints.MAX_ABSOLUTE_TIMEOUT_SECONDS + 1))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(UserSessionPolicyConstraints.ABSOLUTE_TIMEOUT_SECONDS_FIELD);
  }

  @Test
  void rejectsUnknownOrNonIntegralJsonFields() throws Exception {
    GenericPolicyType<UserSessionPolicy> policyType = GenericPolicyType.of(
        AuthSessionPolicyConstants.TYPE_CODE,
        UserSessionPolicy.class,
        UserSessionPolicyConstraints::validateContent);
    ObjectMapper objectMapper = new ObjectMapper();

    assertThatThrownBy(() -> policyType.deserialize(objectMapper, objectMapper.readTree("""
        {"maxSessions": 2, "absoluteTimeoutSeconds": 86400, "newField": true}
        """)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> policyType.deserialize(objectMapper, objectMapper.readTree("""
        {"maxSessions": 2.5, "absoluteTimeoutSeconds": 86400}
        """)))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
