package org.congcong.algomentor.agent.core.work;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import org.congcong.algomentor.agent.core.AgentErrorCode;
import org.congcong.algomentor.agent.core.AgentException;
import org.congcong.algomentor.agent.core.AgentStreamEvent;
import org.congcong.algomentor.llm.core.response.LlmFinishReason;
import org.congcong.algomentor.llm.core.stream.LlmStreamEvent;
import org.junit.jupiter.api.Test;

class AgentWorkStatusProjectorTest {

  private final AgentWorkStatusProjector projector = new AgentWorkStatusProjector(
      new AgentWorkStatusProfile(
          "learning_plan",
          "开始生成学习计划",
          "正在规划",
          Map.of("search_problems", "正在搜索候选题"),
          10,
          Duration.ofMillis(500),
          true),
      Clock.fixed(Instant.parse("2026-06-23T00:00:01Z"), ZoneOffset.UTC));

  @Test
  void projectsRunStartAndDone() {
    AgentWorkStatusEvent start = projector.project(new AgentStreamEvent.AgentRunStart("run-1", "topic", 4))
        .orElseThrow();
    AgentWorkStatusEvent done = projector.project(new AgentStreamEvent.AgentRunEnd(
        "run-1",
        1,
        LlmFinishReason.STOP,
        Map.of())).orElseThrow();

    assertThat(start.eventName()).isEqualTo("work_start");
    assertThat(start.message()).isEqualTo("开始生成学习计划");
    assertThat(done.eventName()).isEqualTo("work_done");
  }

  @Test
  void projectsContentDeltaAsThrottledTruncatedProgress() {
    projector.project(new AgentStreamEvent.AgentRunStart("run-1", "topic", 4));
    AgentWorkStatusEvent event = projector.project(AgentStreamEvent.fromLlm(
        new LlmStreamEvent.ContentDelta("根据你的目标拆分学习阶段"))).orElseThrow();

    assertThat(event).isInstanceOf(AgentWorkStatusEvent.WorkProgress.class);
    AgentWorkStatusEvent.WorkProgress progress = (AgentWorkStatusEvent.WorkProgress) event;
    assertThat(progress.runId()).isEqualTo("run-1");
    assertThat(progress.preview()).isEqualTo("根据你的目标拆...");
    assertThat(progress.message()).isEqualTo("正在规划：根据你的目标拆...");

    assertThat(projector.project(AgentStreamEvent.fromLlm(new LlmStreamEvent.ContentDelta("继续输出")))).isEmpty();
  }

  @Test
  void projectsToolEventsWithoutArgumentsOrResult() {
    AgentWorkStatusEvent start = projector.project(new AgentStreamEvent.AgentToolStart(
        "run-1",
        1,
        "call-1",
        "search_problems")).orElseThrow();

    AgentWorkStatusEvent end = projector.project(new AgentStreamEvent.AgentToolEnd(
        "run-1",
        1,
        "call-1",
        "search_problems",
        com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode().put("secret", "hidden")))
        .orElseThrow();

    assertThat(start).isEqualTo(new AgentWorkStatusEvent.WorkToolStart(
        "run-1",
        "learning_plan",
        "search_problems",
        "正在搜索候选题"));
    assertThat(end.message()).isEqualTo("搜索候选题完成");
  }

  @Test
  void hidesStructuredJsonPreview() {
    projector.project(new AgentStreamEvent.AgentRunStart("run-1", "topic", 4));

    AgentWorkStatusEvent.WorkProgress progress = (AgentWorkStatusEvent.WorkProgress) projector.project(
        AgentStreamEvent.fromLlm(new LlmStreamEvent.ContentDelta("{\"title\":\"四周计划\"")))
        .orElseThrow();

    assertThat(progress.preview()).isEqualTo("整理结构化结果");
    assertThat(progress.message()).isEqualTo("正在规划：整理结构化结果");
  }

  @Test
  void projectsAgentError() {
    AgentWorkStatusEvent event = projector.project(new AgentStreamEvent.AgentError(
        "run-1",
        new AgentException(AgentErrorCode.LLM_STREAM_FAILED, "provider failed", true, Map.of(), null)))
        .orElseThrow();

    assertThat(event).isEqualTo(new AgentWorkStatusEvent.WorkError(
        "run-1",
        "learning_plan",
        "LLM_STREAM_FAILED",
        "处理失败，请稍后重试。",
        true));
  }
}
