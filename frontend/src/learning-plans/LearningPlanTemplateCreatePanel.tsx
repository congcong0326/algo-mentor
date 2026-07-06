import { Database, GitCommit, Layers, Sparkles } from 'lucide-react';
import { useEffect, useMemo, useState } from 'react';
import {
  getLearningPlanTemplate,
  getLearningPlanTemplates,
  requireApiData,
} from '../services/api';
import type {
  LearningPlanTemplateDetailResponse,
  LearningPlanTemplateDraftRequest,
  LearningPlanTemplateSummaryResponse,
} from '../types/api';
import { formatPlanLevel } from '../i18n/formatters';
import { useI18n } from '../i18n/I18nProvider';
import { programmingLanguageOptions } from './options';

interface LearningPlanTemplateCreatePanelProps {
  loading: boolean;
  error?: string;
  onCancel?: () => void;
  onSubmit: (request: LearningPlanTemplateDraftRequest) => void;
}

const DEFAULT_PROGRAMMING_LANGUAGE = 'Java';

export default function LearningPlanTemplateCreatePanel({
  loading,
  error,
  onCancel,
  onSubmit,
}: LearningPlanTemplateCreatePanelProps) {
  const { resources } = useI18n();
  const [templates, setTemplates] = useState<LearningPlanTemplateSummaryResponse[]>([]);
  const [selectedTemplateId, setSelectedTemplateId] = useState('');
  const [selectedTemplateDetail, setSelectedTemplateDetail] = useState<LearningPlanTemplateDetailResponse>();
  const [durationWeeks, setDurationWeeks] = useState(1);
  const [weeklyHours, setWeeklyHours] = useState(1);
  const [programmingLanguage, setProgrammingLanguage] = useState(DEFAULT_PROGRAMMING_LANGUAGE);
  const [listLoading, setListLoading] = useState(true);
  const [detailLoading, setDetailLoading] = useState(false);
  const [loadError, setLoadError] = useState('');
  const [validationError, setValidationError] = useState('');

  const selectedTemplate = useMemo(
    () => templates.find((template) => template.templateId === selectedTemplateId),
    [selectedTemplateId, templates],
  );
  const minimumDurationWeeks = selectedTemplateDetail?.phases.length ?? 1;
  const effectiveError = validationError || error || loadError;
  const submitDisabled = loading || listLoading || detailLoading || !selectedTemplate;

  useEffect(() => {
    const controller = new AbortController();
    setListLoading(true);
    setLoadError('');

    void getLearningPlanTemplates(controller.signal)
      .then((response) => {
        const nextTemplates = requireApiData(response, resources.learningPlans.templateLoadFailed);
        setTemplates(nextTemplates);
        const [firstTemplate] = nextTemplates;
        if (firstTemplate) {
          setSelectedTemplateId(firstTemplate.templateId);
          setDurationWeeks(firstTemplate.defaultDurationWeeks);
          setWeeklyHours(firstTemplate.defaultWeeklyHours);
        }
      })
      .catch((nextError) => {
        if (controller.signal.aborted) {
          return;
        }
        setLoadError(nextError instanceof Error ? nextError.message : resources.learningPlans.templateLoadFailed);
      })
      .finally(() => {
        if (!controller.signal.aborted) {
          setListLoading(false);
        }
      });

    return () => controller.abort();
  }, [resources.learningPlans.templateLoadFailed]);

  useEffect(() => {
    if (!selectedTemplate) {
      setSelectedTemplateDetail(undefined);
      return undefined;
    }

    const controller = new AbortController();
    setSelectedTemplateDetail(undefined);
    setDurationWeeks(selectedTemplate.defaultDurationWeeks);
    setWeeklyHours(selectedTemplate.defaultWeeklyHours);
    setProgrammingLanguage(DEFAULT_PROGRAMMING_LANGUAGE);
    setDetailLoading(true);
    setLoadError('');

    void getLearningPlanTemplate(selectedTemplate.templateId, controller.signal)
      .then((response) => {
        const detail = requireApiData(response, resources.learningPlans.templateDetailLoadFailed);
        setSelectedTemplateDetail(detail);
        setProgrammingLanguage(detail.programmingLanguage?.trim() || DEFAULT_PROGRAMMING_LANGUAGE);
      })
      .catch((nextError) => {
        if (controller.signal.aborted) {
          return;
        }
        setLoadError(nextError instanceof Error ? nextError.message : resources.learningPlans.templateDetailLoadFailed);
      })
      .finally(() => {
        if (!controller.signal.aborted) {
          setDetailLoading(false);
        }
      });

    return () => controller.abort();
  }, [resources.learningPlans.templateDetailLoadFailed, selectedTemplate]);

  function selectTemplate(template: LearningPlanTemplateSummaryResponse) {
    if (loading || listLoading) {
      return;
    }
    setValidationError('');
    setSelectedTemplateId(template.templateId);
  }

  function submit() {
    if (!selectedTemplate) {
      setValidationError(resources.learningPlans.templateEmpty);
      return;
    }
    if (!Number.isInteger(durationWeeks) || durationWeeks <= 0 || !Number.isInteger(weeklyHours) || weeklyHours <= 0) {
      setValidationError(resources.learningPlans.validationPositiveIntegers);
      return;
    }
    if (durationWeeks < minimumDurationWeeks) {
      setValidationError(resources.learningPlans.templateDurationTooShort(minimumDurationWeeks));
      return;
    }

    setValidationError('');
    onSubmit({
      templateId: selectedTemplate.templateId,
      durationWeeks,
      weeklyHours,
      programmingLanguage: programmingLanguage.trim() || undefined,
    });
  }

  return (
    <>
      {effectiveError && <p className="error-text" role="alert">{effectiveError}</p>}

      <div className="template-create-panel">
        {listLoading && <p className="empty-log">{resources.learningPlans.templateLoading}</p>}
        {!listLoading && templates.length === 0 && !loadError && (
          <p className="empty-log">{resources.learningPlans.templateEmpty}</p>
        )}

        {templates.length > 0 && (
          <section className="template-card-grid" aria-label={resources.learningPlans.createFromTemplate}>
            {templates.map((template) => (
              <button
                aria-pressed={selectedTemplateId === template.templateId}
                className={`template-card${selectedTemplateId === template.templateId ? ' selected' : ''}`}
                disabled={loading}
                key={template.templateId}
                onClick={() => selectTemplate(template)}
                type="button"
              >
                <span className="template-card-title">
                  <Layers aria-hidden="true" />
                  <strong>{template.title}</strong>
                </span>
                <span>{template.summary}</span>
                <span className="template-card-meta">
                  {resources.learningPlans.templateDefaultRhythm(
                    template.defaultDurationWeeks,
                    template.defaultWeeklyHours,
                  )}
                </span>
                {template.sourceCommit && (
                  <span className="template-card-meta">
                    <GitCommit aria-hidden="true" />
                    {resources.learningPlans.templateSourceCommit(template.sourceCommit)}
                  </span>
                )}
                <span className="template-card-meta">
                  <Database aria-hidden="true" />
                  {resources.learningPlans.templateProblemStats(
                    template.matchedProblemCount,
                    template.missingProblemCount,
                    template.problemCount,
                  )}
                </span>
              </button>
            ))}
          </section>
        )}

        {selectedTemplate && (
          <>
            <section className="template-detail-strip" aria-label={resources.learningPlans.templateSelected}>
              <div>
                <span>{resources.learningPlans.templateTargetAudience}</span>
                <strong>{selectedTemplate.targetAudience}</strong>
              </div>
              <div>
                <span>{resources.learningPlans.templateExpectedOutcome}</span>
                <strong>{selectedTemplate.expectedOutcome}</strong>
              </div>
              <div>
                <span>{resources.learningPlans.level}</span>
                <strong>{formatPlanLevel(selectedTemplate.level, resources)}</strong>
              </div>
            </section>

            {detailLoading && <p className="empty-log">{resources.learningPlans.templateDetailLoading}</p>}
            {!detailLoading && selectedTemplateDetail && selectedTemplate.missingProblemCount > 0 && (
              <p className="empty-log">
                {resources.learningPlans.templateMissingNotice(selectedTemplate.missingProblemCount)}
              </p>
            )}

            <div className="mini-grid">
              <label className="topic-field">
                <span>{resources.learningPlans.duration}</span>
                <input
                  aria-label={resources.learningPlans.durationInput}
                  disabled={loading}
                  min={minimumDurationWeeks}
                  onChange={(event) => setDurationWeeks(Number(event.target.value))}
                  type="number"
                  value={durationWeeks}
                />
              </label>
              <label className="topic-field">
                <span>{resources.learningPlans.weeklyHours}</span>
                <input
                  aria-label={resources.learningPlans.weeklyHours}
                  disabled={loading}
                  min={1}
                  onChange={(event) => setWeeklyHours(Number(event.target.value))}
                  type="number"
                  value={weeklyHours}
                />
              </label>
            </div>

            <label className="topic-field">
              <span>{resources.learningPlans.programmingLanguage}</span>
              <select
                aria-label={resources.learningPlans.programmingLanguage}
                disabled={loading || detailLoading}
                onChange={(event) => setProgrammingLanguage(event.target.value)}
                value={programmingLanguage}
              >
                {programmingLanguageOptions.map((option) => (
                  <option key={option} value={option}>{option}</option>
                ))}
              </select>
            </label>
          </>
        )}
      </div>

      <div className="modal-actions">
        {onCancel && (
          <button className="secondary-button" disabled={loading} onClick={onCancel} type="button">
            {resources.common.cancel}
          </button>
        )}
        <button className="primary-button" disabled={submitDisabled} onClick={submit} type="button">
          <Sparkles aria-hidden="true" />
          <span>{loading ? resources.learningPlans.generating : resources.learningPlans.templateGenerateDraft}</span>
        </button>
      </div>
    </>
  );
}
