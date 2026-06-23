package org.congcong.algomentor.ai.governance.trace;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;
import org.congcong.algomentor.ai.governance.model.AiActor;
import org.congcong.algomentor.auth.model.AuthRole;
import org.junit.jupiter.api.Test;

class AiTraceAccessPolicyTest {

  @Test
  void allowsAdminToReadFullTrace() {
    AiTraceAccessPolicy policy = new AiTraceAccessPolicy();
    AiActor admin = new AiActor(1L, Set.of(AuthRole.ADMIN), true);

    assertThatCode(() -> policy.assertCanReadFullTrace(admin)).doesNotThrowAnyException();
  }

  @Test
  void rejectsNonAdminEvenForAuthenticatedUser() {
    AiTraceAccessPolicy policy = new AiTraceAccessPolicy();
    AiActor user = new AiActor(2L, Set.of(AuthRole.USER), true);

    assertThatThrownBy(() -> policy.assertCanReadFullTrace(user))
        .isInstanceOf(AiTraceAccessDeniedException.class)
        .hasMessageContaining("Only administrators can read full AI trace content");
  }
}
