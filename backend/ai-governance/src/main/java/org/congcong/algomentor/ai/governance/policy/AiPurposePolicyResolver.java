package org.congcong.algomentor.ai.governance.policy;

public class AiPurposePolicyResolver {

  private final AiGovernanceProperties properties;

  public AiPurposePolicyResolver(AiGovernanceProperties properties) {
    this.properties = properties;
  }

  public AiGovernanceProperties properties() {
    return properties;
  }
}
