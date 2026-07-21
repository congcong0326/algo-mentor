import type {
  AgentToolEndEvent,
  AgentToolStartEvent,
  LearnerDeclaredProfileToolResult,
  LearnerDeclaredProfileToolStatus,
} from '../types/api';

export const LEARNER_DECLARED_PROFILE_TOOL_NAME = 'update_learner_declared_profile';
export const LEARNER_DECLARED_PROFILE_RESULT_TYPE = 'learner_declared_profile_update';

export type LearnerDeclaredProfileToolDisplayStatus = 'RUNNING' | LearnerDeclaredProfileToolStatus;

type ToolLifecycleEvent = Pick<AgentToolStartEvent | AgentToolEndEvent, 'runId' | 'stepIndex' | 'toolCallId'>;

/** 仅接受固定工具契约，异常或未知结果不进入用户可见状态。 */
export function parseLearnerDeclaredProfileToolResult(result: unknown): LearnerDeclaredProfileToolResult | undefined {
  if (typeof result !== 'object' || result === null) {
    return undefined;
  }
  const value = result as { type?: unknown; status?: unknown };
  if (value.type !== LEARNER_DECLARED_PROFILE_RESULT_TYPE
    || (value.status !== 'UPDATED' && value.status !== 'NO_CHANGE' && value.status !== 'FAILED')) {
    return undefined;
  }
  return { type: value.type, status: value.status };
}

/** SSE 重连会重放事件，稳定身份键确保同一工具调用只保留一个状态节点。 */
export function learnerDeclaredProfileToolEventKey(event: ToolLifecycleEvent): string | undefined {
  if (!event.runId.trim() || !event.toolCallId.trim() || !Number.isInteger(event.stepIndex) || event.stepIndex < 1) {
    return undefined;
  }
  return `${event.runId}:${event.stepIndex}:${event.toolCallId}`;
}
