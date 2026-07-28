import { AlertTriangle, Download, LoaderCircle, Upload, X } from 'lucide-react';
import { useRef, useState } from 'react';
import { useI18n } from '../../i18n/I18nProvider';
import {
  ApiRequestError,
  downloadDatabaseBackup,
  requireApiData,
  restoreDatabaseBackup,
} from '../../services/api';

type PendingAction = 'download' | 'restore' | undefined;

export default function DatabaseBackupPage({
  onRestoreCompleted,
}: {
  onRestoreCompleted: () => void;
}) {
  const { resources } = useI18n();
  const t = resources.databaseBackup;
  const [file, setFile] = useState<File>();
  const [pending, setPending] = useState<PendingAction>();
  const [confirmationOpen, setConfirmationOpen] = useState(false);
  const [error, setError] = useState('');
  const fileInputRef = useRef<HTMLInputElement>(null);

  async function download() {
    if (pending) {
      return;
    }
    setError('');
    setPending('download');
    try {
      const archive = await downloadDatabaseBackup();
      triggerDownload(archive.blob, archive.filename);
    } catch (caught) {
      setError(errorMessage(caught, t.downloadFailed));
    } finally {
      setPending(undefined);
    }
  }

  async function restore() {
    if (!file || pending) {
      return;
    }
    setError('');
    setPending('restore');
    try {
      const response = requireApiData(await restoreDatabaseBackup(file), t.restoreFailed);
      if (response.loginRequired) {
        onRestoreCompleted();
        return;
      }
      setPending(undefined);
    } catch (caught) {
      setError(errorMessage(caught, t.restoreFailed));
      setPending(undefined);
    }
  }

  function selectFile(selected: File | undefined) {
    setError('');
    setFile(selected);
  }

  const busy = pending !== undefined;

  return (
    <section aria-label={t.ariaLabel} className="database-backup-page">
      <header className="database-backup-header">
        <div>
          <h1>{t.title}</h1>
        </div>
      </header>

      <section className="database-backup-operation" aria-labelledby="database-backup-export-title">
        <div className="database-backup-operation-heading">
          <div>
            <h2 id="database-backup-export-title">{t.backupTitle}</h2>
          </div>
          <button className="primary-button" disabled={busy} onClick={() => void download()} type="button">
            {pending === 'download' ? <LoaderCircle aria-hidden="true" className="spin" /> : <Download aria-hidden="true" />}
            <span>{pending === 'download' ? t.downloading : t.download}</span>
          </button>
        </div>
      </section>

      <section className="database-backup-operation database-backup-restore" aria-labelledby="database-backup-restore-title">
        <div className="database-backup-operation-heading">
          <div>
            <h2 id="database-backup-restore-title">{t.restoreTitle}</h2>
          </div>
        </div>
        <p className="database-backup-warning"><AlertTriangle aria-hidden="true" />{t.restoreWarning}</p>
        <div className="database-backup-file-controls">
          <label className="secondary-button database-backup-file-picker">
            <Upload aria-hidden="true" />
            <span>{t.chooseFile}</span>
            <input
              accept=".ambak,application/octet-stream"
              disabled={busy}
              onChange={(event) => selectFile(event.target.files?.[0])}
              ref={fileInputRef}
              type="file"
            />
          </label>
          <output className="database-backup-selected-file">{file?.name ?? t.noFileSelected}</output>
          {file ? (
            <button
              aria-label={t.clearFile}
              className="icon-button"
              disabled={busy}
              onClick={() => {
                selectFile(undefined);
                if (fileInputRef.current) {
                  fileInputRef.current.value = '';
                }
              }}
              type="button"
            >
              <X aria-hidden="true" />
            </button>
          ) : null}
        </div>
        <div className="database-backup-restore-action">
          <button
            className="danger-button"
            disabled={!file || busy}
            onClick={() => setConfirmationOpen(true)}
            type="button"
          >
            {pending === 'restore' ? <LoaderCircle aria-hidden="true" className="spin" /> : <AlertTriangle aria-hidden="true" />}
            <span>{pending === 'restore' ? t.restoring : t.overwrite}</span>
          </button>
          {pending === 'restore' ? <span role="status">{t.restoring}</span> : null}
        </div>
      </section>

      {error ? <p className="error-text" role="alert">{error}</p> : null}

      {confirmationOpen ? (
        <div className="database-backup-confirm-backdrop" role="presentation">
          <section aria-labelledby="database-backup-confirm-title" aria-modal="true" className="database-backup-confirm-dialog" role="dialog">
            <div className="database-backup-confirm-heading">
              <AlertTriangle aria-hidden="true" />
              <h2 id="database-backup-confirm-title">{t.confirmTitle}</h2>
            </div>
            <p>{t.confirmDescription}</p>
            <div className="database-backup-confirm-actions">
              <button className="secondary-button" disabled={busy} onClick={() => setConfirmationOpen(false)} type="button">{t.cancel}</button>
              <button className="danger-button" disabled={busy} onClick={() => void restore()} type="button">{t.confirmOverwrite}</button>
            </div>
          </section>
        </div>
      ) : null}
    </section>
  );
}

function triggerDownload(blob: Blob, filename: string) {
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = filename;
  document.body.append(anchor);
  anchor.click();
  anchor.remove();
  URL.revokeObjectURL(url);
}

function errorMessage(error: unknown, fallback: string): string {
  if (error instanceof ApiRequestError) {
    return error.message || fallback;
  }
  return error instanceof Error ? error.message : fallback;
}
