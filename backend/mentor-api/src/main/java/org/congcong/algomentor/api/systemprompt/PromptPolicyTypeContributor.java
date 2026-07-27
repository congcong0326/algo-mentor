package org.congcong.algomentor.api.systemprompt;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.congcong.algomentor.mentor.application.prompt.ManagedSystemPromptDefinition;
import org.congcong.algomentor.mentor.application.prompt.ManagedSystemPromptDefinitionRegistry;
import org.congcong.algomentor.mentor.application.prompt.ManagedSystemPromptPolicyContent;
import org.congcong.algomentor.policy.type.GenericPolicyType;
import org.congcong.algomentor.policy.type.GenericPolicyTypeContributor;
import org.congcong.algomentor.policy.type.GenericPolicyTypeExposure;

/** 将代码注册的系统提示词 definition 映射为内部通用策略类型。 */
public final class PromptPolicyTypeContributor implements GenericPolicyTypeContributor {

  private final Map<String, GenericPolicyType<ManagedSystemPromptPolicyContent>> typesByCode;

  public PromptPolicyTypeContributor(ManagedSystemPromptDefinitionRegistry definitionRegistry) {
    Map<String, GenericPolicyType<ManagedSystemPromptPolicyContent>> values = new LinkedHashMap<>();
    for (ManagedSystemPromptDefinition definition : definitionRegistry.definitions()) {
      GenericPolicyType<ManagedSystemPromptPolicyContent> type = GenericPolicyType.of(
          definition.typeCode(),
          ManagedSystemPromptPolicyContent.class,
          (json, content) -> definitionRegistry.validatePolicyContent(definition, content),
          GenericPolicyTypeExposure.INTERNAL_ONLY);
      values.put(definition.typeCode(), type);
    }
    this.typesByCode = Collections.unmodifiableMap(new LinkedHashMap<>(values));
  }

  @Override
  public Collection<GenericPolicyType<?>> policyTypes() {
    return java.util.List.copyOf(typesByCode.values());
  }

  public GenericPolicyType<ManagedSystemPromptPolicyContent> require(ManagedSystemPromptDefinition definition) {
    GenericPolicyType<ManagedSystemPromptPolicyContent> type = typesByCode.get(definition.typeCode());
    if (type == null) {
      throw new IllegalArgumentException("Prompt policy type is not registered: " + definition.typeCode());
    }
    return type;
  }
}
