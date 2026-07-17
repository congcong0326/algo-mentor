import type { FeedbackThreadSummary } from '../types/api';
import { useI18n } from '../i18n/I18nProvider';

export default function FeedbackThreadList({ items, selectedId, onSelect }: {
  items: FeedbackThreadSummary[];
  selectedId?: number;
  onSelect: (id: number) => void;
}) {
  const { resources } = useI18n();
  return (
    <div className="feedback-thread-list" role="list">
      {items.map((item) => (
        <button aria-pressed={selectedId === item.id} className="feedback-thread-row" key={item.id} onClick={() => onSelect(item.id)} type="button">
          <span className={`feedback-status-dot ${item.unreadMessageCount > 0 ? 'unread' : ''}`} aria-hidden="true" />
          <span className="feedback-thread-row-main">
            <strong>{item.subject || resources.feedback.untitled}</strong>
            <small>{item.category} · {item.status}</small>
          </span>
          {item.unreadMessageCount > 0 ? <span className="feedback-count">{item.unreadMessageCount > 99 ? '99+' : item.unreadMessageCount}</span> : null}
        </button>
      ))}
    </div>
  );
}
