import { useEffect, useState } from 'react';
import { useI18n } from '../../i18n/I18nProvider';
import { createUserGroup, requireApiData, updateUserGroup } from '../../services/api';
import type { UserGroupSummary, UserGroupStatus } from '../../types/api';

interface UserGroupDialogProps {
  group?: UserGroupSummary;
  onClose: () => void;
  onSaved: (group: UserGroupSummary) => void;
}

const groupCodePattern = /^[A-Z][A-Z0-9_]{0,63}$/;

export default function UserGroupDialog({ group, onClose, onSaved }: UserGroupDialogProps) {
  const { resources } = useI18n();
  const t = resources.adminGroups;
  const [code, setCode] = useState(group?.code ?? '');
  const [name, setName] = useState(group?.name ?? '');
  const [description, setDescription] = useState(group?.description ?? '');
  const [status, setStatus] = useState<UserGroupStatus>(group?.status ?? 'ACTIVE');
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    function closeOnEscape(event: KeyboardEvent) {
      if (event.key === 'Escape' && !saving) onClose();
    }
    document.addEventListener('keydown', closeOnEscape);
    return () => document.removeEventListener('keydown', closeOnEscape);
  }, [onClose, saving]);

  async function submit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (saving) return;
    const normalizedCode = code.trim().toUpperCase();
    const normalizedName = name.trim();
    if (!group && !groupCodePattern.test(normalizedCode)) {
      setError(t.codeInvalid);
      return;
    }
    if (!normalizedName) {
      setError(t.nameRequired);
      return;
    }
    setSaving(true);
    setError('');
    try {
      const saved = group
        ? requireApiData(await updateUserGroup(group.id, {
          name: normalizedName,
          description: description.trim() || null,
          status,
        }), t.saveFailed)
        : requireApiData(await createUserGroup({
          code: normalizedCode,
          name: normalizedName,
          description: description.trim() || null,
        }), t.saveFailed);
      onSaved(saved);
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : t.saveFailed);
    } finally {
      setSaving(false);
    }
  }

  return (
    <div className="admin-dialog-backdrop" role="presentation">
      <form aria-labelledby="user-group-dialog-title" aria-modal="true" className="admin-dialog user-group-form" onSubmit={submit} role="dialog">
        <header>
          <h2 id="user-group-dialog-title">{group ? t.editTitle : t.createTitle}</h2>
          <p>{group ? t.editDescription : t.createDescription}</p>
        </header>
        {error ? <p className="error-text" role="alert">{error}</p> : null}
        <label>
          <span>{t.code}</span>
          {group ? <code className="user-group-code-readonly">{group.code}</code> : (
            <input autoFocus maxLength={64} onChange={(event) => setCode(event.target.value.toUpperCase())} placeholder="BETA_TESTER" value={code} />
          )}
        </label>
        <label>
          <span>{t.name}</span>
          <input maxLength={120} onChange={(event) => setName(event.target.value)} value={name} />
        </label>
        <label>
          <span>{t.description}</span>
          <textarea maxLength={500} onChange={(event) => setDescription(event.target.value)} rows={4} value={description} />
        </label>
        {group ? (
          <label>
            <span>{t.status}</span>
            <select onChange={(event) => setStatus(event.target.value as UserGroupStatus)} value={status}>
              <option value="ACTIVE">{t.statusActive}</option>
              <option value="DISABLED">{t.statusDisabled}</option>
            </select>
          </label>
        ) : null}
        <footer>
          <button className="secondary-button" disabled={saving} onClick={onClose} type="button">{resources.common.cancel}</button>
          <button className="primary-button" disabled={saving} type="submit">{saving ? t.saving : t.save}</button>
        </footer>
      </form>
    </div>
  );
}
