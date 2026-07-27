package org.congcong.algomentor.mentor.application.prompt;

import java.util.List;

/** 由业务代码注册的固定系统提示词定义。 */
public interface ManagedSystemPromptDefinition {

  String typeCode();

  String sourceRevision();

  SystemPromptSnapshotScope snapshotScope();

  ManagedSystemPromptTypeDescriptor descriptor();

  List<ManagedSystemPromptSectionDefinition> sections();
}
