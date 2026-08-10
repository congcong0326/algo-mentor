package org.congcong.algomentor.mentor.application.practice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Map;
import java.util.Objects;
import org.congcong.algomentor.agent.core.AgentExecutionContext;
import org.congcong.algomentor.agent.core.AgentTool;
import org.congcong.algomentor.agent.core.runtime.model.AgentRuntimeMetadataKeys;
import org.congcong.algomentor.llm.core.tool.LlmToolSpec;
import org.congcong.algomentor.mentor.application.practice.coachsummary.CoachSummaryProposal;
import org.congcong.algomentor.mentor.application.practice.coachsummary.CoachSummaryProposalService;
import org.congcong.algomentor.mentor.application.review.ReviewContractConstants;

/** 为当前题生成可由用户在聊天消息中一次性采纳的教练总结候选。 */
public final class ProposeCurrentProblemCoachSummaryAgentTool implements AgentTool {

  private static final LlmToolSpec SPEC = new LlmToolSpec(
      ProposeCurrentProblemCoachSummaryAgentToolContracts.TOOL_NAME,
      """
          Create a complete coach-summary proposal for the active practice problem. Use this when the user asks to create, \
          update, replace, or save a coach summary. summaryMarkdown must be the exact complete Markdown that the user will \
          see in the assistant message and may apply with an inline button. This tool creates only a proposal and never \
          changes the saved coach summary by itself.
          """.strip(),
      inputSchema(),
      true);

  private final PracticeSessionRepository sessionRepository;
  private final CoachSummaryProposalService proposalService;

  public ProposeCurrentProblemCoachSummaryAgentTool(
      PracticeSessionRepository sessionRepository,
      CoachSummaryProposalService proposalService
  ) {
    this.sessionRepository = Objects.requireNonNull(sessionRepository, "sessionRepository must not be null");
    this.proposalService = Objects.requireNonNull(proposalService, "proposalService must not be null");
  }

  @Override
  public LlmToolSpec spec() {
    return SPEC;
  }

  @Override
  public JsonNode execute(JsonNode arguments, AgentExecutionContext context) {
    String summaryMarkdown = ProposeCurrentProblemCoachSummaryAgentToolContracts.summaryMarkdown(arguments).orElse(null);
    if (summaryMarkdown == null) {
      return failure(ProposeCurrentProblemCoachSummaryAgentToolContracts.FAILURE_INVALID_ARGUMENTS);
    }
    TrustedPracticeContext trustedContext = trustedContext(context);
    if (trustedContext.failureCode() != null) {
      return failure(trustedContext.failureCode());
    }

    try {
      PracticeSession session = sessionRepository.findSessionForUser(
              trustedContext.sessionId(), trustedContext.userId())
          .orElse(null);
      if (session == null) {
        return failure(ProposeCurrentProblemCoachSummaryAgentToolContracts.FAILURE_PRACTICE_SESSION_NOT_FOUND);
      }
      if (!matchesCurrentProblem(session, trustedContext)) {
        return failure(ProposeCurrentProblemCoachSummaryAgentToolContracts.FAILURE_CURRENT_PROBLEM_MISMATCH);
      }
      CoachSummaryProposal proposal = proposalService.propose(
          trustedContext.userId(),
          session,
          trustedContext.runDbId(),
          trustedContext.toolCallId(),
          summaryMarkdown);
      ObjectNode result = JsonNodeFactory.instance.objectNode();
      result.put(ProposeCurrentProblemCoachSummaryAgentToolContracts.FIELD_TYPE,
          ProposeCurrentProblemCoachSummaryAgentToolContracts.RESULT_TYPE);
      result.put(ProposeCurrentProblemCoachSummaryAgentToolContracts.FIELD_STATUS,
          ProposeCurrentProblemCoachSummaryAgentToolContracts.STATUS_PROPOSED);
      result.put(ProposeCurrentProblemCoachSummaryAgentToolContracts.FIELD_PROPOSAL_ID, proposal.id());
      result.put(ProposeCurrentProblemCoachSummaryAgentToolContracts.FIELD_PROBLEM_SLUG, proposal.problemSlug());
      result.put(ProposeCurrentProblemCoachSummaryAgentToolContracts.FIELD_SUMMARY_MARKDOWN,
          proposal.summaryMarkdown());
      result.put(ProposeCurrentProblemCoachSummaryAgentToolContracts.FIELD_OPERATION, proposal.operation().name());
      result.put(ProposeCurrentProblemCoachSummaryAgentToolContracts.FIELD_BASE_COACH_SUMMARY_REVISION,
          proposal.baseCoachSummaryRevision());
      return result;
    } catch (RuntimeException exception) {
      return failure(ProposeCurrentProblemCoachSummaryAgentToolContracts.FAILURE_INTERNAL);
    }
  }

