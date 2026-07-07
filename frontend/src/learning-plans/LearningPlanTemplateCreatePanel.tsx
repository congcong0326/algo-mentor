import { Database, GitCommit, Layers, Sparkles } from 'lucide-react';
import { useEffect, useMemo, useState } from 'react';
import {
  getLearningPlanTemplate,
  getLearningPlanTemplates,
  requireApiData,
} from '../services/api';
import type {
  LearningPlanRhythmMode,
  LearningPlanRhythmOption,
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

function fallbackTrainingDays(mode: LearningPlanRhythmMode): [number, number] {
  if (mode === 'RELAXED') {
    return [4, 4];
  }
  if (mode === 'SPRINT') {
    return [6, 7];
  }
  return [5, 5];
}

function fallbackDailyProblems(option: LearningPlanRhythmOption): [number, number] {
  const [minDays, maxDays] = fallbackTrainingDays(option.mode);
  const problemCount = option.loadSummary.plannedProblemCount;
  const min = problemCount <= 0
    ? 0
    : Math.max(1, Math.floor(problemCount / Math.max(1, option.durationWeeks * maxDays)));
  const max = problemCount <= 0
    ? 0
    : Math.max(min, Math.ceil(problemCount / Math.max(1, option.durationWeeks * minDays)));
  return [min, max];
}

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
  const [rhythmMode, setRhythmMode] = useState<LearningPlanRhythmMode>('RECOMMENDED');
  const [programmingLanguage, setProgrammingLanguage] = useState(DEFAULT_PROGRAMMING_LANGUAGE);
  const [listLoading, setListLoading] = useState(true);
  const [detailLoading, setDetailLoading] = useState(false);
  const [loadError, setLoadError] = useState('');
  const [validationError, setValidationError] = useState('');

  const selectedTemplate = useMemo(
    () => templates.find((template) => template.templateId === selectedTemplateId),
    [selectedTemplateId, templates],
  );
  const rhythmOptions = selectedTemplateDetail?.rhythmOptions ?? selectedTemplate?.rhythmOptions ?? [];
  const selectedRhythm = rhythmOptions.find((option) => option.mode === rhythmMode) ?? rhythmOptions[0];
  const selectedLoadSummary = selectedRhythm?.loadSummary ?? selectedTemplateDetail?.defaultLoadSummary
    ?? selectedTemplate?.defaultLoadSummary;
  const selectedIntensityLabel = selectedLoadSummary
    ? resources.learningPlans.loadIntensityLabels[selectedLoadSummary.intensity as keyof typeof resources.learningPlans.loadIntensityLabels]
      ?? String(selectedLoadSummary.intensity)
    : resources.learningPlans.unspecified;
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
          setRhythmMode('RECOMMENDED');
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
    setRhythmMode('RECOMMENDED');
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
    setValidationError('');
    onSubmit({
      templateId: selectedTemplate.templateId,
      rhythmMode,
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
                  {resources.learningPlans.templateRouteSummary(
                    template.defaultLoadSummary?.plannedProblemCount ?? template.matchedProblemCount,
                    template.defaultDurationWeeks,
                    template.defaultWeeklyHours,
                    resources.learningPlans.loadIntensityLabels[
                      template.defaultLoadSummary?.intensity as keyof typeof resources.learningPlans.loadIntensityLabels
                    ] ?? resources.learningPlans.unspecified,
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
              <strong>{resources.learningPlans.templateRhythm}</strong>
              <div className="template-rhythm-card-grid">
                {rhythmOptions.map((option) => {
                  const [fallbackMinDays, fallbackMaxDays] = fallbackTrainingDays(option.mode);
                  const minDays = option.trainingDaysPerWeekMin ?? fallbackMinDays;
                  const maxDays = option.trainingDaysPerWeekMax ?? fallbackMaxDays;
                  const [fallbackMinProblems, fallbackMaxProblems] = fallbackDailyProblems(option);
                  const minProblems = option.dailyProblemCountMin ?? fallbackMinProblems;
                  const maxProblems = option.dailyProblemCountMax ?? fallbackMaxProblems;
                  const intensity = resources.learningPlans.loadIntensityLabels[
                    option.loadSummary.intensity as keyof typeof resources.learningPlans.loadIntensityLabels
                  ] ?? String(option.loadSummary.intensity);
                  const hasReview = option.coveragePolicy !== 'FULL_ROUTE_FAST';
                  return (
                  <button
                    aria-label={resources.learningPlans.rhythmLabels[option.mode]}
                    aria-pressed={rhythmMode === option.mode}
                    className={`template-rhythm-card${rhythmMode === option.mode ? ' selected' : ''}`}
                    disabled={loading || detailLoading}
                    key={option.mode}
                    onClick={() => setRhythmMode(option.mode)}
                    type="button"
                  >
                    <span className="template-rhythm-card-title">
                      <strong>{resources.learningPlans.rhythmLabels[option.mode]}</strong>
                      <small>{resources.learningPlans.rhythmRiskLine(intensity)}</small>
                    </span>
                    <span>{resources.learningPlans.rhythmCompletionLine(
                      option.durationWeeks,
                      option.loadSummary.plannedProblemCount,
                    )}</span>
                    <span>{resources.learningPlans.rhythmWeeklyTimeLine(option.weeklyHours, minDays, maxDays)}</span>
                    <span>{resources.learningPlans.rhythmDailyLine(minProblems, maxProblems, hasReview)}</span>
                    <span>{resources.learningPlans.rhythmScopeLabels[option.coveragePolicy]}</span>
                  </button>
                  );
                })}
              </div>
              {selectedRhythm && selectedRhythm.mode === 'SPRINT' && selectedRhythm.loadSummary.intensity === 'OVERLOADED' && (
                <p className="load-risk-line overloaded">
                  {resources.learningPlans.sprintOverloadWarning(
                    selectedRhythm.dailyProblemCountMin ?? fallbackDailyProblems(selectedRhythm)[0],
                    selectedRhythm.dailyProblemCountMax ?? fallbackDailyProblems(selectedRhythm)[1],
                  )}
                </p>
              )}
              {selectedLoadSummary?.suggestions[0] && (
                <p className={`load-risk-line ${String(selectedLoadSummary.intensity).toLowerCase()}`}>
                  {resources.learningPlans.rhythmRiskLine(selectedIntensityLabel)}
                </p>
              )}
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
