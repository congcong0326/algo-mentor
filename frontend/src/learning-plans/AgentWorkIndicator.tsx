import { AlertCircle, CheckCircle2, LoaderCircle } from 'lucide-react';
import type { AgentWorkStatusEvent } from '../types/api';

interface AgentWorkIndicatorProps {
  event?: AgentWorkStatusEvent;
  active: boolean;
  error?: string;
}

export default function AgentWorkIndicator({ event, active, error }: AgentWorkIndicatorProps) {
  const message = error || event?.message || (active ? '正在生成学习计划' : '等待生成');
  const isError = Boolean(error) || Boolean(event?.code);
  const isDone = !active && !isError && event?.message === '生成完成';

  return (
    <div className={`agent-work-indicator${isError ? ' error' : ''}${isDone ? ' done' : ''}`} role="status">
      {isError ? <AlertCircle aria-hidden="true" /> : isDone ? <CheckCircle2 aria-hidden="true" /> : (
        <LoaderCircle aria-hidden="true" className={active ? 'spin-icon' : ''} />
      )}
      <span>{message}</span>
    </div>
  );
}
