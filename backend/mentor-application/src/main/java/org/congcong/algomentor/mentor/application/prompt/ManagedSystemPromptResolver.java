package org.congcong.algomentor.mentor.application.prompt;

/** 系统提示词解析边界，业务仅传入已注册的 definition。 */
public interface ManagedSystemPromptResolver {

  ResolvedSystemPromptSnapshot resolve(ManagedSystemPromptDefinition definition, long userId);
}
