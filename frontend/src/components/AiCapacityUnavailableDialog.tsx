import { useEffect, useRef } from 'react';
import type { KeyboardEvent } from 'react';
import { useI18n } from '../i18n/I18nProvider';

interface AiCapacityUnavailableDialogProps {
  open: boolean;
  onClose: () => void;
}

/** 在 AI 执行分组容量耗尽时，显式告知用户稍后重试。 */
export default function AiCapacityUnavailableDialog({ open, onClose }: AiCapacityUnavailableDialogProps) {
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
        aria-describedby="ai-capacity-unavailable-description"
        aria-labelledby="ai-capacity-unavailable-title"
        aria-modal="true"
        className="ai-capacity-unavailable-dialog"
        onKeyDown={handleKeyDown}
        role="dialog"
        tabIndex={-1}
      >
        <h2 id="ai-capacity-unavailable-title">{resources.common.notice}</h2>
        <p id="ai-capacity-unavailable-description">{resources.common.aiCapacityUnavailable}</p>
        <div className="modal-actions ai-capacity-unavailable-actions">
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
