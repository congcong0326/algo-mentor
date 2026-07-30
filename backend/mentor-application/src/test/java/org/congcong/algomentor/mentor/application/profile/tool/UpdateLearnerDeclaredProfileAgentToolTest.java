package org.congcong.algomentor.mentor.application.profile.tool;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Flow;
import org.congcong.algomentor.agent.core.AgentExecutionContext;
import org.congcong.algomentor.agent.core.AgentRunResult;
import org.congcong.algomentor.agent.core.AgentStreamEvent;
import org.congcong.algomentor.agent.core.runtime.api.AgentInvocation;
import org.congcong.algomentor.agent.core.runtime.api.AgentRuntime;
import org.congcong.algomentor.agent.core.runtime.model.AgentRuntimeMetadataKeys;
import org.congcong.algomentor.mentor.application.practice.PracticeChatPromptConstants;
import org.congcong.algomentor.mentor.application.profile.LearnerProfileDimension;
import org.congcong.algomentor.mentor.application.profile.LearnerProfileQueryService;
import org.congcong.algomentor.mentor.application.profile.LearnerProfileUpdateService;
import org.congcong.algomentor.mentor.application.profile.ai.DeclaredProfileUpdatePromptBuilder;
import org.congcong.algomentor.mentor.application.profile.ai.DeclaredProfileUpdateService;
import org.junit.jupiter.api.Test;

class UpdateLearnerDeclaredProfileAgentToolTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void exposesOnlyBatchArgumentsAndBuildsTrustedChildParentContext() throws Exception {
    CapturingService service = new CapturingService(DeclaredProfileUpdateResult.failed(List.of(
        LearnerProfileDimension.GOALS_AND_INTENTS)));
    UpdateLearnerDeclaredProfileAgentTool tool = new UpdateLearnerDeclaredProfileAgentTool(service, objectMapper);

    JsonNode result = tool.execute(objectMapper.readTree("""
        {"updates":[{"dimension":"GOALS_AND_INTENTS","statement":"Prepare interview","intent":"DECLARE"}]}
        """), context(42L));

    assertThat(tool.spec().name()).isEqualTo(LearnerDeclaredProfileToolContracts.TOOL_NAME);
    assertThat(tool.spec().strict()).isTrue();
    assertThat(tool.spec().inputSchema().toString())
        .contains(LearnerDeclaredProfileToolContracts.ARGUMENT_UPDATES)
        .doesNotContain("userId")
        .doesNotContain("tagId")
        .doesNotContain("revision");
    assertThat(service.userId).isEqualTo(42L);
    assertThat(service.parentRunDbId).isEqualTo(99L);
    assertThat(service.parentStepIndex).isEqualTo(4);
    assertThat(result.path(LearnerDeclaredProfileToolContracts.RESULT_FIELD_TYPE).asText())
        .isEqualTo(LearnerDeclaredProfileToolContracts.RESULT_TYPE);
    assertThat(result.path(LearnerDeclaredProfileToolContracts.RESULT_FIELD_STATUS).asText())
        .isEqualTo(DeclaredProfileUpdateResult.Status.FAILED.name());
  }

  @Test
  void returnsOrdinaryFailedJsonForInvalidArgumentsOrUntrustedParentContext() throws Exception {
    CapturingService service = new CapturingService(new DeclaredProfileUpdateResult(
        DeclaredProfileUpdateResult.Status.UPDATED,
        LearnerDeclaredProfileToolContracts.MESSAGE_UPDATED,
        List.of()));
    UpdateLearnerDeclaredProfileAgentTool tool = new UpdateLearnerDeclaredProfileAgentTool(service, objectMapper);

    JsonNode invalid = tool.execute(objectMapper.readTree("{\"userId\":9}"), context(42L));
    JsonNode nonPractice = tool.execute(objectMapper.readTree("""
        {"updates":[{"dimension":"GOALS_AND_INTENTS","statement":"Prepare interview","intent":"DECLARE"}]}
        """), new AgentExecutionContext("run-42", 4, Map.of(
            AgentRuntimeMetadataKeys.USER_ID, 42L,
            AgentRuntimeMetadataKeys.RUN_DB_ID, 99L,
            PracticeChatPromptConstants.METADATA_SCENARIO, "TOPIC_CHAT"), false));
    JsonNode missingParentRun = tool.execute(objectMapper.readTree("""
        {"updates":[{"dimension":"GOALS_AND_INTENTS","statement":"Prepare interview","intent":"DECLARE"}]}
        """), new AgentExecutionContext("run-42", 4, Map.of(
            AgentRuntimeMetadataKeys.USER_ID, 42L,
            PracticeChatPromptConstants.METADATA_SCENARIO, PracticeChatPromptConstants.SCENARIO), false));

    assertThat(invalid.path(LearnerDeclaredProfileToolContracts.RESULT_FIELD_STATUS).asText())
        .isEqualTo(DeclaredProfileUpdateResult.Status.FAILED.name());
    assertThat(nonPractice.path(LearnerDeclaredProfileToolContracts.RESULT_FIELD_STATUS).asText())
        .isEqualTo(DeclaredProfileUpdateResult.Status.FAILED.name());
    assertThat(missingParentRun.path(LearnerDeclaredProfileToolContracts.RESULT_FIELD_STATUS).asText())
        .isEqualTo(DeclaredProfileUpdateResult.Status.FAILED.name());
    assertThat(service.calls).isZero();
  }

  private AgentExecutionContext context(long userId) {
    return new AgentExecutionContext("run-42", 4, Map.of(
        AgentRuntimeMetadataKeys.USER_ID, userId,
        AgentRuntimeMetadataKeys.RUN_DB_ID, 99L,
        PracticeChatPromptConstants.METADATA_SCENARIO, PracticeChatPromptConstants.SCENARIO), false);
  }

  private static final class CapturingService extends DeclaredProfileUpdateService {
    private final DeclaredProfileUpdateResult result;
    private int calls;
    private long userId;
    private long parentRunDbId;
    private int parentStepIndex;

    private CapturingService(DeclaredProfileUpdateResult result) {
      super(
          new LearnerProfileQueryService(null),
          new LearnerProfileUpdateService(null, null, null),
          new NoopRuntime(),
          new DeclaredProfileUpdatePromptBuilder(),
          1,
          300);
      this.result = result;
    }

    @Override
    public DeclaredProfileUpdateResult update(
        long userId,
        DeclaredProfileUpdateRequest request,
        long parentRunDbId,
        int parentStepIndex
    ) {
      calls++;
      this.userId = userId;
      this.parentRunDbId = parentRunDbId;
      this.parentStepIndex = parentStepIndex;
      return result;
    }
  }

  private static final class NoopRuntime implements AgentRuntime {
    @Override
    public AgentRunResult execute(AgentInvocation<?> invocation) {
      throw new UnsupportedOperationException("runtime not used");
    }

    @Override
    public Flow.Publisher<AgentStreamEvent> stream(AgentInvocation<?> invocation) {
      throw new UnsupportedOperationException("stream not used");
    }
  }
}
