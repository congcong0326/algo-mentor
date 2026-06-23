package org.congcong.algomentor.ai.governance.policy;

import org.congcong.algomentor.ai.governance.model.AiPurpose;

public class AiPurposePolicyResolver {

  private final AiGovernanceProperties properties;

  public AiPurposePolicyResolver(AiGovernanceProperties properties) {
    this.properties = properties;
  }

  public AiGovernanceProperties properties() {
    return properties;
  }

  public AiPurposePolicy resolve(AiPurpose purpose) {
    AiGovernanceProperties.PurposeProperties purposeProperties =
        purpose == null ? null : properties.getPurposes().get(purpose);
    if (purposeProperties == null) {
      throw new IllegalArgumentException("Unsupported AI purpose: " + purpose);
    }
    return purposeProperties.toPolicy();
  }
}
