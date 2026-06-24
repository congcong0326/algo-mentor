package org.congcong.algomentor.agent.core.prompt;

import java.util.List;
import org.congcong.algomentor.llm.core.request.LlmMessage;

public interface PromptMessageNormalizer {

  List<LlmMessage> normalize(PromptProfile profile, List<RenderedPromptSection> renderedSections);
}
