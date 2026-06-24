package org.congcong.algomentor.agent.core.runtime.context;

import java.util.Set;
import org.congcong.algomentor.agent.core.prompt.PromptAssemblyRequest;
import org.congcong.algomentor.agent.core.prompt.PromptProfile;
import org.congcong.algomentor.agent.core.prompt.PromptProfileResolver;

final class LegacyContextPromptProfileResolver implements PromptProfileResolver {

  private final ContextAssemblyPolicy policy;

  LegacyContextPromptProfileResolver(ContextAssemblyPolicy policy) {
    this.policy = policy == null ? ContextAssemblyPolicy.defaultPolicy() : policy;
  }

  @Override
  public PromptProfile resolve(PromptAssemblyRequest request) {
    return new PromptProfile(
        LegacyContextPromptConstants.PROFILE_ID,
        policy.policyVersion(),
        policy.policyName(),
        policy.policyVersion(),
        request.tokenBudget() > 0 ? request.tokenBudget() : policy.tokenBudget(),
        Set.of(LegacyContextPromptConstants.CURRENT_USER_SECTION_ID),
        null);
  }
}
