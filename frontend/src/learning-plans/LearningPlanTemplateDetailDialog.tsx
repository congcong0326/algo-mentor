import { ExternalLink, X } from 'lucide-react';
import { useEffect, useRef, useState } from 'react';
import type { KeyboardEvent } from 'react';
import { formatPlanLevel } from '../i18n/formatters';
import { useI18n } from '../i18n/I18nProvider';
import { getLearningPlanTemplate, requireApiData } from '../services/api';
import type {
  LearningPlanTemplateDetailResponse,
  LearningPlanTemplateSummaryResponse,
} from '../types/api';

interface LearningPlanTemplateDetailDialogProps {
  template: LearningPlanTemplateSummaryResponse;
  onClose: () => void;
}

const FOCUSABLE_SELECTOR = [
  'button:not([disabled])',
  'a[href]',
  '[tabindex]:not([tabindex="-1"]):not([disabled])',
].join(',');

export default function LearningPlanTemplateDetailDialog({
  template,
  onClose,
}: LearningPlanTemplateDetailDialogProps) {
  const { resources } = useI18n();
  const [detail, setDetail] = useState<LearningPlanTemplateDetailResponse>();
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [reloadKey, setReloadKey] = useState(0);
  const dialogRef = useRef<HTMLElement>(null);
  const closeButtonRef = useRef<HTMLButtonElement>(null);
  const priorFocusRef = useRef<HTMLElement | null>(
    document.activeElement instanceof HTMLElement ? document.activeElement : null,
  );

  useEffect(() => {
    const controller = new AbortController();
    setLoading(true);
    setError('');
    setDetail(undefined);

    void getLearningPlanTemplate(template.templateId, controller.signal)
      .then((response) => {
        if (!controller.signal.aborted) {
          setDetail(requireApiData(response, resources.learningPlans.templateDetailLoadFailed));
        }
      })
      .catch((nextError) => {
        if (!controller.signal.aborted) {
          setError(nextError instanceof Error
            ? nextError.message
            : resources.learningPlans.templateDetailLoadFailed);
        }
      })
      .finally(() => {
        if (!controller.signal.aborted) {
          setLoading(false);
        }
      });

    return () => controller.abort();
  }, [reloadKey, resources.learningPlans.templateDetailLoadFailed, template.contentLocale, template.templateId]);

  useEffect(() => {
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    const focusTimer = window.setTimeout(() => closeButtonRef.current?.focus());

    return () => {
      window.clearTimeout(focusTimer);
      document.body.style.overflow = previousOverflow;
      if (priorFocusRef.current?.isConnected) {
        priorFocusRef.current.focus();
      }
    };
  }, []);

  function focusableElements(): HTMLElement[] {
    return Array.from(dialogRef.current?.querySelectorAll<HTMLElement>(FOCUSABLE_SELECTOR) ?? [])
      .filter((element) => !element.hasAttribute('disabled') && element.tabIndex >= 0);
  }

  function handleKeyDown(event: KeyboardEvent<HTMLElement>) {
    if (event.key === 'Escape') {
      event.preventDefault();
      onClose();
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

  return (
    <div className="modal-backdrop template-detail-backdrop" onMouseDown={onClose} role="presentation">
      <section
        aria-labelledby="template-detail-title"
        aria-modal="true"
        className="template-detail-dialog"
        onKeyDown={handleKeyDown}
        onMouseDown={(event) => event.stopPropagation()}
        ref={dialogRef}
        role="dialog"
        tabIndex={-1}
      >
        <header className="template-detail-header">
          <div>
            <p className="eyebrow">{resources.learningPlans.templateDetailEyebrow}</p>
            <h2 id="template-detail-title">{template.title}</h2>
            <p>{template.summary}</p>
          </div>
          <button
            aria-label={resources.common.close}
            className="icon-button"
            onClick={onClose}
            ref={closeButtonRef}
            type="button"
          >
            <X aria-hidden="true" />
          </button>
        </header>

        <div className="template-detail-body">
          {loading && (
            <p className="template-detail-state" role="status">
              {resources.learningPlans.templateDetailLoading}
            </p>
          )}

          {!loading && error && (
            <div className="template-detail-state error" role="alert">
              <span>{error}</span>
              <button className="secondary-button compact" onClick={() => setReloadKey((value) => value + 1)} type="button">
                {resources.app.retry}
              </button>
            </div>
          )}

          {!loading && detail && (
            <>
              <dl className="template-detail-facts">
                <div>
                  <dt>{resources.learningPlans.templateGoal}</dt>
                  <dd>{detail.goal}</dd>
                </div>
                <div>
                  <dt>{resources.learningPlans.templateTargetAudience}</dt>
                  <dd>{detail.targetAudience}</dd>
                </div>
                <div>
                  <dt>{resources.learningPlans.templateExpectedOutcome}</dt>
                  <dd>{detail.expectedOutcome}</dd>
                </div>
              </dl>

              <div aria-label={resources.learningPlans.templateOverview} className="template-detail-meta" role="list">
                <span role="listitem">{formatPlanLevel(detail.level, resources)}</span>
                <span role="listitem">{resources.learningPlans.templateRouteSummary(
                  detail.plannedProblemCount,
                  detail.defaultDurationWeeks,
                  detail.defaultWeeklyHours,
                )}</span>
                {detail.programmingLanguage && <span role="listitem">{detail.programmingLanguage}</span>}
              </div>

              <section className="template-detail-section" aria-labelledby="template-detail-route-title">
                <div className="template-detail-section-heading">
                  <p className="eyebrow">{resources.learningPlans.templatePhaseRoute}</p>
                  <h3 id="template-detail-route-title">{resources.learningPlans.templatePhaseRouteTitle}</h3>
                </div>
                <ol className="template-phase-list">
                  {detail.phases.map((phase) => (
                    <li key={phase.phaseIndex}>
                      <span className="template-phase-index" aria-hidden="true">{phase.phaseIndex}</span>
                      <div>
                        <div className="template-phase-heading">
                          <h4>{phase.title}</h4>
                          <span>{resources.learningPlans.templatePhaseSummary(
                            phase.durationWeeks,
                            phase.plannedProblemCount,
                          )}</span>
                        </div>
                        <p>{phase.focus}</p>
                      </div>
                    </li>
                  ))}
                </ol>
              </section>

              <section className="template-detail-section" aria-labelledby="template-detail-fit-title">
                <div className="template-detail-section-heading">
                  <p className="eyebrow">{resources.learningPlans.templateFitEyebrow}</p>
                  <h3 id="template-detail-fit-title">{resources.learningPlans.templateFitTitle}</h3>
                </div>
                <div className="template-fit-grid">
                  <TemplateDetailList title={resources.learningPlans.templatePrerequisites} values={detail.prerequisites} />
                  <TemplateDetailList title={resources.learningPlans.templateRecommendedFor} values={detail.recommendedFor} />
                  <TemplateDetailList title={resources.learningPlans.templateNotRecommendedFor} values={detail.notRecommendedFor} />
                </div>
              </section>

              {detail.sourceName && detail.sourceUrl && (
                <footer className="template-detail-source">
                  <span>{resources.learningPlans.templateSource}: {detail.sourceName}</span>
                  <a href={detail.sourceUrl} rel="noreferrer" target="_blank">
                    <span>{resources.learningPlans.templateOpenSource}</span>
                    <ExternalLink aria-hidden="true" />
                  </a>
                </footer>
              )}
            </>
          )}
        </div>
      </section>
    </div>
  );
}

function TemplateDetailList({ title, values }: { title: string; values: string[] }) {
  if (values.length === 0) {
    return null;
  }

  return (
    <div>
      <h4>{title}</h4>
      <ul>
        {values.map((value) => <li key={value}>{value}</li>)}
      </ul>
    </div>
  );
}
