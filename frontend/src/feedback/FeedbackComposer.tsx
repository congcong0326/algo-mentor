import { FormEvent, useState } from 'react';
import { useI18n } from '../i18n/I18nProvider';

export default function FeedbackComposer({ closed, onSend, pending }: {
  closed?: boolean;
  onSend: (content: string) => Promise<void>;
  pending: boolean;
}) {
  const { resources } = useI18n();
  const [content, setContent] = useState('');
  async function submit(event: FormEvent) {
    event.preventDefault();
    if (!content.trim() || pending) return;
    await onSend(content);
    setContent('');
  }
  return (
    <form className="feedback-composer" onSubmit={(event) => void submit(event)}>
      <textarea aria-label={resources.feedback.replyContent} maxLength={4000} onChange={(event) => setContent(event.target.value)} placeholder={resources.feedback.replyPlaceholder} value={content} />
      <button className="primary-button" disabled={pending || !content.trim()} type="submit">
        {pending ? resources.feedback.sending : closed ? resources.feedback.replyAndReopen : resources.feedback.sendReply}
      </button>
    </form>
  );
}
