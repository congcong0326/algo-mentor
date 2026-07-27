package org.congcong.algomentor.mentor.application.prompt;

import java.util.Objects;

/** 不依赖数据库或通用策略模块的代码默认 resolver。 */
public final class CodeDefaultManagedSystemPromptResolver implements ManagedSystemPromptResolver {

  private final ManagedSystemPromptDefinitionRegistry registry;
  private final SystemPromptResolutionSource source;

  public CodeDefaultManagedSystemPromptResolver(ManagedSystemPromptDefinitionRegistry registry) {
    this(registry, SystemPromptResolutionSource.CODE_POLICY_UNAVAILABLE);
  }

  public CodeDefaultManagedSystemPromptResolver(
      ManagedSystemPromptDefinitionRegistry registry,
      SystemPromptResolutionSource source
  ) {
    this.registry = Objects.requireNonNull(registry, "registry must not be null");
    this.source = Objects.requireNonNull(source, "source must not be null");
  }

  @Override
  public ResolvedSystemPromptSnapshot resolve(ManagedSystemPromptDefinition definition, long userId) {
    if (userId < 1) {
      throw new IllegalArgumentException("userId must be positive");
    }
    registry.requireRegisteredInstance(definition);
    return registry.codeDefaultSnapshot(definition, source);
  }
}
