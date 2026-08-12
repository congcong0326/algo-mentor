package org.congcong.algomentor.api.controller.admin.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.congcong.algomentor.agent.core.runtime.audit.AgentAuditQuery;
import org.congcong.algomentor.agent.core.runtime.audit.AgentAuditRunDetail;
import org.congcong.algomentor.agent.core.runtime.audit.AgentAuditRunFilter;
import org.congcong.algomentor.agent.core.runtime.audit.AgentAuditRunPage;
import org.congcong.algomentor.agent.core.runtime.audit.AgentAuditRunSort;
import org.congcong.algomentor.agent.core.runtime.audit.AgentAuditSortDirection;
import org.congcong.algomentor.agent.core.runtime.audit.AgentAuditRunSummary;
import org.congcong.algomentor.agent.core.runtime.audit.AgentAuditStepDetail;
import org.congcong.algomentor.agent.core.runtime.audit.AgentAuditStepSummary;
import org.congcong.algomentor.agent.core.runtime.audit.AgentAuditToolResult;
import org.congcong.algomentor.agent.core.runtime.audit.AgentAuditUsage;
import org.congcong.algomentor.common.admin.audit.AdminAuditAction;
import org.congcong.algomentor.common.admin.audit.AdminAuditMetadataKey;
import org.congcong.algomentor.common.admin.audit.AdminOperationAuditEvent;
import org.congcong.algomentor.common.admin.audit.AdminOperationAuditRecorder;
import org.congcong.algomentor.identity.repository.IdentityUserRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.server.ResponseStatusException;

class AdminAiAuditControllerTest {

  private final AgentAuditQuery auditQuery = mock(AgentAuditQuery.class);
  private final IdentityUserRepository users = mock(IdentityUserRepository.class);
  private final AdminOperationAuditRecorder recorder = mock(AdminOperationAuditRecorder.class);
  private final AdminAiAuditController controller = new AdminAiAuditController(auditQuery, users, recorder);

  @Test
  void passesAllListFiltersIntoReadOnlyQuery() {
    when(auditQuery.findRuns(any())).thenReturn(new AgentAuditRunPage(List.of(), 0, 2, 25));

    var response = controller.runs(
        "2026-08-01T00:00:00Z", "2026-08-02T00:00:00Z", 2, 25, 7L, " PRACTICE_CHAT ", " LEARNING ", " practice ",
        11L, 13L, 17L, " openai ", " gpt-test ", " succeeded ", " stop ", true, false, true, false, 200L, 800L, 0.2D, 0.8D,
        "overBudget", "asc");

    assertThat(response.success()).isTrue();
    ArgumentCaptor<AgentAuditRunFilter> filter = ArgumentCaptor.forClass(AgentAuditRunFilter.class);
    verify(auditQuery).findRuns(filter.capture());
    assertThat(filter.getValue())
        .extracting(
            AgentAuditRunFilter::page, AgentAuditRunFilter::pageSize, AgentAuditRunFilter::userId,
            AgentAuditRunFilter::scenario, AgentAuditRunFilter::purpose, AgentAuditRunFilter::source,
            AgentAuditRunFilter::taskId, AgentAuditRunFilter::turnId,
            AgentAuditRunFilter::runId, AgentAuditRunFilter::provider, AgentAuditRunFilter::model,
            AgentAuditRunFilter::status, AgentAuditRunFilter::finishReason, AgentAuditRunFilter::hasTools,
            AgentAuditRunFilter::hasCompaction, AgentAuditRunFilter::overBudget, AgentAuditRunFilter::providerError,
            AgentAuditRunFilter::minCachedTokens, AgentAuditRunFilter::maxCachedTokens,
            AgentAuditRunFilter::minCacheRatio, AgentAuditRunFilter::maxCacheRatio,
            AgentAuditRunFilter::sort, AgentAuditRunFilter::direction)
        .containsExactly(2, 25, 7L, "PRACTICE_CHAT", "LEARNING", "practice", 11L, 13L, 17L, "openai", "gpt-test", "succeeded", "stop",
            true, false, true, false, 200L, 800L, 0.2D, 0.8D, AgentAuditRunSort.OVER_BUDGET, AgentAuditSortDirection.ASC);
  }

  @Test
  void rejectsInvalidPageAndToolResultRangeAsBadRequests() {
    assertBadRequest(() -> controller.runs(
        null, null, 0, 20, null, null, null, null, null, null, null, null, null, null,
        null, null, null, null, null, null, null, null, null, null, null));
    assertBadRequest(() -> controller.toolResult(17L, "call-1", true, -1, 20, administrator()));
    verifyNoInteractions(recorder);
  }

  @Test
  void rejectsSortFieldsOutsideTheAllowList() {
    assertBadRequest(() -> controller.runs(
        null, null, 1, 20, null, null, null, null, null, null, null, null, null, null,
        null, null, null, null, null, null, null, null, null, "r.id desc", "desc"));
  }

