package org.congcong.algomentor.agent.core.prompt;

public interface PromptProfileResolver {

  PromptProfile resolve(PromptAssemblyRequest request);
}
