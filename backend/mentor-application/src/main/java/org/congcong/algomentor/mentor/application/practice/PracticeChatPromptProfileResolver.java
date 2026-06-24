package org.congcong.algomentor.mentor.application.practice;

import java.util.Set;
import org.congcong.algomentor.agent.core.prompt.PromptAssemblyRequest;
import org.congcong.algomentor.agent.core.prompt.PromptProfile;
import org.congcong.algomentor.agent.core.prompt.PromptProfileResolver;
import org.congcong.algomentor.agent.core.prompt.PromptSlot;

public class PracticeChatPromptProfileResolver implements PromptProfileResolver {

  private static final PromptProfile PROFILE = new PromptProfile(
      PracticeChatPromptConstants.PROFILE_ID,
      PracticeChatPromptConstants.PROFILE_VERSION,
      PracticeChatPromptConstants.POLICY_NAME,
      PracticeChatPromptConstants.POLICY_VERSION,
      PracticeChatPromptConstants.DEFAULT_TOKEN_BUDGET,
      Set.of(
          PracticeChatPromptConstants.SECTION_BASE_INSTRUCTION,
          PracticeChatPromptConstants.SECTION_SCENARIO_POLICY,
          PracticeChatPromptConstants.SECTION_RUNTIME_CONTEXT,
          PracticeChatPromptConstants.SECTION_CURRENT_USER_MESSAGE),
      PromptSlot.canonicalOrder());

  @Override
  public PromptProfile resolve(PromptAssemblyRequest request) {
    if (!PracticeChatPromptConstants.SCENARIO.equals(request.scenario())) {
      throw new IllegalArgumentException("Unsupported practice chat prompt scenario: " + request.scenario());
    }
    if (request.profileHint() != null && !PracticeChatPromptConstants.PROFILE_ID.equals(request.profileHint())) {
      throw new IllegalArgumentException("Unsupported practice chat prompt profile: " + request.profileHint());
    }
    return PROFILE;
  }
}
