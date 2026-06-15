import { act, cleanup, fireEvent, render, screen, within } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import App from './App';

type MockEventListener = (event: MessageEvent<string>) => void;

class MockEventSource {
  static instances: MockEventSource[] = [];

  readonly url: string;
  onopen: (() => void) | null = null;
  onerror: (() => void) | null = null;
  close = vi.fn();

  private readonly listeners = new Map<string, MockEventListener[]>();

  constructor(url: string) {
    this.url = url;
    MockEventSource.instances.push(this);
  }

  addEventListener(eventName: string, listener: EventListenerOrEventListenerObject) {
    const listeners = this.listeners.get(eventName) ?? [];
    listeners.push(listener as MockEventListener);
    this.listeners.set(eventName, listeners);
  }

  emit(eventName: string, data: unknown) {
    const event = new MessageEvent(eventName, { data: JSON.stringify(data) });
    this.listeners.get(eventName)?.forEach((listener) => listener(event));
  }
}

describe('App', () => {
  beforeEach(() => {
    MockEventSource.instances = [];
    vi.stubGlobal('EventSource', MockEventSource);
  });

  afterEach(() => {
    cleanup();
    vi.unstubAllGlobals();
  });

  it('renders the SSE test client shell', () => {
    render(<App />);

    expect(screen.getByRole('heading', { name: 'AI SSE 测试台' })).toBeInTheDocument();
    expect(screen.getByRole('textbox', { name: 'Topic' })).toHaveValue('two pointers');
    expect(screen.getByRole('button', { name: 'Start' })).toBeInTheDocument();
    expect(screen.getByText('/api/ai/explanations/stream?topic=two%20pointers')).toBeInTheDocument();
  });

  it('merges consecutive content_delta logs into one event row', () => {
    render(<App />);
    fireEvent.click(screen.getByRole('button', { name: 'Start' }));

    const source = MockEventSource.instances[0];
    act(() => {
      source.emit('content_delta', { content: 'Hello' });
      source.emit('content_delta', { content: ' world' });
    });

    expect(screen.getByText('Hello world')).toBeInTheDocument();
    expect(screen.getAllByText('content_delta')).toHaveLength(1);

    const logPanel = screen.getByRole('heading', { name: '事件日志' }).closest('article');
    expect(logPanel).not.toBeNull();
    expect(within(logPanel as HTMLElement).getByText(/Hello world/)).toBeInTheDocument();
  });

  it('starts a new content_delta log after another event type', () => {
    render(<App />);
    fireEvent.click(screen.getByRole('button', { name: 'Start' }));

    const source = MockEventSource.instances[0];
    act(() => {
      source.emit('content_delta', { content: 'first' });
      source.emit('usage', { usage: { totalTokens: 3 } });
      source.emit('content_delta', { content: 'second' });
    });

    expect(screen.getAllByText('content_delta')).toHaveLength(2);
    expect(screen.getByText('usage')).toBeInTheDocument();
    expect(screen.getByText('firstsecond')).toBeInTheDocument();
  });
});
