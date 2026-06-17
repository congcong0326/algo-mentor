package org.congcong.algomentor.mentor.application.conversation;

import java.util.List;

public interface ConversationRepository {

  ConversationDraft createOrReuseRun(AgentConversationCommand command);

  List<ConversationMessage> recentMessages(long taskId, int messageLimit);
}
