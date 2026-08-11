import { BookOpen, Info, Layers, Sparkles } from 'lucide-react';
import { useEffect, useMemo, useRef, useState } from 'react';
import {
  getLearningPlanTemplates,
  requireApiData,
} from '../services/api';
import type {
  LearningPlanTemplateCatalogCategory,
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
import LearningPlanTemplateDetailDialog from './LearningPlanTemplateDetailDialog';

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

type TemplateCatalogView = 'RECOMMENDED' | LearningPlanTemplateCatalogCategory;

export default function LearningPlanTemplateCreatePanel({
  loading,
  error,
  onCancel,
  onSubmit,
}: LearningPlanTemplateCreatePanelProps) {
  const { locale, resources } = useI18n();
  const [templates, setTemplates] = useState<LearningPlanTemplateSummaryResponse[]>([]);
  const [catalogView, setCatalogView] = useState<TemplateCatalogView>('RECOMMENDED');
  const [selectedTemplateId, setSelectedTemplateId] = useState('');
  const [dailyProblemCount, setDailyProblemCount] = useState(DEFAULT_DAILY_PROBLEM_COUNT);
  const [trainingDaysPerWeek, setTrainingDaysPerWeek] = useState(DEFAULT_TRAINING_DAYS_PER_WEEK);
  const [programmingLanguage, setProgrammingLanguage] = useState(DEFAULT_PROGRAMMING_LANGUAGE);
  const [listLoading, setListLoading] = useState(true);
  const [loadError, setLoadError] = useState('');
  const [validationError, setValidationError] = useState('');
  const [detailTemplateId, setDetailTemplateId] = useState('');
  const selectedTemplateIdRef = useRef('');

  const selectedTemplate = useMemo(
    () => templates.find((template) => template.templateId === selectedTemplateId),
    [selectedTemplateId, templates],
  );
  const visibleTemplates = useMemo(
    () => filterTemplates(templates, catalogView),
    [catalogView, templates],
  );
  const detailTemplate = templates.find((template) => template.templateId === detailTemplateId);
  const defaultRhythmSettings = selectedTemplate?.defaultRhythmSettings;
  const totalProblemCount = defaultRhythmSettings?.totalProblemCount
    ?? selectedTemplate?.plannedProblemCount
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
  const submitDisabled = loading || listLoading || !selectedTemplate;

  useEffect(() => {
    const controller = new AbortController();
    setListLoading(true);
    setLoadError('');

    void getLearningPlanTemplates(controller.signal)
      .then((response) => {
        const nextTemplates = requireApiData(response, resources.learningPlans.templateLoadFailed);
        setTemplates(nextTemplates);
        const preservedTemplate = nextTemplates.find(
          (template) => template.templateId === selectedTemplateIdRef.current,
        );
        const [firstTemplate] = filterTemplates(nextTemplates, 'RECOMMENDED');
        const nextSelectedTemplate = preservedTemplate ?? firstTemplate;
        const nextSelectedTemplateId = nextSelectedTemplate?.templateId ?? '';
        selectedTemplateIdRef.current = nextSelectedTemplateId;
        setSelectedTemplateId(nextSelectedTemplateId);
        if (!preservedTemplate && nextSelectedTemplate) {
          applyTemplateDefaults(nextSelectedTemplate);
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
  }, [locale, resources.learningPlans.templateLoadFailed]);

  function selectTemplate(template: LearningPlanTemplateSummaryResponse) {
    if (loading || listLoading) {
      return;
    }
    setValidationError('');
    selectedTemplateIdRef.current = template.templateId;
    setSelectedTemplateId(template.templateId);
    applyTemplateDefaults(template);
  }

  function selectCatalogView(view: TemplateCatalogView) {
    if (loading || listLoading) {
      return;
    }
    setValidationError('');
    setCatalogView(view);
    const nextTemplates = filterTemplates(templates, view);
    if (!nextTemplates.some((template) => template.templateId === selectedTemplateId)) {
      const [firstTemplate] = nextTemplates;
      const nextTemplateId = firstTemplate?.templateId ?? '';
      selectedTemplateIdRef.current = nextTemplateId;
      setSelectedTemplateId(nextTemplateId);
      if (firstTemplate) {
        applyTemplateDefaults(firstTemplate);
      }
    }
  }

  function submit() {
    if (!selectedTemplate) {
      setValidationError(resources.learningPlans.templateEmpty);
      return;
    }
    setValidationError('');
    onSubmit({
      templateId: selectedTemplate.templateId,
      contentLocale: selectedTemplate.contentLocale,
      dailyProblemCount,
      trainingDaysPerWeek,
      programmingLanguage: programmingLanguage.trim() || undefined,
    });
  }

  function applyDefaultRhythm(settings?: LearningPlanRhythmSettings) {
    setDailyProblemCount(settings?.dailyProblemCount ?? DEFAULT_DAILY_PROBLEM_COUNT);
    setTrainingDaysPerWeek(settings?.trainingDaysPerWeek ?? DEFAULT_TRAINING_DAYS_PER_WEEK);
  }

  function applyTemplateDefaults(template: LearningPlanTemplateSummaryResponse) {
    applyDefaultRhythm(template.defaultRhythmSettings);
    setProgrammingLanguage(template.programmingLanguage?.trim() || DEFAULT_PROGRAMMING_LANGUAGE);
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
          <section aria-label={resources.learningPlans.createFromTemplate}>
            <div aria-label={resources.learningPlans.templateCatalog} className="template-catalog-tabs" role="tablist">
              {[
                ['RECOMMENDED', resources.learningPlans.templateCatalogRecommended],
                ['SYSTEMATIC_LEARNING', resources.learningPlans.templateCatalogSystematicLearning],
                ['INTERVIEW_PREP', resources.learningPlans.templateCatalogInterviewPrep],
                ['TOPIC_BREAKTHROUGH', resources.learningPlans.templateCatalogTopicBreakthrough],
                ['LANGUAGE_AND_ROLE', resources.learningPlans.templateCatalogLanguageAndRole],
              ].map(([view, label]) => (
                <button
                  aria-controls={`template-catalog-panel-${view}`}
                  aria-selected={catalogView === view}
                  className={catalogView === view ? 'selected' : ''}
                  id={`template-catalog-tab-${view}`}
                  key={view}
                  onClick={() => selectCatalogView(view as TemplateCatalogView)}
                  role="tab"
                  type="button"
                >
                  {label}
                </button>
              ))}
            </div>
            <div
              aria-labelledby={`template-catalog-tab-${catalogView}`}
              id={`template-catalog-panel-${catalogView}`}
              role="tabpanel"
            >
              {visibleTemplates.length === 0 && (
                <p className="empty-log">{resources.learningPlans.templateCatalogEmpty}</p>
              )}
              <div className="template-card-grid">
                {visibleTemplates.map((template) => (
                  <article
                    className={`template-card${selectedTemplateId === template.templateId ? ' selected' : ''}${loading || listLoading ? ' disabled' : ''}`}
                    key={template.templateId}
                  >
                    <button
                      aria-describedby={`template-preview-${template.templateId}`}
                      aria-label={[
                        template.title,
                        template.summary,
                        formatPlanLevel(template.level, resources),
                        resources.learningPlans.templateRouteSummary(
                          template.plannedProblemCount,
                          template.defaultDurationWeeks,
                          template.defaultWeeklyHours,
                        ),
                      ].join(', ')}
                      aria-pressed={selectedTemplateId === template.templateId}
                      className="template-card-select"
                      disabled={loading || listLoading}
                      onClick={() => selectTemplate(template)}
                      type="button"
                    >
                      <span className="template-card-title">
                        <span className="template-card-icon" aria-hidden="true">
                          <Layers />
                        </span>
                        <strong>{template.title}</strong>
                        {template.recommendedOrder != null && (
                          <span className="template-recommended-badge">{resources.learningPlans.templateRecommendedBadge}</span>
                        )}
                        <span className="template-card-preview-icon" aria-hidden="true">
                          <Info />
                        </span>
                      </span>
                      <span>{template.summary}</span>
                      <span className="template-card-meta">{formatPlanLevel(template.level, resources)}</span>
                      <span className="template-card-meta">
                        {resources.learningPlans.templateRouteSummary(
                          template.plannedProblemCount,
                          template.defaultDurationWeeks,
                          template.defaultWeeklyHours,
                        )}
                      </span>
                      <span
                        className="template-card-preview"
                        id={`template-preview-${template.templateId}`}
                        role="tooltip"
                      >
                        <span>
                          <small>{resources.learningPlans.templateTargetAudience}</small>
                          <strong>{template.targetAudience}</strong>
                        </span>
                        <span>
                          <small>{resources.learningPlans.templateExpectedOutcome}</small>
                          <strong>{template.expectedOutcome}</strong>
                        </span>
                      </span>
                    </button>
                    <button
                      aria-label={resources.learningPlans.templateViewContentFor(template.title)}
                      className="template-card-view-button"
                      disabled={loading || listLoading}
                      onClick={() => {
                        selectTemplate(template);
                        setDetailTemplateId(template.templateId);
                      }}
                      type="button"
                    >
                      <BookOpen aria-hidden="true" />
                      <span>{resources.learningPlans.templateViewContent}</span>
                    </button>
                  </article>
                ))}
              </div>
            </div>
          </section>
        )}

        {selectedTemplate && (
          <>
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
                    disabled={loading}
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
                    disabled={loading}
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
                disabled={loading}
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

      {detailTemplate && (
        <LearningPlanTemplateDetailDialog
          onClose={() => setDetailTemplateId('')}
          template={detailTemplate}
        />
      )}
    </>
  );
}

function filterTemplates(
  templates: LearningPlanTemplateSummaryResponse[],
  view: TemplateCatalogView,
): LearningPlanTemplateSummaryResponse[] {
  if (view !== 'RECOMMENDED') {
    return templates.filter((template) => template.catalogCategory === view);
  }
  return templates
    .filter((template) => template.recommendedOrder != null)
    .sort((left, right) => (left.recommendedOrder ?? Number.MAX_SAFE_INTEGER)
      - (right.recommendedOrder ?? Number.MAX_SAFE_INTEGER));
}

function clampNumber(value: number, min: number, max: number) {
  if (!Number.isFinite(value)) {
    return min;
  }
  return Math.max(min, Math.min(max, Math.trunc(value)));
}
