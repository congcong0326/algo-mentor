import { useEffect, useMemo, useState } from 'react';
import type {
  LearningPlanCreateDraftRequest,
  LearningPlanDifficultyPreference,
  LearningPlanIntent,
  LearningPlanLevel,
} from '../types/api';
import { formatPlanLevel, formatTopicTag } from '../i18n/formatters';
import { useI18n } from '../i18n/I18nProvider';
import DifficultyDistributionControl from './DifficultyDistributionControl';
import {
  buildLearningPlanGoal,
  getDifficultyDistribution,
  planScenarioOptions,
  programmingLanguageOptions,
  topicOptions,
} from './options';

interface LearningPlanCreateFormProps {
  loading: boolean;
  error?: string;
  submitLabel?: string;
  confirmOnCancel?: boolean;
  onDirtyChange?: (dirty: boolean) => void;
  onCancel?: () => void;
  onSubmit: (request: LearningPlanCreateDraftRequest) => void;
}

const DEFAULT_INTENT: LearningPlanIntent = 'INTERVIEW_SPRINT';
const DEFAULT_DURATION_WEEKS = 4;
const DEFAULT_WEEKLY_HOURS = 6;
const DEFAULT_LEVEL: LearningPlanLevel = 'INTERMEDIATE';
const DEFAULT_PROGRAMMING_LANGUAGE = 'Java';
const DEFAULT_DIFFICULTY_VALUE = 50;

