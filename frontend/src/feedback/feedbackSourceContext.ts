export interface FeedbackSourceContext {
  sourcePath: string;
  sourceRequestId?: string;
  sourceRunId?: string;
  capturedAt: number;
}

const storageKey = 'algo-mentor-feedback-source';
const maxAgeMs = 30 * 60 * 1000;
let inMemory: FeedbackSourceContext | undefined;

export function recordFeedbackRequestContext(apiPath: string, requestId?: string): void {
  if (!shouldRecord(apiPath) || typeof window === 'undefined') return;
  save({ sourcePath: window.location.pathname, sourceRequestId: requestId, capturedAt: Date.now() });
}

export function recordFeedbackRunContext(runId: string | undefined): void {
  if (!runId || typeof window === 'undefined') return;
  const current = getFeedbackSourceContext() ?? { sourcePath: window.location.pathname, capturedAt: Date.now() };
  save({ ...current, sourcePath: window.location.pathname, sourceRunId: runId, capturedAt: Date.now() });
}

export function captureFeedbackNavigationContext(): void {
  if (typeof window !== 'undefined') {
    const current = getFeedbackSourceContext();
    if (current?.sourcePath === window.location.pathname) {
      save({ ...current, capturedAt: Date.now() });
      return;
    }
    save({ sourcePath: window.location.pathname, capturedAt: Date.now() });
  }
}

export function getFeedbackSourceContext(): FeedbackSourceContext | undefined {
  const value = read();
  return value && Date.now() - value.capturedAt <= maxAgeMs ? value : undefined;
}

export function clearFeedbackSourceContext(): void { inMemory = undefined; try { window.sessionStorage.removeItem(storageKey); } catch { /* storage is optional */ } }

function shouldRecord(path: string): boolean {
  return !path.startsWith('/api/feedback') && !path.startsWith('/api/admin/feedback')
    && !path.startsWith('/api/auth/') && path !== '/api/health';
}

function save(value: FeedbackSourceContext): void {
  inMemory = value;
  try { window.sessionStorage.setItem(storageKey, JSON.stringify(value)); } catch { /* use memory fallback */ }
}

function read(): FeedbackSourceContext | undefined {
  if (inMemory) return inMemory;
  try {
    const raw = window.sessionStorage.getItem(storageKey);
    if (raw) inMemory = JSON.parse(raw) as FeedbackSourceContext;
  } catch { /* use memory fallback */ }
  return inMemory;
}
