import { X } from 'lucide-react';
import { useEffect, useRef, useState } from 'react';
import type { KeyboardEvent } from 'react';
import { learnerProfileStatementAnchorId } from '../app/navigation';
import { useI18n } from '../i18n/I18nProvider';
import { getLearnerProfileStatementEvidence, requireApiData } from '../services/api';
import type {
  LearnerProfileCitation,
  LearnerProfileEvidenceItem,
} from '../types/api';
import LearnerProfileEvidenceTimeline from './LearnerProfileEvidenceTimeline';
import { learnerProfileEvidenceKey } from './profilePresentation';

interface LearnerProfileEvidenceDrawerProps {
  citation?: LearnerProfileCitation;
  onClose: () => void;
  open: boolean;
}

const FOCUSABLE_SELECTOR = [
  'button:not([disabled])',
  'a[href]',
  '[tabindex]:not([tabindex="-1"]):not([disabled])',
].join(',');

export default function LearnerProfileEvidenceDrawer({
  citation,
  onClose,
  open,
}: LearnerProfileEvidenceDrawerProps) {
  const { resources } = useI18n();
  const [items, setItems] = useState<LearnerProfileEvidenceItem[]>([]);
  const [loadedStatementRef, setLoadedStatementRef] = useState<string>();
  const [nextCursor, setNextCursor] = useState<string>();
  const [loading, setLoading] = useState(false);
  const [loadingMore, setLoadingMore] = useState(false);
  const [error, setError] = useState('');
  const dialogRef = useRef<HTMLElement>(null);
  const closeButtonRef = useRef<HTMLButtonElement>(null);
  const priorFocusRef = useRef<HTMLElement | null>(null);
  const wasOpenRef = useRef(false);
  const abortRef = useRef<AbortController | undefined>(undefined);
  const requestIdRef = useRef(0);
  const completedCursorsRef = useRef(new Set<string>());
  const inFlightCursorRef = useRef<string | undefined>(undefined);

  const visibleItems = loadedStatementRef === citation?.statementRef ? items : [];
  const visibleNextCursor = loadedStatementRef === citation?.statementRef ? nextCursor : undefined;

  useEffect(() => {
    if (!open || !citation) {
      abortRef.current?.abort();
      return undefined;
    }

    abortRef.current?.abort();
    completedCursorsRef.current = new Set();
    inFlightCursorRef.current = undefined;
    setItems([]);
    setLoadedStatementRef(undefined);
    setNextCursor(undefined);
    setError('');
    void loadPage(citation, undefined, false);

    return () => abortRef.current?.abort();
  }, [citation?.statementRef, open]);

  useEffect(() => {
    if (open && !wasOpenRef.current) {
      priorFocusRef.current = document.activeElement instanceof HTMLElement ? document.activeElement : null;
      window.setTimeout(() => closeButtonRef.current?.focus());
    }
    if (!open && wasOpenRef.current) {
      restoreFocus();
    }
    wasOpenRef.current = open;
    return () => {
      if (wasOpenRef.current) {
        restoreFocus();
        wasOpenRef.current = false;
      }
    };
  }, [open]);

  useEffect(() => {
    if (!open) {
      return undefined;
    }
    const originalOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    return () => {
      document.body.style.overflow = originalOverflow;
    };
  }, [open]);

  function restoreFocus() {
    if (priorFocusRef.current?.isConnected) {
      priorFocusRef.current.focus();
    }
    priorFocusRef.current = null;
  }

  function focusableElements(): HTMLElement[] {
    return Array.from(dialogRef.current?.querySelectorAll<HTMLElement>(FOCUSABLE_SELECTOR) ?? [])
      .filter((element) => !element.hasAttribute('disabled') && element.tabIndex >= 0);
  }

  function close() {
    abortRef.current?.abort();
    onClose();
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
    const focusable = focusableElements();
    if (focusable.length === 0) {
      event.preventDefault();
      dialogRef.current?.focus();
      return;
    }
    const first = focusable[0];
    const last = focusable[focusable.length - 1];
    const active = document.activeElement;
    if (event.shiftKey && (active === first || !dialogRef.current?.contains(active))) {
      event.preventDefault();
      last.focus();
    } else if (!event.shiftKey && active === last) {
      event.preventDefault();
      first.focus();
    }
  }

  async function loadPage(
    targetCitation: LearnerProfileCitation,
    cursor: string | undefined,
    append: boolean,
  ) {
    const cursorKey = cursor ?? '__first__';
    if (append && (
      loadingMore
      || inFlightCursorRef.current === cursorKey
      || completedCursorsRef.current.has(cursorKey)
    )) {
      return;
    }

    abortRef.current?.abort();
    inFlightCursorRef.current = cursorKey;
    const controller = new AbortController();
    const requestId = requestIdRef.current + 1;
    requestIdRef.current = requestId;
    abortRef.current = controller;
    if (append) {
      setLoadingMore(true);
    } else {
      setLoading(true);
    }
    setError('');

    try {
      const response = await getLearnerProfileStatementEvidence(
        targetCitation.statementRef,
        { cursor, limit: 20 },
        controller.signal,
      );
      const page = requireApiData(response, resources.myPage.evidenceLoadFailed);
      if (controller.signal.aborted || requestId !== requestIdRef.current) {
        return;
      }
      completedCursorsRef.current.add(cursorKey);
      setItems((current) => {
        const nextItems = append ? [...current, ...page.items] : page.items;
        const seen = new Set<string>();
        return nextItems.filter((item) => {
          const key = learnerProfileEvidenceKey(item);
          if (seen.has(key)) {
            return false;
          }
          seen.add(key);
          return true;
        });
      });
      setLoadedStatementRef(targetCitation.statementRef);
      setNextCursor(page.nextCursor ?? undefined);
    } catch (requestError) {
      if (!controller.signal.aborted && requestId === requestIdRef.current) {
        setError(resources.myPage.evidenceLoadFailed);
      }
    } finally {
      if (!controller.signal.aborted && requestId === requestIdRef.current) {
        setLoading(false);
        setLoadingMore(false);
        inFlightCursorRef.current = undefined;
      }
    }
  }

  function retry() {
    if (citation) {
      void loadPage(citation, undefined, false);
    }
  }

  function loadMore() {
    if (citation && visibleNextCursor) {
      void loadPage(citation, visibleNextCursor, true);
    }
  }

  if (!open || !citation) {
    return null;
  }

  return (
    <div className="learner-profile-drawer-backdrop" onMouseDown={close} role="presentation">
      <section
        aria-labelledby="learner-profile-evidence-title"
        aria-modal="true"
        className="learner-profile-evidence-drawer"
        id="learner-profile-evidence-drawer"
        onKeyDown={handleKeyDown}
        onMouseDown={(event) => event.stopPropagation()}
        ref={dialogRef}
        role="dialog"
        tabIndex={-1}
      >
        <header className="learner-profile-evidence-drawer-header">
          <div>
            <p className="my-section-eyebrow">{resources.myPage.evidenceEyebrow}</p>
            <h2 id="learner-profile-evidence-title">{resources.myPage.evidenceDrawerTitle(citation.displayNumber)}</h2>
            <p>{citation.sourceSummary}</p>
          </div>
          <button
            aria-label={resources.myPage.closeEvidenceDrawer}
            className="icon-button"
            onClick={close}
            ref={closeButtonRef}
            type="button"
          >
            <X aria-hidden="true" />
          </button>
        </header>

        <div className="learner-profile-evidence-drawer-body">
          {loading ? <p className="learner-profile-evidence-state" role="status">{resources.myPage.evidenceLoading}</p> : null}
          {!loading && error ? (
            <div className="learner-profile-evidence-state error" role="alert">
              <span>{error}</span>
              <button className="secondary-button compact" onClick={retry} type="button">{resources.app.retry}</button>
            </div>
          ) : null}
          {!loading && !error && visibleItems.length === 0 ? (
            <p className="learner-profile-evidence-state">{resources.myPage.evidenceEmpty}</p>
          ) : null}
          {visibleItems.length > 0 ? (
            <LearnerProfileEvidenceTimeline
              items={visibleItems}
              profileAnchor={learnerProfileStatementAnchorId(citation.claimRevisionId)}
            />
          ) : null}
          {visibleNextCursor ? (
            <button
              className="secondary-button learner-profile-load-more"
              disabled={loadingMore}
              onClick={loadMore}
              type="button"
            >
              {loadingMore ? resources.myPage.evidenceLoadingMore : resources.myPage.evidenceLoadMore}
            </button>
          ) : null}
          {!loading && !error && visibleItems.length > 0 && !visibleNextCursor ? (
            <p className="learner-profile-evidence-end">{resources.myPage.evidenceEnd}</p>
          ) : null}
        </div>
      </section>
    </div>
  );
}
