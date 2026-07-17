import { KeyRound, X } from 'lucide-react';
import { useEffect, useRef, useState } from 'react';
import { useI18n } from '../../i18n/I18nProvider';
import { ApiRequestError, getAdminUserDetail, requireApiData } from '../../services/api';
import type { AdminUserDetail } from '../../types/api';
import { formatDateTime } from '../ai/aiFormat';
import AdminUserAiSection from './AdminUserAiSection';
import AdminUserSupportSection from './AdminUserSupportSection';

interface AdminUserDetailDrawerProps {
  onClose: () => void;
  onResetPassword: (user: AdminUserDetail) => void;
  onViewFullUsage: (userId: number) => void;
  onNavigate: (path: string) => void;
  userId: number;
}

export default function AdminUserDetailDrawer({
  onClose,
  onResetPassword,
  onViewFullUsage,
  onNavigate,
  userId,
}: AdminUserDetailDrawerProps) {
  const { resources } = useI18n();
  const t = resources.adminUsers;
  const [user, setUser] = useState<AdminUserDetail>();
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const requestIdRef = useRef(0);

  useEffect(() => {
    const controller = new AbortController();
    void load(controller.signal);
    return () => controller.abort();
  }, [userId]);

  async function load(signal?: AbortSignal) {
    const requestId = requestIdRef.current + 1;
    requestIdRef.current = requestId;
    const current = () => requestIdRef.current === requestId && !signal?.aborted;
    setLoading(true);
    setError('');
    setUser(undefined);
    try {
      const detail = requireApiData(await getAdminUserDetail(userId, signal), t.loadFailed);
      if (current()) {
        setUser(detail);
      }
    } catch (caught) {
      if (current()) {
        setError(errorMessage(caught, t.loadFailed));
      }
    } finally {
      if (current()) {
        setLoading(false);
      }
    }
  }

  const title = user?.displayName ?? resources.app.unknownUser(userId);

  return (
    <div className="admin-user-drawer-backdrop" role="presentation">
      <aside aria-label={t.detailAriaLabel} className="admin-user-detail-drawer" role="region">
        <header className="admin-user-drawer-header">
          <div>
            <p className="section-kicker">{t.detailAriaLabel}</p>
            <h2>{title}</h2>
          </div>
          <button aria-label={resources.common.close} className="icon-button" onClick={onClose} title={resources.common.close} type="button">
            <X aria-hidden="true" />
          </button>
        </header>

        {loading ? <p role="status">{t.loading}</p> : null}
        {error ? <p className="error-text" role="alert">{error}</p> : null}

        {user ? (
          <>
            <div className="admin-user-detail-actions">
              {user.status !== 'DELETED' ? (
                <button className="secondary-button compact" onClick={() => onResetPassword(user)} type="button">
                  <KeyRound aria-hidden="true" />
                  <span>{t.resetPassword}</span>
                </button>
              ) : null}
            </div>
            <dl className="admin-user-drawer-details">
              <dt>{t.id}</dt><dd>{user.id}</dd>
              <dt>{t.email}</dt><dd>{user.email ?? resources.common.empty}</dd>
              <dt>{t.roles}</dt><dd>{user.roles.join(', ')}</dd>
              <dt>{t.status}</dt><dd>{statusLabel(user.status, t)}</dd>
              <dt>{t.createdAt}</dt><dd>{formatDateTime(user.createdAt)}</dd>
              <dt>{t.updatedAt}</dt><dd>{formatDateTime(user.updatedAt)}</dd>
              <dt>{t.lastLoginAt}</dt><dd>{formatDateTime(user.lastLoginAt, resources.common.empty)}</dd>
              <dt>{t.deletedAt}</dt><dd>{formatDateTime(user.deletedAt, resources.common.empty)}</dd>
              <dt>{t.deletedBy}</dt><dd>{user.deletedBy ?? resources.common.empty}</dd>
            </dl>
          </>
        ) : null}

        <AdminUserAiSection key={userId} onViewFullUsage={() => onViewFullUsage(userId)} userId={userId} />
        <AdminUserSupportSection key={`support-${userId}`} onNavigate={onNavigate} userId={userId} />
      </aside>
    </div>
  );
}

function statusLabel(status: AdminUserDetail['status'], resources: ReturnType<typeof useI18n>['resources']['adminUsers']): string {
  if (status === 'ACTIVE') {
    return resources.statusActive;
  }
  if (status === 'DISABLED') {
    return resources.statusDisabled;
  }
  return resources.statusDeleted;
}

function errorMessage(error: unknown, fallback: string): string {
  if (error instanceof ApiRequestError) {
    return error.message || fallback;
  }
  return error instanceof Error ? error.message : fallback;
}
