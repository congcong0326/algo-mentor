import { LogOut, X } from 'lucide-react';
import { useI18n } from '../../i18n/I18nProvider';
import type { AdminAuthSession } from '../../types/api';

interface SessionRevocationDialogProps {
  pending: boolean;
  session: AdminAuthSession;
  onCancel: () => void;
  onConfirm: () => void;
}

export default function SessionRevocationDialog({
  pending,
  session,
  onCancel,
  onConfirm,
}: SessionRevocationDialogProps) {
  const { locale, resources } = useI18n();
  const t = resources.sessionMonitoring;
  const user = session.displayName || session.email || `#${session.userId}`;
  const lastAccessedAt = formatDateTime(session.lastAccessedAt, locale);

  return (
    <div className="admin-confirm-dialog-backdrop" role="presentation">
      <section aria-describedby="session-revocation-description" aria-labelledby="session-revocation-title" aria-modal="true" className="admin-confirm-dialog session-revocation-dialog" role="dialog">
        <header className="session-revocation-dialog-header">
          <div>
            <h2 id="session-revocation-title">{t.confirmTitle}</h2>
            <p id="session-revocation-description">{t.confirmDescription(user, lastAccessedAt)}</p>
          </div>
          <button aria-label={resources.common.close} className="icon-button" disabled={pending} onClick={onCancel} type="button">
            <X aria-hidden="true" />
          </button>
        </header>
        <p>{t.connectionNotice}</p>
        <footer>
          <button className="secondary-button" disabled={pending} onClick={onCancel} type="button">
            {resources.common.cancel}
          </button>
          <button className="danger-button" disabled={pending} onClick={onConfirm} type="button">
            <LogOut aria-hidden="true" />
            <span>{pending ? t.revoking : t.revoke}</span>
          </button>
        </footer>
      </section>
    </div>
  );
}

function formatDateTime(value: string, locale: string): string {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return value;
  }
  return new Intl.DateTimeFormat(locale, {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  }).format(date);
}
