import { useEffect, useId, useMemo, useState } from 'react';
import { unicodeCodePointLength, useUserInputLimits } from '../config/userInputLimits';
import type {
  LearningPlanCreateDraftRequest,
  LearningPlanIntent,
  LearningPlanLevel,
} from '../types/api';
import { formatPlanLevel, formatTopicTag } from '../i18n/formatters';
import { useI18n } from '../i18n/I18nProvider';
import DifficultyDistributionControl from './DifficultyDistributionControl';
import {
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
const PLAN_SIZE_OPTIONS = [5, 10, 15, 20, 25, 30] as const;
const DEFAULT_TARGET_PROBLEM_COUNT = 15;
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
  const inputLimits = useUserInputLimits();
  const [intent, setIntent] = useState<LearningPlanIntent>(DEFAULT_INTENT);
  const [targetProblemCount, setTargetProblemCount] = useState(DEFAULT_TARGET_PROBLEM_COUNT);
  const [level, setLevel] = useState<LearningPlanLevel>(DEFAULT_LEVEL);
  const [programmingLanguage, setProgrammingLanguage] = useState(DEFAULT_PROGRAMMING_LANGUAGE);
  const [difficultyValue, setDifficultyValue] = useState(DEFAULT_DIFFICULTY_VALUE);
  const [topicPreferences, setTopicPreferences] = useState<string[]>([]);
  const [objective, setObjective] = useState('');
  const [additionalConstraints, setAdditionalConstraints] = useState('');
  const [personalizationEnabled, setPersonalizationEnabled] = useState(true);
  const [validationError, setValidationError] = useState('');
  const objectiveCounterId = useId();
  const additionalConstraintsCounterId = useId();

  const objectiveLength = unicodeCodePointLength(objective);
  const additionalConstraintsLength = unicodeCodePointLength(additionalConstraints);
  const textLimitExceeded = objectiveLength > inputLimits.learningPlanCreate.objectiveMaxChars
    || additionalConstraintsLength > inputLimits.learningPlanCreate.additionalConstraintsMaxChars;
  const selectedDifficulty = getDifficultyDistribution(difficultyValue);
  const effectiveSubmitLabel = submitLabel ?? resources.learningPlans.generateDraft;

  const hasUnsavedInput = useMemo(
    () => intent !== DEFAULT_INTENT
      || targetProblemCount !== DEFAULT_TARGET_PROBLEM_COUNT
      || level !== DEFAULT_LEVEL
      || programmingLanguage !== DEFAULT_PROGRAMMING_LANGUAGE
      || difficultyValue !== DEFAULT_DIFFICULTY_VALUE
      || topicPreferences.length > 0
      || objective.trim().length > 0
      || additionalConstraints.trim().length > 0
      || !personalizationEnabled,
    [
      additionalConstraints,
      difficultyValue,
      intent,
      level,
      objective,
      personalizationEnabled,
      programmingLanguage,
      topicPreferences.length,
      targetProblemCount,
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
    if (textLimitExceeded) {
      setValidationError(resources.learningPlans.validationInputLimit);
      return;
    }
    if (intent === 'TOPIC_BREAKTHROUGH' && topicPreferences.length === 0) {
      setValidationError(resources.learningPlans.validationTopicRequired);
      return;
    }

    setValidationError('');
    onSubmit({
      intent,
      objective: objective || undefined,
      targetProblemCount,
      level,
      programmingLanguage,
      difficultyDistribution: {
        easyPercent: selectedDifficulty.easyPercent,
        mediumPercent: selectedDifficulty.mediumPercent,
        hardPercent: selectedDifficulty.hardPercent,
      },
      topicPreferences,
      additionalConstraints: additionalConstraints || undefined,
      personalizationEnabled,
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

        <section className="question-block">
          <strong>{resources.learningPlans.targetProblemCount}</strong>
          <div className="segmented-grid">
            {PLAN_SIZE_OPTIONS.map((option) => (
              <button
                aria-pressed={targetProblemCount === option}
                className={targetProblemCount === option ? 'selected' : ''}
                disabled={loading}
                key={option}
                onClick={() => setTargetProblemCount(option)}
                type="button"
              >
                {resources.learningPlans.problemCount(option)}
              </button>
            ))}
          </div>
          <p className="load-summary-line">{resources.learningPlans.targetProblemCountHint}</p>
        </section>

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

        <label className="topic-field">
          <span>{resources.learningPlans.objective}</span>
          <textarea
            aria-describedby={objectiveCounterId}
            aria-invalid={objectiveLength > inputLimits.learningPlanCreate.objectiveMaxChars}
            aria-label={resources.learningPlans.objective}
            disabled={loading}
            onChange={(event) => setObjective(event.target.value)}
            rows={3}
            value={objective}
          />
          <small
            className={objectiveLength > inputLimits.learningPlanCreate.objectiveMaxChars
              ? 'input-limit-counter is-over-limit'
              : 'input-limit-counter'}
            id={objectiveCounterId}
          >
            {resources.common.characterCount(
              objectiveLength,
              inputLimits.learningPlanCreate.objectiveMaxChars,
            )}
          </small>
        </label>

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
          <span>{resources.learningPlans.additionalConstraints}</span>
          <textarea
            aria-describedby={additionalConstraintsCounterId}
            aria-invalid={additionalConstraintsLength
              > inputLimits.learningPlanCreate.additionalConstraintsMaxChars}
            aria-label={resources.learningPlans.additionalConstraints}
            disabled={loading}
            onChange={(event) => setAdditionalConstraints(event.target.value)}
            rows={4}
            value={additionalConstraints}
          />
          <small
            className={additionalConstraintsLength
              > inputLimits.learningPlanCreate.additionalConstraintsMaxChars
              ? 'input-limit-counter is-over-limit'
              : 'input-limit-counter'}
            id={additionalConstraintsCounterId}
          >
            {resources.common.characterCount(
              additionalConstraintsLength,
              inputLimits.learningPlanCreate.additionalConstraintsMaxChars,
            )}
          </small>
        </label>

        <label className="checkbox-field">
          <input
            aria-label={resources.learningPlans.personalizationEnabled}
            checked={personalizationEnabled}
            disabled={loading}
            onChange={(event) => setPersonalizationEnabled(event.target.checked)}
            type="checkbox"
          />
          <span>{resources.learningPlans.personalizationEnabled}</span>
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
