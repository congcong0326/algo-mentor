package org.congcong.algomentor.mentor.application.profile.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.congcong.algomentor.mentor.application.profile.LearnerMemoryClaimDimension;
import org.congcong.algomentor.mentor.application.profile.tool.DeclaredProfileUpdateIntent;
import org.congcong.algomentor.mentor.application.profile.tool.DeclaredProfileUpdateRequest;
import org.congcong.algomentor.mentor.application.profile.tool.LearnerDeclaredProfileToolContracts;
import org.junit.jupiter.api.Test;

class DeclaredProfileUpdateServiceTest {

  @Test
  void derivesStableChildIdempotencyKeyFromTheTrustedParentAndNormalizedRequest() {
    DeclaredProfileUpdateRequest first = request(" Prepare interview ");
    DeclaredProfileUpdateRequest equivalent = request("Prepare   interview");

    String key = DeclaredProfileUpdateService.childIdempotencyKey(401L, 3, first);

    assertThat(key).isEqualTo(DeclaredProfileUpdateService.childIdempotencyKey(401L, 3, equivalent));
    assertThat(key).startsWith(LearnerDeclaredProfileToolContracts.CHILD_IDEMPOTENCY_KEY_PREFIX);
    assertThat(key).doesNotContain(first.updates().get(0).statement());
  }

  private DeclaredProfileUpdateRequest request(String statement) {
    return new DeclaredProfileUpdateRequest(List.of(new DeclaredProfileUpdateRequest.Item(
        LearnerMemoryClaimDimension.GOALS_AND_INTENTS, statement, DeclaredProfileUpdateIntent.DECLARE)));
  }
}
