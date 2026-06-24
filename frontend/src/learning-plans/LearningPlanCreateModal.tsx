import { X } from 'lucide-react';
import { useEffect, useRef, useState } from 'react';
import type { KeyboardEvent } from 'react';
import { useI18n } from '../i18n/I18nProvider';
import type { LearningPlanCreateDraftRequest } from '../types/api';
import LearningPlanCreateForm from './LearningPlanCreateForm';

interface LearningPlanCreateModalProps {
  open: boolean;
  loading: boolean;
  error?: string;
  onClose: () => void;
  onSubmit: (request: LearningPlanCreateDraftRequest) => void;
}

const FOCUSABLE_SELECTOR = [
  'button:not([disabled])',
  'input:not([disabled])',
  'select:not([disabled])',
  'textarea:not([disabled])',
  'a[href]',
  '[tabindex]:not([tabindex="-1"]):not([disabled])',
].join(',');

export default function LearningPlanCreateModal({
  open,
  loading,
  error,
  onClose,
  onSubmit,
}: LearningPlanCreateModalProps) {
  const { resources } = useI18n();
  const [hasUnsavedInput, setHasUnsavedInput] = useState(false);
  const dialogRef = useRef<HTMLElement>(null);
  const closeButtonRef = useRef<HTMLButtonElement>(null);
  const previouslyFocusedElementRef = useRef<HTMLElement | null>(null);
  const wasOpenRef = useRef(false);

  function getFocusableElements() {
    return Array.from(dialogRef.current?.querySelectorAll<HTMLElement>(FOCUSABLE_SELECTOR) ?? [])
      .filter((element) => !element.hasAttribute('disabled') && element.tabIndex >= 0);
  }

  function focusInitialControl() {
    if (closeButtonRef.current && !closeButtonRef.current.disabled) {
      closeButtonRef.current.focus();
      return;
    }

    const [firstElement] = getFocusableElements();
    (firstElement ?? dialogRef.current)?.focus();
  }

  function restorePreviousFocus() {
    const previous = previouslyFocusedElementRef.current;

    if (previous?.isConnected) {
      previous.focus();
    }
    previouslyFocusedElementRef.current = null;
  }

  useEffect(() => {
    if (open && !wasOpenRef.current) {
      previouslyFocusedElementRef.current = document.activeElement instanceof HTMLElement
        ? document.activeElement
        : null;
      focusInitialControl();
    }

    if (!open && wasOpenRef.current) {
      restorePreviousFocus();
    }

    wasOpenRef.current = open;

    return () => {
      if (wasOpenRef.current) {
        restorePreviousFocus();
        wasOpenRef.current = false;
      }
    };
  }, [open]);

  if (!open) {
    return null;
  }

  function close() {
    if (loading) {
      return;
    }
    if (hasUnsavedInput && !window.confirm(resources.learningPlans.confirmDiscard)) {
      return;
    }
    onClose();
    restorePreviousFocus();
  }

  function handleKeyDown(event: KeyboardEvent<HTMLElement>) {
    if (event.key === 'Escape') {
      event.preventDefault();
      close();
      return;
    }

    if (event.key !== 'Tab') {
      return;
    }

    const focusableElements = getFocusableElements();
    if (focusableElements.length === 0) {
      event.preventDefault();
      dialogRef.current?.focus();
      return;
    }

    const firstElement = focusableElements[0];
    const lastElement = focusableElements[focusableElements.length - 1];
    const activeElement = document.activeElement;

    if (event.shiftKey && (activeElement === firstElement || !dialogRef.current?.contains(activeElement))) {
      event.preventDefault();
      lastElement.focus();
      return;
    }

    if (!event.shiftKey && activeElement === lastElement) {
      event.preventDefault();
      firstElement.focus();
    }
  }

  return (
    <div className="modal-backdrop">
      <section
        aria-labelledby="create-plan-title"
        aria-modal="true"
        className="create-plan-modal"
        onKeyDown={handleKeyDown}
        ref={dialogRef}
        role="dialog"
        tabIndex={-1}
      >
        <div className="modal-heading">
          <div>
            <p className="eyebrow">{resources.learningPlans.newPlan}</p>
            <h2 id="create-plan-title">{resources.learningPlans.createTitle}</h2>
          </div>
          <button
            aria-label={resources.common.close}
            className="icon-button"
            disabled={loading}
            onClick={close}
            ref={closeButtonRef}
            type="button"
          >
            <X aria-hidden="true" />
          </button>
        </div>

        <LearningPlanCreateForm
          confirmOnCancel={false}
          error={error}
          loading={loading}
          onCancel={close}
          onDirtyChange={setHasUnsavedInput}
          onSubmit={onSubmit}
        />
      </section>
    </div>
  );
}
