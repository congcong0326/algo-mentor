package org.congcong.algomentor.policy.controller.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.util.List;
import org.congcong.algomentor.policy.service.EffectiveGenericPolicyQueryService;
import org.congcong.algomentor.policy.service.GenericPolicyErrorCode;
import org.congcong.algomentor.policy.service.GenericPolicyException;
import org.congcong.algomentor.policy.type.GenericPolicyType;
import org.congcong.algomentor.policy.type.GenericPolicyTypeExposure;
import org.congcong.algomentor.policy.type.GenericPolicyTypeRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;

class EffectiveGenericPolicyControllerTest {

  @Test
  void rejectsCurrentUserReadsForInternalOnlyPolicyTypes() {
    GenericPolicyType<String> internalType = GenericPolicyType.of(
        "ai.system-prompt.demo.v1",
        String.class,
        (json, content) -> { },
        GenericPolicyTypeExposure.INTERNAL_ONLY);
    EffectiveGenericPolicyController controller = new EffectiveGenericPolicyController(
        mock(EffectiveGenericPolicyQueryService.class),
        new GenericPolicyTypeRegistry(List.of(internalType)));

    assertThatThrownBy(() -> controller.effective("ai.system-prompt.demo.v1", mock(Authentication.class)))
        .isInstanceOf(GenericPolicyException.class)
        .extracting(error -> ((GenericPolicyException) error).code())
        .isEqualTo(GenericPolicyErrorCode.POLICY_ACCESS_DENIED);
  }
}
