package org.congcong.algomentor.agent.core.runtime.context;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.congcong.algomentor.agent.core.runtime.model.AgentMessage;
import org.congcong.algomentor.agent.core.runtime.model.AgentRuntimeMetadataKeys;
import org.congcong.algomentor.llm.core.request.LlmMessage;
import org.junit.jupiter.api.Test;

class ContextAssemblerTest {

  @Test
  void delegatesToPromptAssemblyButKeepsLegacyMessageOrderAndMetadata() {
    ContextAssembler assembler = new ContextAssembler(new ContextAssemblyPolicy(1, 8_000, "legacy-policy", "v2"));
    List<AgentMessage> history = new ArrayList<>();
    history.add(message(1, 1, AgentMessage.Role.USER, "old user"));
    history.add(message(2, 2, AgentMessage.Role.ASSISTANT, "old assistant"));
    history.add(message(3, 3, AgentMessage.Role.USER, "recent user"));
    history.add(message(4, 4, AgentMessage.Role.ASSISTANT, "recent assistant"));

    AssembledContext context = assembler.assemble(
        "system prompt",
        "summary text",
        history,
        "current question");

    assertThat(context.messages())
        .extracting(LlmMessage::role)
        .containsExactly(
            LlmMessage.Role.SYSTEM,
            LlmMessage.Role.SYSTEM,
            LlmMessage.Role.USER,
            LlmMessage.Role.ASSISTANT,
            LlmMessage.Role.USER);
    assertThat(context.messages())
        .extracting(LlmMessage::text)
        .containsExactly(
            "system prompt",
            "Conversation summary:\nsummary text",
            "recent user",
            "recent assistant",
            "current question");
    assertThat(context.metadata())
        .containsEntry(AgentRuntimeMetadataKeys.CONTEXT_POLICY, "legacy-policy")
        .containsEntry(AgentRuntimeMetadataKeys.CONTEXT_POLICY_VERSION, "v2")
        .containsEntry(AgentRuntimeMetadataKeys.TOKEN_BUDGET, 8_000)
        .containsKey(AgentRuntimeMetadataKeys.TOKEN_ESTIMATE);
    assertThat(context.metadata().keySet())
        .doesNotContain("promptProfile", "promptContentHashes");
    assertThat(context.tokenEstimate()).isEqualTo(context.metadata().get(AgentRuntimeMetadataKeys.TOKEN_ESTIMATE));
  }

  @Test
  void omitsBlankSystemAndSummaryLikeLegacyAssembler() {
    AssembledContext context = new ContextAssembler().assemble(
        "",
        " ",
        List.of(),
        "current question");

    assertThat(context.messages())
        .extracting(LlmMessage::role)
        .containsExactly(LlmMessage.Role.USER);
    assertThat(context.messages().get(0).text()).isEqualTo("current question");
  }

  private AgentMessage message(long id, long sequenceNo, AgentMessage.Role role, String content) {
    return new AgentMessage(id, 11, sequenceNo, role, content, Instant.EPOCH);
  }
}
