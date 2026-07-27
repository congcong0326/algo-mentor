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
  LearningPlanRhythmSettings,
  LearningPlanTemplateSummaryResponse,
} from '../types/api';
import { formatPlanLevel } from '../i18n/formatters';
import { useI18n } from '../i18n/I18nProvider';
import { programmingLanguageOptions } from './options';
import {
  buildStandardRhythmReference,
  estimateRhythmWeeks,
} from './learningPlanRhythm';

interface LearningPlanTemplateCreatePanelProps {
  loading: boolean;
  error?: string;
  onCancel?: () => void;
  onSubmit: (request: LearningPlanTemplateDraftRequest) => void;
}

const DEFAULT_PROGRAMMING_LANGUAGE = 'Java';
const DEFAULT_DAILY_PROBLEM_COUNT = 1;
const DEFAULT_TRAINING_DAYS_PER_WEEK = 5;
const MIN_DAILY_PROBLEM_COUNT = 1;
const MAX_DAILY_PROBLEM_COUNT = 10;
const MIN_TRAINING_DAYS_PER_WEEK = 1;
const MAX_TRAINING_DAYS_PER_WEEK = 7;

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
  const [dailyProblemCount, setDailyProblemCount] = useState(DEFAULT_DAILY_PROBLEM_COUNT);
  const [trainingDaysPerWeek, setTrainingDaysPerWeek] = useState(DEFAULT_TRAINING_DAYS_PER_WEEK);
  const [programmingLanguage, setProgrammingLanguage] = useState(DEFAULT_PROGRAMMING_LANGUAGE);
  const [listLoading, setListLoading] = useState(true);
  const [detailLoading, setDetailLoading] = useState(false);
  const [loadError, setLoadError] = useState('');
  const [validationError, setValidationError] = useState('');

  const selectedTemplate = useMemo(
    () => templates.find((template) => template.templateId === selectedTemplateId),
    [selectedTemplateId, templates],
  );
  const defaultRhythmSettings = selectedTemplateDetail?.defaultRhythmSettings ?? selectedTemplate?.defaultRhythmSettings;
  const totalProblemCount = defaultRhythmSettings?.totalProblemCount
    ?? selectedTemplateDetail?.matchedProblemCount
    ?? selectedTemplate?.matchedProblemCount
    ?? 0;
  const standardRhythm = selectedTemplate
    ? buildStandardRhythmReference({
      recommendedWeeks: selectedTemplate.defaultDurationWeeks,
      settings: defaultRhythmSettings,
      totalProblemCount,
    })
    : undefined;
  const estimatedWeeks = estimateRhythmWeeks(totalProblemCount, dailyProblemCount, trainingDaysPerWeek);
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
          applyDefaultRhythm(firstTemplate.defaultRhythmSettings);
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
    applyDefaultRhythm(selectedTemplate.defaultRhythmSettings);
    setProgrammingLanguage(DEFAULT_PROGRAMMING_LANGUAGE);
    setDetailLoading(true);
    setLoadError('');

    void getLearningPlanTemplate(selectedTemplate.templateId, controller.signal)
      .then((response) => {
        const detail = requireApiData(response, resources.learningPlans.templateDetailLoadFailed);
        setSelectedTemplateDetail(detail);
        setProgrammingLanguage(detail.programmingLanguage?.trim() || DEFAULT_PROGRAMMING_LANGUAGE);
        applyDefaultRhythm(detail.defaultRhythmSettings);
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
    setValidationError('');
    onSubmit({
      templateId: selectedTemplate.templateId,
      dailyProblemCount,
      trainingDaysPerWeek,
      programmingLanguage: programmingLanguage.trim() || undefined,
    });
  }

  function applyDefaultRhythm(settings?: LearningPlanRhythmSettings) {
    setDailyProblemCount(settings?.dailyProblemCount ?? DEFAULT_DAILY_PROBLEM_COUNT);
    setTrainingDaysPerWeek(settings?.trainingDaysPerWeek ?? DEFAULT_TRAINING_DAYS_PER_WEEK);
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
                  <span className="template-card-icon" aria-hidden="true">
                    <Layers />
                  </span>
                  <strong>{template.title}</strong>
                </span>
                <span>{template.summary}</span>
                <span className="template-card-meta">
                  {resources.learningPlans.templateRouteSummary(
                    template.defaultLoadSummary?.plannedProblemCount ?? template.matchedProblemCount,
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

            <section className="question-block">
              <strong>{resources.learningPlans.standardRhythmTitle}</strong>
              {standardRhythm && (
                <div className="standard-rhythm-card">
                  <strong>
                    {resources.learningPlans.standardRhythmMainLine(
                      standardRhythm.dailyProblemCount,
                      standardRhythm.trainingDaysPerWeek,
                      standardRhythm.recommendedWeeks,
                    )}
                  </strong>
                  <p>
                    {resources.learningPlans.standardRhythmReason(
                      standardRhythm.totalProblemCount,
                      standardRhythm.recommendedWeeks,
                      standardRhythm.trainingDaysPerWeek,
                    )}
                  </p>
                </div>
              )}
              <div className="rhythm-stepper-grid">
                <label>
                  <span>{resources.learningPlans.dailyProblemCount}</span>
                  <input
                    aria-label={resources.learningPlans.dailyProblemCount}
                    disabled={loading || detailLoading}
                    max={MAX_DAILY_PROBLEM_COUNT}
                    min={MIN_DAILY_PROBLEM_COUNT}
                    onChange={(event) => setDailyProblemCount(clampNumber(
                      event.target.valueAsNumber,
                      MIN_DAILY_PROBLEM_COUNT,
                      MAX_DAILY_PROBLEM_COUNT,
                    ))}
                    type="number"
                    value={dailyProblemCount}
                  />
                </label>
                <label>
                  <span>{resources.learningPlans.trainingDaysPerWeek}</span>
                  <input
                    aria-label={resources.learningPlans.trainingDaysPerWeek}
                    disabled={loading || detailLoading}
                    max={MAX_TRAINING_DAYS_PER_WEEK}
                    min={MIN_TRAINING_DAYS_PER_WEEK}
                    onChange={(event) => setTrainingDaysPerWeek(clampNumber(
                      event.target.valueAsNumber,
                      MIN_TRAINING_DAYS_PER_WEEK,
                      MAX_TRAINING_DAYS_PER_WEEK,
                    ))}
                    type="number"
                    value={trainingDaysPerWeek}
                  />
                </label>
              </div>
              <p className="rhythm-estimate-line">
                {resources.learningPlans.currentRhythmEstimateLine(
                  dailyProblemCount,
                  trainingDaysPerWeek,
                  totalProblemCount,
                  estimatedWeeks,
                )}
              </p>
            </section>

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

function clampNumber(value: number, min: number, max: number) {
  if (!Number.isFinite(value)) {
    return min;
  }
  return Math.max(min, Math.min(max, Math.trunc(value)));
}
