import {
  Activity,
  AlertTriangle,
  CircleStop,
  Play,
  RefreshCw,
  RotateCcw,
  Server,
} from 'lucide-react';
import { forwardRef, useEffect, useImperativeHandle, useRef, useState } from 'react';
import { ApiRequestError, streamAgentConversation } from '../services/api';
import type {
  AgentConversationStreamRequest,
  ContentDeltaData,
  MessageEndData,
  MessageStartData,
  SseEventName,
  ToolCallDeltaData,
  UsageData,
} from '../types/api';
import { AGENT_RUN_IN_PROGRESS_CODE } from '../types/api';
import { generateClientId } from '../utils/id';

export type ConnectionState = 'idle' | 'connecting' | 'open' | 'blocked' | 'stopped' | 'error' | 'done';

interface StreamLogEntry {
  id: number;
  eventName: SseEventName | 'connection_open' | 'connection_stopped' | 'connection_error';
  timestamp: string;
  data: unknown;
}

export interface AiDebugConsoleProps {
  onConnectionStateChange?: (state: ConnectionState) => void;
}

export interface AiDebugConsoleHandle {
  stopStreamForLogout: () => void;
}

export function debugStatusLabel(state: ConnectionState): string {
  const labels: Record<ConnectionState, string> = {
    idle: 'idle',
    connecting: 'connecting',
    open: 'open',
    blocked: 'blocked',
    stopped: 'stopped',
    error: 'error',
    done: 'done',
  };

  return labels[state];
}

function formatJson(data: unknown): string {
  if (typeof data === 'string') {
    return data;
  }

  return JSON.stringify(data, null, 2);
}

function nowTime(): string {
  return new Intl.DateTimeFormat('zh-CN', {
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    fractionalSecondDigits: 3,
  }).format(new Date());
}

function isObject(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null;
}

function readMessageStart(data: unknown): MessageStartData {
  return isObject(data) ? data : {};
}

function readContentDelta(data: unknown): ContentDeltaData {
  return isObject(data) ? data : {};
}

function readToolCallDelta(data: unknown): ToolCallDeltaData {
  return isObject(data) ? data : {};
}

function readUsage(data: unknown): UsageData {
  return isObject(data) ? data : {};
}

function readMessageEnd(data: unknown): MessageEndData {
  return isObject(data) ? data : {};
}

function mergeContentDeltaData(previousData: unknown, nextData: unknown): unknown {
  if (!isObject(previousData) || !isObject(nextData)) {
    return nextData;
  }

  const previousContent = readContentDelta(previousData).content ?? '';
  const nextContent = readContentDelta(nextData).content ?? '';

  return {
    ...previousData,
    ...nextData,
    content: previousContent + nextContent,
  };
}

function mergeToolCallDeltaData(previousData: unknown, nextData: unknown): unknown {
  if (!isObject(previousData) || !isObject(nextData)) {
    return nextData;
  }

  const previousDelta = readToolCallDelta(previousData);
  const nextDelta = readToolCallDelta(nextData);

  if (previousDelta.id !== nextDelta.id) {
    return nextData;
  }

  return {
    ...previousData,
    ...nextData,
    argumentsDelta: (previousDelta.argumentsDelta ?? '') + (nextDelta.argumentsDelta ?? ''),
  };
}

function parseOptionalPositiveNumber(value: string): number | undefined {
  const trimmed = value.trim();
  if (!trimmed) {
    return undefined;
  }

  const parsed = Number(trimmed);
  return Number.isInteger(parsed) && parsed > 0 ? parsed : undefined;
}

