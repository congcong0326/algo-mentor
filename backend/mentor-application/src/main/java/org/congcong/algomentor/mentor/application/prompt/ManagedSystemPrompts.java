package org.congcong.algomentor.mentor.application.prompt;

/** 无 Spring 或数据库环境使用的默认 definition registry 与 resolver。 */
public final class ManagedSystemPrompts {

  private static final ManagedSystemPromptDefinitionRegistry DEFAULT_REGISTRY =
      new ManagedSystemPromptDefinitionRegistry(ManagedSystemPromptDefinitions.all());
  private static final ManagedSystemPromptResolver DEFAULT_RESOLVER =
      new CodeDefaultManagedSystemPromptResolver(DEFAULT_REGISTRY);

  private ManagedSystemPrompts() {
  }

  public static ManagedSystemPromptDefinitionRegistry defaultRegistry() {
    return DEFAULT_REGISTRY;
  }

  public static ManagedSystemPromptResolver defaultResolver() {
    return DEFAULT_RESOLVER;
  }
}
