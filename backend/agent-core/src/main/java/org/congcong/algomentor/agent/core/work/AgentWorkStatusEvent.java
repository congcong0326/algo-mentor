package org.congcong.algomentor.agent.core.work;

/**
 * 面向产品界面的简化 Agent 工作状态事件。
 */
public sealed interface AgentWorkStatusEvent
    permits AgentWorkStatusEvent.WorkStart,
    AgentWorkStatusEvent.WorkProgress,
    AgentWorkStatusEvent.WorkToolStart,
    AgentWorkStatusEvent.WorkToolEnd,
    AgentWorkStatusEvent.WorkDone,
    AgentWorkStatusEvent.WorkError {

  String eventName();

  String runId();

  String scenario();

  String message();

  record WorkStart(String runId, String scenario, String message) implements AgentWorkStatusEvent {
    @Override
    public String eventName() {
      return AgentWorkStatusEventNames.WORK_START;
    }
  }

  record WorkProgress(String runId, String scenario, String message, String preview) implements AgentWorkStatusEvent {
    @Override
    public String eventName() {
      return AgentWorkStatusEventNames.WORK_PROGRESS;
    }
  }

  record WorkToolStart(String runId, String scenario, String toolName, String message)
      implements AgentWorkStatusEvent {
    @Override
    public String eventName() {
      return AgentWorkStatusEventNames.WORK_TOOL_START;
    }
  }

  record WorkToolEnd(String runId, String scenario, String toolName, String message) implements AgentWorkStatusEvent {
    @Override
    public String eventName() {
      return AgentWorkStatusEventNames.WORK_TOOL_END;
    }
  }

  record WorkDone(String runId, String scenario, String message) implements AgentWorkStatusEvent {
    @Override
    public String eventName() {
      return AgentWorkStatusEventNames.WORK_DONE;
    }
  }

  record WorkError(String runId, String scenario, String code, String message, boolean retryable)
      implements AgentWorkStatusEvent {
    @Override
    public String eventName() {
      return AgentWorkStatusEventNames.WORK_ERROR;
    }
  }
}
