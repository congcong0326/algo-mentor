import { AGENT_EXECUTOR_OVERLOADED_CODE } from '../types/api';

/**
 * Agent 执行容量已耗尽时，后端在 HTTP 错误体或 SSE 终态事件中返回的稳定错误码。
 */
export function isAgentExecutorOverloaded(error: unknown): boolean {
  return typeof error === 'object'
    && error !== null
    && (error as { code?: unknown }).code === AGENT_EXECUTOR_OVERLOADED_CODE;
}