  private TrustedPracticeContext trustedContext(AgentExecutionContext context) {
    if (context == null || context.toolCallId().isBlank()) {
      return TrustedPracticeContext.failed(ProposeCurrentProblemCoachSummaryAgentToolContracts.FAILURE_MISSING_METADATA);
    }
    Map<String, Object> metadata = context.requestMetadata();
    String scenario = nonBlank(metadata.get(PracticeChatPromptConstants.METADATA_SCENARIO));
    if (scenario == null) {
      return TrustedPracticeContext.failed(ProposeCurrentProblemCoachSummaryAgentToolContracts.FAILURE_MISSING_METADATA);
    }
    if (!PracticeChatPromptConstants.SCENARIO.equals(scenario)) {
      return TrustedPracticeContext.failed(ProposeCurrentProblemCoachSummaryAgentToolContracts.FAILURE_NOT_PRACTICE_CHAT);
    }
    Long userId = positiveLong(metadata.get(AgentRuntimeMetadataKeys.USER_ID));
    Long sessionId = positiveLong(metadata.get(PracticeChatPromptConstants.METADATA_PRACTICE_SESSION_ID));
    Long planId = positiveLong(metadata.get(PracticeChatPromptConstants.METADATA_PLAN_ID));
    Integer phaseIndex = positiveInteger(metadata.get(PracticeChatPromptConstants.METADATA_PHASE_INDEX));
    String problemSlug = nonBlank(metadata.get(PracticeChatPromptConstants.METADATA_PROBLEM_SLUG));
    Long runDbId = positiveLong(metadata.get(AgentRuntimeMetadataKeys.RUN_DB_ID));
    if (userId == null || sessionId == null || planId == null || phaseIndex == null || problemSlug == null
        || runDbId == null) {
      return TrustedPracticeContext.failed(ProposeCurrentProblemCoachSummaryAgentToolContracts.FAILURE_MISSING_METADATA);
    }
    return new TrustedPracticeContext(
        userId, sessionId, planId, phaseIndex, problemSlug, runDbId, context.toolCallId(), null);
  }

  private boolean matchesCurrentProblem(PracticeSession session, TrustedPracticeContext context) {
    return session.id() == context.sessionId()
        && session.userId() == context.userId()
        && session.planId() == context.planId()
        && session.phaseIndex() == context.phaseIndex()
        && session.problemSlug().equals(context.problemSlug());
  }

  private Long positiveLong(Object value) {
    if (value instanceof Number number) {
      return number.longValue() > 0 ? number.longValue() : null;
    }
    if (value instanceof CharSequence text) {
      try {
        long parsed = Long.parseLong(text.toString().trim());
        return parsed > 0 ? parsed : null;
      } catch (NumberFormatException ignored) {
        return null;
      }
    }
    return null;
  }

  private Integer positiveInteger(Object value) {
    Long parsed = positiveLong(value);
    return parsed == null || parsed > Integer.MAX_VALUE ? null : parsed.intValue();
  }

  private String nonBlank(Object value) {
    return value == null || value.toString().isBlank() ? null : value.toString().trim();
  }

  private JsonNode failure(String failureCode) {
    ObjectNode result = JsonNodeFactory.instance.objectNode();
    result.put(ProposeCurrentProblemCoachSummaryAgentToolContracts.FIELD_TYPE,
        ProposeCurrentProblemCoachSummaryAgentToolContracts.RESULT_TYPE);
    result.put(ProposeCurrentProblemCoachSummaryAgentToolContracts.FIELD_STATUS,
        ProposeCurrentProblemCoachSummaryAgentToolContracts.STATUS_FAILED);
    result.put(ProposeCurrentProblemCoachSummaryAgentToolContracts.FIELD_FAILURE_CODE, failureCode);
    result.put(ProposeCurrentProblemCoachSummaryAgentToolContracts.FIELD_MESSAGE,
        "The coach summary proposal could not be created.");
    return result;
  }

  private static ObjectNode inputSchema() {
    ObjectNode schema = JsonNodeFactory.instance.objectNode();
    schema.put("type", "object");
    ObjectNode properties = schema.putObject("properties");
    ObjectNode summary = properties.putObject(
        ProposeCurrentProblemCoachSummaryAgentToolContracts.ARGUMENT_SUMMARY_MARKDOWN);
    summary.put("type", "string");
    summary.put("minLength", 1);
    summary.put("maxLength", ReviewContractConstants.NOTE_MARKDOWN_MAX_CHARS);
    schema.putArray("required")
        .add(ProposeCurrentProblemCoachSummaryAgentToolContracts.ARGUMENT_SUMMARY_MARKDOWN);
    schema.put("additionalProperties", false);
    return schema;
  }

  private record TrustedPracticeContext(
      long userId,
      long sessionId,
      long planId,
      int phaseIndex,
      String problemSlug,
      long runDbId,
      String toolCallId,
      String failureCode
  ) {
    private static TrustedPracticeContext failed(String failureCode) {
      return new TrustedPracticeContext(0, 0, 0, 0, "", 0, "", failureCode);
    }
  }
}