  @Test
  void returnsNotFoundWithoutRecordingAViewWhenRunDoesNotExist() {
    when(auditQuery.findRun(99L)).thenReturn(Optional.empty());

    ResponseStatusException exception = org.junit.jupiter.api.Assertions.assertThrows(
        ResponseStatusException.class,
        () -> controller.run(99L, administrator()));

    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    verifyNoInteractions(recorder);
  }

  @Test
  void recordsSuccessfulRunStepAndRawViewsWithoutPromptContent() {
    AgentAuditRunSummary summary = summary();
    when(auditQuery.findRun(17L)).thenReturn(Optional.of(new AgentAuditRunDetail(
        summary, 1, null, 4, null, null, null, null, null, List.of(), List.of(), AgentAuditUsage.empty())));
    when(auditQuery.findStep(17L, 1, false)).thenReturn(Optional.of(new AgentAuditStepDetail(
        step(), 101L, new ObjectMapper().createObjectNode().put("secret", "[REDACTED]"),
        null, null, null, null, "hash", "v1", null, null, List.of())));
    when(auditQuery.findStep(17L, 1, true)).thenReturn(Optional.of(new AgentAuditStepDetail(
        step(), 101L, new ObjectMapper().createObjectNode().put("secret", "[REDACTED]"),
        null, null, null, null, "hash", "v1", null, null, List.of())));

    assertThat(controller.run(17L, administrator()).success()).isTrue();
    assertThat(controller.step(17L, 1, false, administrator()).success()).isTrue();
    assertThat(controller.step(17L, 1, true, administrator()).success()).isTrue();

    ArgumentCaptor<AdminOperationAuditEvent> events = ArgumentCaptor.forClass(AdminOperationAuditEvent.class);
    verify(recorder, org.mockito.Mockito.times(3)).record(events.capture());
    assertThat(events.getAllValues())
        .extracting(AdminOperationAuditEvent::action, AdminOperationAuditEvent::targetRef)
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple(AdminAuditAction.AI_RUN_TRACE_VIEW, "17"),
            org.assertj.core.groups.Tuple.tuple(AdminAuditAction.AI_RUN_TRACE_VIEW, "17"),
            org.assertj.core.groups.Tuple.tuple(AdminAuditAction.AI_RUN_TRACE_VIEW, "17"));
    assertThat(events.getAllValues())
        .extracting(event -> event.metadata().get(AdminAuditMetadataKey.APPLICATION_VERSION))
        .containsExactly("summary", "detail", "raw");
  }

  @Test
  void recordsFullToolResultContentViews() {
    when(auditQuery.findToolResult(17L, "call-1", true, 0, 200)).thenReturn(Optional.of(new AgentAuditToolResult(
        17L, 1, "call-1", "lookup", "SUCCEEDED", "blob", "tool-result:1", "hash", 20, 1,
        null, "[REDACTED] result", true, true, Instant.parse("2026-09-01T00:00:00Z"))));

    assertThat(controller.toolResult(17L, "call-1", true, 0, 200, administrator()).success()).isTrue();

    ArgumentCaptor<AdminOperationAuditEvent> event = ArgumentCaptor.forClass(AdminOperationAuditEvent.class);
    verify(recorder).record(event.capture());
    assertThat(event.getValue())
        .extracting(AdminOperationAuditEvent::action, AdminOperationAuditEvent::targetRef)
        .containsExactly(AdminAuditAction.AI_RUN_TRACE_VIEW, "17");
    assertThat(event.getValue().metadata().get(AdminAuditMetadataKey.APPLICATION_VERSION)).isEqualTo("content");
  }

  private void assertBadRequest(org.junit.jupiter.api.function.Executable executable) {
    ResponseStatusException exception = org.junit.jupiter.api.Assertions.assertThrows(
        ResponseStatusException.class,
        executable);
    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  private UsernamePasswordAuthenticationToken administrator() {
    return new UsernamePasswordAuthenticationToken(
        "42", "n/a", List.of(new SimpleGrantedAuthority("ai-run:read")));
  }

  private AgentAuditRunSummary summary() {
    return new AgentAuditRunSummary(
        17L, "run-17", 11L, 13L, 7L, "PRACTICE_CHAT", "LEARNING", "practice", "openai", "gpt-test", "SUCCEEDED", "stop",
        1, 0, 0, 0, 8_000, 6_800, 7_920, 8_762L, 640L, 762L, false, 0, false,
        Instant.parse("2026-08-01T00:00:00Z"), Instant.parse("2026-08-01T00:00:01Z"));
  }

  private AgentAuditStepSummary step() {
    return new AgentAuditStepSummary(
        1, "SUCCEEDED", "openai", "gpt-test", "stop", Instant.parse("2026-08-01T00:00:00Z"),
        Instant.parse("2026-08-01T00:00:01Z"), 1, java.util.Map.of("user", 1), 7_800, 0, 0, 3, 7_920,
        8_000, 80, new AgentAuditUsage(8_762L, 640L, 400L, null, 9_162L), false, null, true, 0, 0, null, null);
  }
}
