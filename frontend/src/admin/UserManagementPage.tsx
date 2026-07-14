import { Copy, KeyRound } from 'lucide-react';
import { useEffect, useMemo, useRef, useState } from 'react';
import {
  ApiRequestError,
  deleteAdminUser,
  getAdminUserDetail,
  getAdminUsers,
  requireApiData,
  resetAdminUserPassword,
  updateAdminUserStatus,
} from '../services/api';
import type {
  AdminPasswordResetResponse,
  AdminUserDetail,
  AdminUserPage,
  AdminUserSummary,
  AuthUserStatus,
} from '../types/api';
import { useI18n } from '../i18n/I18nProvider';
import type { LocaleResources } from '../i18n/locales';

interface UserManagementPageProps {
  onNavigateHome: () => void;
}

type StatusFilter = AuthUserStatus | '';
type ConfirmAction = 'disable' | 'restore' | 'delete' | 'resetPassword';

interface PendingConfirmation {
  action: ConfirmAction;
  user: AdminUserSummary;
}

interface TemporaryPasswordDialogState extends AdminPasswordResetResponse {
  user: AdminUserSummary;
}

const defaultPageSize = 20;

export default function UserManagementPage({ onNavigateHome }: UserManagementPageProps) {
  const { resources } = useI18n();
  const t = resources.adminUsers;
  const [page, setPage] = useState(1);
  const [pageSize] = useState(defaultPageSize);
  const [keywordInput, setKeywordInput] = useState('');
  const [keyword, setKeyword] = useState('');
  const [status, setStatus] = useState<StatusFilter>('');
  const [usersPage, setUsersPage] = useState<AdminUserPage>({
    items: [],
    total: 0,
    page: 1,
    pageSize: defaultPageSize,
  });
  const [selectedUser, setSelectedUser] = useState<AdminUserDetail>();
  const [loading, setLoading] = useState(false);
  const [detailLoading, setDetailLoading] = useState(false);
  const [error, setError] = useState('');
  const [forbidden, setForbidden] = useState(false);
  const [operation, setOperation] = useState(false);
  const [pendingConfirmation, setPendingConfirmation] = useState<PendingConfirmation>();
  const [temporaryPassword, setTemporaryPassword] = useState<TemporaryPasswordDialogState>();
  const [temporaryPasswordCopied, setTemporaryPasswordCopied] = useState(false);
  const listRequestIdRef = useRef(0);
  const detailRequestIdRef = useRef(0);

  const totalPages = useMemo(
    () => Math.max(1, Math.ceil(usersPage.total / usersPage.pageSize)),
    [usersPage.pageSize, usersPage.total],
  );

  useEffect(() => {
    const controller = new AbortController();
    void loadUsers(page, controller.signal);

    return () => controller.abort();
  }, [keyword, page, pageSize, status]);

  async function loadUsers(pageToLoad: number, signal?: AbortSignal): Promise<AdminUserPage | undefined> {
    const requestId = listRequestIdRef.current + 1;
    listRequestIdRef.current = requestId;
    const isCurrentRequest = () => requestId === listRequestIdRef.current && !signal?.aborted;

    setLoading(true);
    setError('');
    setForbidden(false);

    try {
      const data = requireApiData(await getAdminUsers({
        page: pageToLoad,
        pageSize,
        keyword,
        status,
      }, signal), t.loadFailed);
      if (!isCurrentRequest()) {
        return undefined;
      }
      setUsersPage(data);
      return data;
    } catch (caught) {
      if (!isCurrentRequest()) {
        return undefined;
      }
      if (isForbidden(caught)) {
        setForbidden(true);
        setError(t.forbidden);
        return undefined;
      }
      setError(caught instanceof Error ? caught.message : t.loadFailed);
      return undefined;
    } finally {
      if (isCurrentRequest()) {
        setLoading(false);
      }
    }
  }

  async function openDetail(userId: number) {
    const requestId = detailRequestIdRef.current + 1;
    detailRequestIdRef.current = requestId;
    const isCurrentRequest = () => requestId === detailRequestIdRef.current;

    setDetailLoading(true);
    setError('');
    try {
      const detail = requireApiData(await getAdminUserDetail(userId), t.loadFailed);
      if (!isCurrentRequest()) {
        return;
      }
      setSelectedUser(detail);
    } catch (caught) {
      if (isCurrentRequest()) {
        setError(caught instanceof Error ? caught.message : t.loadFailed);
      }
    } finally {
      if (isCurrentRequest()) {
        setDetailLoading(false);
      }
    }
  }

  function handleKeywordChange(value: string) {
    setKeywordInput(value);
    setPage(1);
    setKeyword(value.trim());
  }

  function handleStatusChange(value: StatusFilter) {
    setPage(1);
    setStatus(value);
  }

  async function confirmOperation() {
    if (!pendingConfirmation || operation) {
      return;
    }

    const { action, user } = pendingConfirmation;
    setOperation(true);
    setError('');

    try {
      if (action === 'disable') {
        requireApiData(await updateAdminUserStatus(user.id, { status: 'DISABLED' }), t.operationFailed);
        await loadUsers(page);
      } else if (action === 'restore') {
        requireApiData(await updateAdminUserStatus(user.id, { status: 'ACTIVE' }), t.operationFailed);
        await loadUsers(page);
      } else if (action === 'delete') {
        requireApiData(await deleteAdminUser(user.id), t.operationFailed);
        const reloaded = await loadUsers(page);
        if (reloaded && reloaded.items.length === 0 && page > 1) {
          setPage(page - 1);
        }
      } else {
        const result = requireApiData(await resetAdminUserPassword(user.id), t.operationFailed);
        setTemporaryPassword({ ...result, user });
        setTemporaryPasswordCopied(false);
      }
      setPendingConfirmation(undefined);
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : t.operationFailed);
    } finally {
      setOperation(false);
    }
  }

  function confirmationTitle(action: ConfirmAction): string {
    if (action === 'disable') {
      return t.confirmDisableTitle;
    }
    if (action === 'restore') {
      return t.confirmRestoreTitle;
    }
    if (action === 'resetPassword') {
      return t.confirmPasswordResetTitle;
    }
    return t.confirmDeleteTitle;
  }

  async function copyTemporaryPassword() {
    if (!temporaryPassword) {
      return;
    }
    try {
      await navigator.clipboard.writeText(temporaryPassword.temporaryPassword);
      setTemporaryPasswordCopied(true);
    } catch {
      setError(t.operationFailed);
    }
  }

  function closeTemporaryPassword() {
    setTemporaryPassword(undefined);
    setTemporaryPasswordCopied(false);
  }

  if (forbidden) {
    return (
      <section className="admin-users-page" aria-label={t.ariaLabel}>
        <div className="admin-users-forbidden" role="alert">
          <h1>{t.title}</h1>
          <p>{t.forbidden}</p>
          <button className="primary-button" onClick={onNavigateHome} type="button">
            {t.backHome}
          </button>
        </div>
      </section>
    );
  }

  return (
    <section className="admin-users-page" aria-label={t.ariaLabel}>
      <div className="admin-users-toolbar">
        <div>
          <h1>{t.title}</h1>
          {error ? <p className="error-text" role="alert">{error}</p> : null}
        </div>
        <div className="admin-users-controls">
          <input
            aria-label={t.searchPlaceholder}
            onChange={(event) => handleKeywordChange(event.target.value)}
            placeholder={t.searchPlaceholder}
            type="search"
            value={keywordInput}
          />
          <label>
            <span>{t.status}</span>
            <select
              aria-label={t.status}
              onChange={(event) => handleStatusChange(event.target.value as StatusFilter)}
              value={status}
            >
              <option value="">{t.statusAll}</option>
              <option value="ACTIVE">{t.statusActive}</option>
              <option value="DISABLED">{t.statusDisabled}</option>
              <option value="DELETED">{t.statusDeleted}</option>
            </select>
          </label>
          <button className="secondary-button" onClick={() => void loadUsers(page)} type="button">
            {t.refresh}
          </button>
        </div>
      </div>

      <div className="admin-users-table-wrap">
        <table className="admin-users-table">
          <thead>
            <tr>
              <th>{t.id}</th>
              <th>{t.email}</th>
              <th>{t.displayName}</th>
              <th>{t.roles}</th>
              <th>{t.status}</th>
              <th>{t.createdAt}</th>
              <th>{t.lastLoginAt}</th>
              <th>{t.actions}</th>
            </tr>
          </thead>
          <tbody>
            {loading ? Array.from({ length: 4 }, (_, index) => (
              <tr className="admin-users-skeleton-row" key={`loading-${index}`}>
                <td colSpan={8}>{t.loading}</td>
              </tr>
            )) : null}
            {!loading && usersPage.items.map((user) => (
              <tr key={user.id}>
                <td>{user.id}</td>
                <td>{user.email ?? resources.app.unknownUser(user.id)}</td>
                <td>{user.displayName ?? resources.common.empty}</td>
                <td>{user.roles.join(', ')}</td>
                <td>
                  <span className={`admin-user-status ${user.status.toLowerCase()}`}>
                    {statusLabel(user.status, t)}
                  </span>
                </td>
                <td>{formatDateTime(user.createdAt)}</td>
                <td>{formatOptionalDateTime(user.lastLoginAt, resources.common.empty)}</td>
                <td>
                  <div className="admin-user-actions">
                    <button className="secondary-button compact" onClick={() => void openDetail(user.id)} type="button">
                      {resources.common.view}
                    </button>
                    {user.status === 'ACTIVE' ? (
                      <button className="secondary-button compact" onClick={() => setPendingConfirmation({ action: 'disable', user })} type="button">
                        {t.disable}
                      </button>
                    ) : null}
                    {user.status === 'DISABLED' ? (
                      <button className="secondary-button compact" onClick={() => setPendingConfirmation({ action: 'restore', user })} type="button">
                        {t.restore}
                      </button>
                    ) : null}
                    {user.status === 'ACTIVE' || user.status === 'DISABLED' ? (
                      <button className="secondary-button compact danger-button" onClick={() => setPendingConfirmation({ action: 'delete', user })} type="button">
                        {t.delete}
                      </button>
                    ) : null}
                  </div>
                </td>
              </tr>
            ))}
            {!loading && usersPage.items.length === 0 ? (
              <tr>
                <td colSpan={8}>{t.empty}</td>
              </tr>
            ) : null}
          </tbody>
        </table>
      </div>

      <div className="admin-users-pagination">
        <button
          className="secondary-button"
          disabled={page <= 1 || loading}
          onClick={() => setPage((currentPage) => Math.max(1, currentPage - 1))}
          type="button"
        >
          {resources.common.previousPage}
        </button>
        <span>{resources.common.pageStatus(page, totalPages)}</span>
        <button
          className="secondary-button"
          disabled={page >= totalPages || loading}
          onClick={() => setPage((currentPage) => currentPage + 1)}
          type="button"
        >
          {resources.common.nextPage}
        </button>
      </div>

      {detailLoading ? <div role="status">{t.loading}</div> : null}
      {selectedUser ? (
        <aside className="admin-user-detail" aria-label={t.detailAriaLabel} role="region">
          <div>
            <h2>{selectedUser.displayName ?? resources.app.unknownUser(selectedUser.id)}</h2>
            <div className="admin-user-detail-actions">
              {selectedUser.status !== 'DELETED' ? (
                <button
                  className="secondary-button compact"
                  onClick={() => setPendingConfirmation({ action: 'resetPassword', user: selectedUser })}
                  type="button"
                >
                  <KeyRound aria-hidden="true" />
                  <span>{t.resetPassword}</span>
                </button>
              ) : null}
              <button className="secondary-button compact" onClick={() => setSelectedUser(undefined)} type="button">
                {resources.common.close}
              </button>
            </div>
          </div>
          <dl>
            <dt>{t.id}</dt>
            <dd>{selectedUser.id}</dd>
            <dt>{t.email}</dt>
            <dd>{selectedUser.email ?? resources.common.empty}</dd>
            <dt>{t.roles}</dt>
            <dd>{selectedUser.roles.join(', ')}</dd>
            <dt>{t.status}</dt>
            <dd>{statusLabel(selectedUser.status, t)}</dd>
            <dt>{t.createdAt}</dt>
            <dd>{formatDateTime(selectedUser.createdAt)}</dd>
            <dt>{t.updatedAt}</dt>
            <dd>{formatDateTime(selectedUser.updatedAt)}</dd>
            <dt>{t.lastLoginAt}</dt>
            <dd>{formatOptionalDateTime(selectedUser.lastLoginAt, resources.common.empty)}</dd>
            <dt>{t.deletedAt}</dt>
            <dd>{formatOptionalDateTime(selectedUser.deletedAt, resources.common.empty)}</dd>
            <dt>{t.deletedBy}</dt>
            <dd>{selectedUser.deletedBy ?? resources.common.empty}</dd>
          </dl>
        </aside>
      ) : null}

      {pendingConfirmation ? (
        <div className="admin-confirm-dialog-backdrop">
          <div className="admin-confirm-dialog" aria-labelledby="admin-confirm-dialog-title" aria-modal="true" role="dialog">
            <h2 id="admin-confirm-dialog-title">{confirmationTitle(pendingConfirmation.action)}</h2>
            {pendingConfirmation.action === 'delete' ? <p>{t.confirmDeleteDescription}</p> : null}
            {pendingConfirmation.action === 'resetPassword' ? <p>{t.confirmPasswordResetDescription}</p> : null}
            <div className="button-row">
              <button className="secondary-button" disabled={operation} onClick={() => setPendingConfirmation(undefined)} type="button">
                {resources.common.cancel}
              </button>
              <button className="primary-button" disabled={operation} onClick={() => void confirmOperation()} type="button">
                {t.confirm}
              </button>
            </div>
          </div>
        </div>
      ) : null}

      {temporaryPassword ? (
        <div className="admin-confirm-dialog-backdrop">
          <div
            className="admin-confirm-dialog temporary-password-dialog"
            aria-labelledby="temporary-password-dialog-title"
            aria-modal="true"
            role="dialog"
          >
            <h2 id="temporary-password-dialog-title">{t.temporaryPasswordTitle}</h2>
            <p>{t.temporaryPasswordNotice}</p>
            <code>{temporaryPassword.temporaryPassword}</code>
            <p>{t.temporaryPasswordExpiresAt}: {formatDateTime(temporaryPassword.expiresAt)}</p>
            <div className="button-row">
              <button className="secondary-button" onClick={() => void copyTemporaryPassword()} type="button">
                <Copy aria-hidden="true" />
                <span>{temporaryPasswordCopied ? t.temporaryPasswordCopied : t.copyTemporaryPassword}</span>
              </button>
              <button className="primary-button" onClick={closeTemporaryPassword} type="button">
                {resources.common.close}
              </button>
            </div>
          </div>
        </div>
      ) : null}
    </section>
  );
}

function isForbidden(error: unknown): boolean {
  return error instanceof ApiRequestError && (error.status === 401 || error.status === 403);
}

function statusLabel(status: AuthUserStatus, resources: LocaleResources['adminUsers']): string {
  if (status === 'ACTIVE') {
    return resources.statusActive;
  }
  if (status === 'DISABLED') {
    return resources.statusDisabled;
  }
  return resources.statusDeleted;
}

function formatOptionalDateTime(value: string | null | undefined, fallback: string): string {
  return value ? formatDateTime(value) : fallback;
}

function formatDateTime(value: string): string {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return value;
  }
  return [
    date.getUTCFullYear(),
    String(date.getUTCMonth() + 1).padStart(2, '0'),
    String(date.getUTCDate()).padStart(2, '0'),
  ].join('-') + ` ${String(date.getUTCHours()).padStart(2, '0')}:${String(date.getUTCMinutes()).padStart(2, '0')}`;
}
