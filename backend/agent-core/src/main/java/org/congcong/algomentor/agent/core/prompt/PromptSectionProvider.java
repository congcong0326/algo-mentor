package org.congcong.algomentor.agent.core.prompt;

import java.util.List;

public interface PromptSectionProvider {

  List<PromptSection> sections(PromptAssemblyRequest request, PromptProfile profile);
}
