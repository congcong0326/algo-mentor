import type { FeedbackThreadDetail } from '../types/api';
import { useI18n } from '../i18n/I18nProvider';

export default function FeedbackThreadTimeline({ detail }: { detail: FeedbackThreadDetail }) {
  const { resources } = useI18n();
  return (
    <ol className="feedback-timeline">
      {detail.messages.map((message) => (
        <li className={`feedback-message ${message.senderType === 'ADMIN' ? 'from-admin' : 'from-user'}`} key={message.id}>
          <div className="feedback-message-meta">
            <strong>{message.senderType === 'ADMIN' ? resources.feedback.administrator : resources.feedback.user}</strong>
            <time dateTime={message.createdAt}>{new Date(message.createdAt).toLocaleString()}</time>
          </div>
          <p>{message.content}</p>
        </li>
      ))}
    </ol>
  );
}