const AiDebugConsole = forwardRef<AiDebugConsoleHandle, AiDebugConsoleProps>(function AiDebugConsole(
  { onConnectionStateChange },
  ref,
) {
  const [message, setMessage] = useState('Explain two pointers with a concrete example.');
  const [taskId, setTaskId] = useState('');
  const [userId, setUserId] = useState('');
  const [idempotencyKey, setIdempotencyKey] = useState<string>(() => generateClientId());
  const [connectionState, setConnectionState] = useState<ConnectionState>('idle');
  const [logs, setLogs] = useState<StreamLogEntry[]>([]);
  const [output, setOutput] = useState('');
  const [provider, setProvider] = useState('-');
  const [model, setModel] = useState('-');
  const [finishReason, setFinishReason] = useState('-');
  const [usage, setUsage] = useState<UsageData['usage']>();
  const abortControllerRef = useRef<AbortController | null>(null);
  const logIdRef = useRef(0);
  const mountedRef = useRef(true);

  function setAndReportConnectionState(nextState: ConnectionState) {
    setConnectionState(nextState);
    onConnectionStateChange?.(nextState);
  }

  function abortStreamAndResetState(updateLocalState = true) {
    const controller = abortControllerRef.current;
    if (controller) {
      abortControllerRef.current = null;
      controller.abort();
    }

    if (updateLocalState) {
      setAndReportConnectionState('idle');
      return;
    }

    onConnectionStateChange?.('idle');
  }

  useEffect(() => {
    return () => {
      mountedRef.current = false;
      abortStreamAndResetState(false);
    };
  }, []);

  useImperativeHandle(ref, () => ({
    stopStreamForLogout: () => {
      abortStreamAndResetState();
    },
  }));

  function addLog(eventName: StreamLogEntry['eventName'], data: unknown) {
    const entry: StreamLogEntry = {
      id: logIdRef.current,
      eventName,
      timestamp: nowTime(),
      data,
    };

    setLogs((current) => {
      const lastLog = current.at(-1);
      if (eventName === 'content_delta' && lastLog?.eventName === 'content_delta') {
        return [
          ...current.slice(0, -1),
          {
            ...lastLog,
            timestamp: entry.timestamp,
            data: mergeContentDeltaData(lastLog.data, data),
          },
        ];
      }

      if (eventName === 'tool_call_delta' && lastLog?.eventName === 'tool_call_delta') {
        const mergedData = mergeToolCallDeltaData(lastLog.data, data);

        if (mergedData !== data) {
          return [
            ...current.slice(0, -1),
            {
              ...lastLog,
              timestamp: entry.timestamp,
              data: mergedData,
            },
          ];
        }
      }

      logIdRef.current += 1;
      return [...current, entry];
    });
  }

  function resetStreamState() {
    logIdRef.current = 0;
    setLogs([]);
    setOutput('');
    setProvider('-');
    setModel('-');
    setFinishReason('-');
    setUsage(undefined);
  }

  function closeCurrentStream(nextState: ConnectionState) {
    abortControllerRef.current?.abort();
    abortControllerRef.current = null;
    setAndReportConnectionState(nextState);
  }

  function handleEvent(eventName: SseEventName, data: unknown) {
    addLog(eventName, data);

    if (eventName === 'message_start') {
      const messageStart = readMessageStart(data);
      setProvider(messageStart.provider ?? '-');
      setModel(messageStart.model ?? '-');
      setAndReportConnectionState('open');
    }

    if (eventName === 'content_delta') {
      const delta = readContentDelta(data);
      setOutput((current) => current + (delta.content ?? ''));
    }

    if (eventName === 'usage') {
      setUsage(readUsage(data).usage);
    }

    if (eventName === 'message_end') {
      setFinishReason(readMessageEnd(data).finishReason ?? '-');
    }

    if (eventName === 'agent_run_end') {
      abortControllerRef.current = null;
      setAndReportConnectionState('done');
    }

    if (eventName === 'error' || eventName === 'agent_error') {
      setAndReportConnectionState('error');
    }
  }

  async function startStream() {
    const trimmedMessage = message.trim();
    if (!trimmedMessage) {
      return;
    }

    abortControllerRef.current?.abort();
    resetStreamState();
    setAndReportConnectionState('connecting');

    const controller = new AbortController();
    abortControllerRef.current = controller;
    const effectiveIdempotencyKey = idempotencyKey.trim() || generateClientId();
    setIdempotencyKey(effectiveIdempotencyKey);

    try {
      await streamAgentConversation(buildRequestBody(trimmedMessage), {
        idempotencyKey: effectiveIdempotencyKey,
        signal: controller.signal,
        onOpen: () => {
          if (!mountedRef.current || abortControllerRef.current !== controller) {
            return;
          }
          setAndReportConnectionState('open');
          addLog('connection_open', { message: 'POST SSE connection opened.' });
        },
        onEvent: ({ eventName, data }) => {
          if (!mountedRef.current || abortControllerRef.current !== controller) {
            return;
          }
          handleEvent(eventName, data);
        },
      });

      if (abortControllerRef.current === controller) {
        abortControllerRef.current = null;
        setConnectionState((current) => {
          const nextState = current === 'open' || current === 'connecting' ? 'done' : current;
          onConnectionStateChange?.(nextState);
          return nextState;
        });
      }
    } catch (error) {
      if (controller.signal.aborted) {
        return;
      }
      abortControllerRef.current = null;
      const message = error instanceof Error ? error.message : 'Conversation stream failed.';
      addLog('connection_error', {
        message,
        ...(error instanceof ApiRequestError ? {
          status: error.status,
          code: error.code,
          metadata: error.metadata,
        } : {}),
      });
      setAndReportConnectionState(error instanceof ApiRequestError && error.code === AGENT_RUN_IN_PROGRESS_CODE
        ? 'blocked'
        : 'error');
    }
  }

  function stopStream() {
    addLog('connection_stopped', { message: 'Connection stopped by user.' });
    closeCurrentStream('stopped');
  }

  function clearLogs() {
    resetStreamState();
    if (!abortControllerRef.current) {
      setAndReportConnectionState('idle');
    }
  }

  function regenerateIdempotencyKey() {
    setIdempotencyKey(generateClientId());
  }

  function buildRequestBody(trimmedMessage = message.trim()): AgentConversationStreamRequest {
    return {
      ...(parseOptionalPositiveNumber(taskId) === undefined ? {} : { taskId: parseOptionalPositiveNumber(taskId) }),
      ...(parseOptionalPositiveNumber(userId) === undefined ? {} : { userId: parseOptionalPositiveNumber(userId) }),
      message: trimmedMessage,
    };
  }

  const isStreaming = connectionState === 'connecting' || connectionState === 'open';
  const sendDisabled = isStreaming || connectionState === 'blocked';
  const requestBody = buildRequestBody();

  return (
    <>
      <section className="control-panel" aria-label="SSE 请求控制">
        <label className="topic-field">
          <span>Message</span>
          <textarea
            aria-label="Message"
            disabled={isStreaming}
            onChange={(event) => setMessage(event.target.value)}
            placeholder="输入本轮用户消息"
            rows={4}
            value={message}
          />
        </label>
        <div className="field-grid">
          <label className="topic-field">
            <span>Task ID</span>
            <input
              aria-label="Task ID"
              disabled={isStreaming}
              inputMode="numeric"
              onChange={(event) => setTaskId(event.target.value)}
              placeholder="首轮可留空"
              value={taskId}
            />
          </label>
          <label className="topic-field">
            <span>User ID</span>
            <input
              aria-label="User ID"
              disabled={isStreaming}
              inputMode="numeric"
              onChange={(event) => setUserId(event.target.value)}
              placeholder="可选"
              value={userId}
            />
          </label>
          <label className="topic-field key-field">
            <span>Idempotency Key</span>
            <input
              aria-label="Idempotency Key"
              disabled={isStreaming}
              onChange={(event) => setIdempotencyKey(event.target.value)}
              value={idempotencyKey}
            />
          </label>
        </div>
        <div className="button-row">
          <button className="primary-button" disabled={sendDisabled || !message.trim()} onClick={startStream} type="button">
            <Play aria-hidden="true" />
            <span>Start</span>
          </button>
          <button className="secondary-button" disabled={!isStreaming} onClick={stopStream} type="button">
            <CircleStop aria-hidden="true" />
            <span>Stop</span>
          </button>
          <button className="secondary-button" disabled={isStreaming} onClick={clearLogs} type="button">
            <RotateCcw aria-hidden="true" />
            <span>Clear</span>
          </button>
          <button className="secondary-button" disabled={isStreaming} onClick={regenerateIdempotencyKey} type="button">
            <RefreshCw aria-hidden="true" />
            <span>Key</span>
          </button>
        </div>
        <div className="request-url">
          <Server aria-hidden="true" />
          <code>POST /api/agent/conversations/stream</code>
        </div>
        <pre className="request-body">{formatJson(requestBody)}</pre>
        <div className="request-url">
          <Server aria-hidden="true" />
          <code>Idempotency-Key: {idempotencyKey || '(auto)'}</code>
        </div>
      </section>

      <section className="summary-grid" aria-label="流式请求摘要">
        <article className="summary-card">
          <span>Provider</span>
          <strong>{provider}</strong>
        </article>
        <article className="summary-card">
          <span>Model</span>
          <strong>{model}</strong>
        </article>
        <article className="summary-card">
          <span>Finish</span>
          <strong>{finishReason}</strong>
        </article>
        <article className="summary-card">
          <span>Tokens</span>
          <strong>{usage?.totalTokens ?? '-'}</strong>
        </article>
      </section>

      <section className="stream-grid">
        <article className="output-panel" aria-labelledby="output-title">
          <div className="panel-title">
            <Activity aria-hidden="true" />
            <h2 id="output-title">模型输出</h2>
          </div>
          <pre className="model-output">{output || '等待 content_delta 事件...'}</pre>
          {usage && (
            <dl className="usage-row" aria-label="Token usage">
              <div>
                <dt>input</dt>
                <dd>{usage.inputTokens ?? '-'}</dd>
              </div>
              <div>
                <dt>output</dt>
                <dd>{usage.outputTokens ?? '-'}</dd>
              </div>
              <div>
                <dt>reasoning</dt>
                <dd>{usage.reasoningTokens ?? '-'}</dd>
              </div>
              <div>
                <dt>cached</dt>
                <dd>{usage.cachedInputTokens ?? '-'}</dd>
              </div>
            </dl>
          )}
        </article>

        <article className="log-panel" aria-labelledby="log-title">
          <div className="panel-title">
            <AlertTriangle aria-hidden="true" />
            <h2 id="log-title">事件日志</h2>
          </div>
          <div className="event-list">
            {logs.length === 0 ? (
              <p className="empty-log">等待 SSE 事件...</p>
            ) : (
              logs.map((log) => (
                <article className="event-row" key={log.id}>
                  <div className="event-row-header">
                    <span className={`event-name ${log.eventName}`}>{log.eventName}</span>
                    <time>{log.timestamp}</time>
                  </div>
                  <pre>{formatJson(log.data)}</pre>
                </article>
              ))
            )}
          </div>
        </article>
      </section>
    </>
  );
});

export default AiDebugConsole;
