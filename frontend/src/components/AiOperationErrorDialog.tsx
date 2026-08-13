import { useEffect, useRef } from 'react';
import type { KeyboardEvent } from 'react';
import { useI18n } from '../i18n/I18nProvider';

interface AiOperationErrorDialogProps {
  message: string;
  onClose: () => void;
  open: boolean;
  reason?: string;
}

/** 在页面任意滚动位置清晰呈现 AI 操作失败及其可展示原因。 */
export default function AiOperationErrorDialog({
  message,
  onClose,
  open,
  reason,
}: AiOperationErrorDialogProps) {
  const { resources } = useI18n();
  const closeButtonRef = useRef<HTMLButtonElement>(null);

  useEffect(() => {
    if (open) {
      closeButtonRef.current?.focus();
    }
  }, [open]);

  if (!open) {
    return null;
  }

  function handleKeyDown(event: KeyboardEvent<HTMLElement>) {
    if (event.key === 'Escape') {
      event.preventDefault();
      onClose();
      return;
    }
    if (event.key === 'Tab') {
      event.preventDefault();
      closeButtonRef.current?.focus();
    }
  }

  return (
    <div className="modal-backdrop" role="presentation">
      <section
        aria-describedby={reason ? 'ai-operation-error-reason' : undefined}
        aria-labelledby="ai-operation-error-title"
        aria-modal="true"
        className="ai-operation-error-dialog"
        onKeyDown={handleKeyDown}
        role="alertdialog"
        tabIndex={-1}
      >
        <h2 id="ai-operation-error-title">{message}</h2>
        {reason ? <p id="ai-operation-error-reason">{reason}</p> : null}
        <div className="modal-actions ai-operation-error-actions">
          <button
            className="primary-button compact"
            onClick={onClose}
            ref={closeButtonRef}
            type="button"
          >
            {resources.common.close}
          </button>
        </div>
      </section>
    </div>
  );
}