export default function LearningPlanCreateForm({
  loading,
  error,
  submitLabel,
  confirmOnCancel = true,
  onDirtyChange,
  onCancel,
  onSubmit,
}: LearningPlanCreateFormProps) {
  const { resources } = useI18n();
  const [intent, setIntent] = useState<LearningPlanIntent>(DEFAULT_INTENT);
  const [durationWeeks, setDurationWeeks] = useState(DEFAULT_DURATION_WEEKS);
  const [weeklyHours, setWeeklyHours] = useState(DEFAULT_WEEKLY_HOURS);
  const [level, setLevel] = useState<LearningPlanLevel>(DEFAULT_LEVEL);
  const [programmingLanguage, setProgrammingLanguage] = useState(DEFAULT_PROGRAMMING_LANGUAGE);
  const [difficultyValue, setDifficultyValue] = useState(DEFAULT_DIFFICULTY_VALUE);
  const [topicPreferences, setTopicPreferences] = useState<string[]>([]);
  const [additionalThoughts, setAdditionalThoughts] = useState('');
  const [validationError, setValidationError] = useState('');

  const numericValid = Number.isInteger(durationWeeks) && durationWeeks > 0
    && Number.isInteger(weeklyHours) && weeklyHours > 0;
  const selectedScenario = planScenarioOptions.find((option) => option.value === intent) ?? planScenarioOptions[0];
  const selectedLevelLabel = formatPlanLevel(level, resources);
  const selectedDifficulty = getDifficultyDistribution(difficultyValue);
  const difficultyPreference: LearningPlanDifficultyPreference = selectedDifficulty.preference;
  const effectiveSubmitLabel = submitLabel ?? resources.learningPlans.generateDraft;

  const hasUnsavedInput = useMemo(
    () => intent !== DEFAULT_INTENT
      || durationWeeks !== DEFAULT_DURATION_WEEKS
      || weeklyHours !== DEFAULT_WEEKLY_HOURS
      || level !== DEFAULT_LEVEL
      || programmingLanguage !== DEFAULT_PROGRAMMING_LANGUAGE
      || difficultyValue !== DEFAULT_DIFFICULTY_VALUE
      || topicPreferences.length > 0
      || additionalThoughts.trim().length > 0,
    [
      additionalThoughts,
      difficultyValue,
      durationWeeks,
      intent,
      level,
      programmingLanguage,
      topicPreferences.length,
      weeklyHours,
    ],
  );

  useEffect(() => {
    onDirtyChange?.(hasUnsavedInput);
  }, [hasUnsavedInput, onDirtyChange]);

  function confirmCancel() {
    if (loading || !onCancel) {
      return;
    }
    if (confirmOnCancel && hasUnsavedInput && !window.confirm(resources.learningPlans.confirmDiscard)) {
      return;
    }
    onCancel();
  }

  function toggleTopic(value: string) {
    setTopicPreferences((current) => (
      current.includes(value) ? current.filter((topic) => topic !== value) : [...current, value]
    ));
  }

  function submit() {
    if (!numericValid) {
      setValidationError(resources.learningPlans.validationPositiveIntegers);
      return;
    }
    if (intent === 'TOPIC_BREAKTHROUGH' && topicPreferences.length === 0) {
      setValidationError(resources.learningPlans.validationTopicRequired);
      return;
    }

    setValidationError('');
    onSubmit({
      intent,
      goal: buildLearningPlanGoal({
        resources,
        intentLabel: resources.labels.planScenarios[selectedScenario.labelKey],
        durationWeeks,
        weeklyHours,
        levelLabel: selectedLevelLabel,
        programmingLanguage,
        difficultyLabel: resources.labels.difficultyDistribution[selectedDifficulty.labelKey],
        easyPercent: selectedDifficulty.easyPercent,
        mediumPercent: selectedDifficulty.mediumPercent,
        hardPercent: selectedDifficulty.hardPercent,
        topics: topicPreferences,
        additionalThoughts,
      }),
      durationWeeks,
      level,
      weeklyHours,
      programmingLanguage,
      difficultyPreference,
      interviewOriented: selectedScenario.interviewOriented,
      topicPreferences,
    });
  }

  return (
    <>
      {(error || validationError) && <p className="error-text" role="alert">{validationError || error}</p>}

      <div className="modal-form">
        <section className="question-block">
          <strong>{resources.learningPlans.scenario}</strong>
          <div className="segmented-grid">
            {planScenarioOptions.map((option) => (
              <button
                aria-pressed={intent === option.value}
                className={intent === option.value ? 'selected' : ''}
                disabled={loading}
                key={option.value}
                onClick={() => setIntent(option.value)}
                type="button"
              >
                {resources.labels.planScenarios[option.labelKey]}
              </button>
            ))}
          </div>
        </section>

        <div className="mini-grid">
          <label className="topic-field">
            <span>{resources.learningPlans.duration}</span>
            <input
              aria-label={resources.learningPlans.durationInput}
              disabled={loading}
              min={1}
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

        <div className="mini-grid">
          <label className="topic-field">
            <span>{resources.learningPlans.level}</span>
            <select
              aria-label={resources.learningPlans.level}
              disabled={loading}
              onChange={(event) => setLevel(event.target.value as LearningPlanLevel)}
              value={level}
            >
              {(['BEGINNER', 'INTERMEDIATE', 'ADVANCED'] as LearningPlanLevel[]).map((option) => (
                <option key={option} value={option}>{formatPlanLevel(option, resources)}</option>
              ))}
            </select>
          </label>
          <label className="topic-field">
            <span>{resources.learningPlans.programmingLanguage}</span>
            <select
              aria-label={resources.learningPlans.programmingLanguage}
              disabled={loading}
              onChange={(event) => setProgrammingLanguage(event.target.value)}
              value={programmingLanguage}
            >
              {programmingLanguageOptions.map((option) => <option key={option} value={option}>{option}</option>)}
            </select>
          </label>
        </div>

        <DifficultyDistributionControl disabled={loading} onChange={setDifficultyValue} value={difficultyValue} />

        <section className="question-block">
          <strong>{resources.learningPlans.topicPreferences}</strong>
          <div className="topic-option-grid">
            {topicOptions.map((option) => (
              <button
                aria-pressed={topicPreferences.includes(option.value)}
                className={topicPreferences.includes(option.value) ? 'selected' : ''}
                disabled={loading}
                key={option.value}
                onClick={() => toggleTopic(option.value)}
                type="button"
              >
                {formatTopicTag(option.value, resources)}
              </button>
            ))}
          </div>
        </section>

        <label className="topic-field">
          <span>{resources.learningPlans.additionalThoughts}</span>
          <textarea
            aria-label={resources.learningPlans.additionalThoughts}
            disabled={loading}
            onChange={(event) => setAdditionalThoughts(event.target.value)}
            rows={4}
            value={additionalThoughts}
          />
        </label>
      </div>

      <div className="modal-actions">
        {onCancel && (
          <button className="secondary-button" disabled={loading} onClick={confirmCancel} type="button">
            {resources.common.cancel}
          </button>
        )}
        <button className="primary-button" disabled={loading} onClick={submit} type="button">
          {loading ? resources.learningPlans.generating : effectiveSubmitLabel}
        </button>
      </div>
    </>
  );
}
