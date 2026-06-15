import {
  Activity,
  AlertTriangle,
  CircleStop,
  Play,
  Radio,
  RotateCcw,
  Server,
} from 'lucide-react';
import { useEffect, useRef, useState } from 'react';

type ConnectionState = 'idle' | 'connecting' | 'open' | 'stopped' | 'error' | 'done';

type SseEventName =
  | 'agent_run_start'
  | 'agent_step_start'
  | 'agent_tool_start'
  | 'agent_tool_end'
  | 'agent_step_end'
  | 'agent_run_end'
  | 'message_start'
  | 'content_delta'
  | 'tool_call_start'
  | 'tool_call_delta'
  | 'tool_call_end'
  | 'usage'
  | 'message_end'
  | 'heartbeat'
  | 'error'
  | 'agent_error';

interface StreamLogEntry {
  id: number;
  eventName: SseEventName | 'connection_open' | 'connection_stopped' | 'connection_error';
  timestamp: string;
  data: unknown;
}

interface MessageStartData {
  provider?: string;
  model?: string;
}

interface ContentDeltaData {
  content?: string;
}

interface UsageData {
  usage?: {
    inputTokens?: number;
    outputTokens?: number;
    reasoningTokens?: number;
    cachedInputTokens?: number;
    totalTokens?: number;
  };
}

interface MessageEndData {
  finishReason?: string;
}

const streamEvents: SseEventName[] = [
  'agent_run_start',
  'agent_step_start',
  'agent_tool_start',
  'agent_tool_end',
  'agent_step_end',
  'agent_run_end',
  'message_start',
  'content_delta',
  'tool_call_start',
  'tool_call_delta',
  'tool_call_end',
  'usage',
  'message_end',
  'heartbeat',
  'error',
  'agent_error',
];

function parseEventData(rawData: string): unknown {
  if (!rawData) {
    return {};
  }

  try {
    return JSON.parse(rawData);
  } catch {
    return rawData;
  }
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

function statusLabel(state: ConnectionState): string {
  const labels: Record<ConnectionState, string> = {
    idle: 'idle',
    connecting: 'connecting',
    open: 'open',
    stopped: 'stopped',
    error: 'error',
    done: 'done',
  };

  return labels[state];
}

export default function App() {
  const [topic, setTopic] = useState('two pointers');
  const [connectionState, setConnectionState] = useState<ConnectionState>('idle');
  const [logs, setLogs] = useState<StreamLogEntry[]>([]);
  const [output, setOutput] = useState('');
  const [provider, setProvider] = useState('-');
  const [model, setModel] = useState('-');
  const [finishReason, setFinishReason] = useState('-');
  const [usage, setUsage] = useState<UsageData['usage']>();
  const eventSourceRef = useRef<EventSource | null>(null);
  const logIdRef = useRef(0);

  useEffect(() => {
    return () => {
      eventSourceRef.current?.close();
    };
  }, []);

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

  function closeCurrentSource(nextState: ConnectionState) {
    eventSourceRef.current?.close();
    eventSourceRef.current = null;
    setConnectionState(nextState);
  }

  function handleEvent(eventName: SseEventName, event: MessageEvent<string>) {
    const data = parseEventData(event.data);
    addLog(eventName, data);

    if (eventName === 'message_start') {
      const messageStart = readMessageStart(data);
      setProvider(messageStart.provider ?? '-');
      setModel(messageStart.model ?? '-');
      setConnectionState('open');
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
      closeCurrentSource('done');
    }

    if (eventName === 'error' || eventName === 'agent_error') {
      setConnectionState('error');
    }
  }

  function startStream() {
    const trimmedTopic = topic.trim();
    if (!trimmedTopic) {
      return;
    }

    eventSourceRef.current?.close();
    resetStreamState();
    setConnectionState('connecting');

    const source = new EventSource(`/api/ai/explanations/stream?topic=${encodeURIComponent(trimmedTopic)}`);
    eventSourceRef.current = source;

    source.onopen = () => {
      setConnectionState('open');
      addLog('connection_open', { message: 'EventSource connection opened.' });
    };

    source.onerror = () => {
      addLog('connection_error', { message: 'EventSource connection error or closed by server.' });
      closeCurrentSource('error');
    };

    streamEvents.forEach((eventName) => {
      source.addEventListener(eventName, (event) => {
        handleEvent(eventName, event as MessageEvent<string>);
      });
    });
  }

  function stopStream() {
    addLog('connection_stopped', { message: 'Connection stopped by user.' });
    closeCurrentSource('stopped');
  }

  function clearLogs() {
    resetStreamState();
    if (!eventSourceRef.current) {
      setConnectionState('idle');
    }
  }

  const isStreaming = connectionState === 'connecting' || connectionState === 'open';
  const requestUrl = `/api/ai/explanations/stream?topic=${encodeURIComponent(topic.trim() || 'two pointers')}`;

  return (
    <main className="test-shell">
      <section className="test-header" aria-labelledby="page-title">
        <div>
          <p className="eyebrow">SSE TEST CLIENT</p>
          <h1 id="page-title">AI SSE 测试台</h1>
        </div>
        <div className={`status-pill ${connectionState}`}>
          <Radio aria-hidden="true" />
          <span>{statusLabel(connectionState)}</span>
        </div>
      </section>

      <section className="control-panel" aria-label="SSE 请求控制">
        <label className="topic-field">
          <span>Topic</span>
          <input
            aria-label="Topic"
            disabled={isStreaming}
            onChange={(event) => setTopic(event.target.value)}
            placeholder="输入任意测试 topic"
            value={topic}
          />
        </label>
        <div className="button-row">
          <button className="primary-button" disabled={isStreaming || !topic.trim()} onClick={startStream} type="button">
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
        </div>
        <div className="request-url">
          <Server aria-hidden="true" />
          <code>{requestUrl}</code>
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
    </main>
  );
}
