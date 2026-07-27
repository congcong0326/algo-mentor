package org.congcong.algomentor.mentor.application.prompt;

import org.congcong.algomentor.llm.core.request.LlmMessage;

/** 受管理固定 system message 的唯一创建入口。 */
public final class ManagedSystemMessageFactory {

  private ManagedSystemMessageFactory() {
  }

  public static LlmMessage system(ResolvedSystemPromptSnapshot snapshot, String sectionKey) {
    return LlmMessage.system(snapshot.requireSection(sectionKey).text());
  }
}
