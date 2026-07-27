package org.congcong.algomentor.mentor.application.prompt;

import java.util.List;
import org.congcong.algomentor.ai.governance.model.AiBusinessScenario;

/** 由业务代码注册的固定系统提示词定义。 */
public interface ManagedSystemPromptDefinition {

  AiBusinessScenario scenario();

  String typeCode();

  String sourceRevision();

  SystemPromptSnapshotScope snapshotScope();

  ManagedSystemPromptTypeDescriptor descriptor();

  List<ManagedSystemPromptSectionDefinition> sections();
}
