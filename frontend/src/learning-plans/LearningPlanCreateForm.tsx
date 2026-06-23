import { useEffect, useMemo, useState } from 'react';
import type {
  DifficultyDistributionLevel,
  LearningPlanCreateDraftRequest,
  LearningPlanDifficultyPreference,
  LearningPlanIntent,
  LearningPlanLevel,
} from '../types/api';
import DifficultyDistributionControl from './DifficultyDistributionControl';
import {
  buildLearningPlanGoal,
  difficultyDistributionOptions,
  levelOptions,
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
const DEFAULT_DIFFICULTY_LEVEL: DifficultyDistributionLevel = 'BALANCED';

export default function LearningPlanCreateForm({
  loading,
  error,
  submitLabel = '生成计划草案',
  confirmOnCancel = true,
  onDirtyChange,
  onCancel,
  onSubmit,
}: LearningPlanCreateFormProps) {
  const [intent, setIntent] = useState<LearningPlanIntent>(DEFAULT_INTENT);
  const [durationWeeks, setDurationWeeks] = useState(DEFAULT_DURATION_WEEKS);
  const [weeklyHours, setWeeklyHours] = useState(DEFAULT_WEEKLY_HOURS);
  const [level, setLevel] = useState<LearningPlanLevel>(DEFAULT_LEVEL);
  const [programmingLanguage, setProgrammingLanguage] = useState(DEFAULT_PROGRAMMING_LANGUAGE);
  const [difficultyLevel, setDifficultyLevel] = useState<DifficultyDistributionLevel>(DEFAULT_DIFFICULTY_LEVEL);
  const [topicPreferences, setTopicPreferences] = useState<string[]>([]);
  const [additionalThoughts, setAdditionalThoughts] = useState('');
  const [validationError, setValidationError] = useState('');

  const numericValid = Number.isInteger(durationWeeks) && durationWeeks > 0
    && Number.isInteger(weeklyHours) && weeklyHours > 0;
  const selectedScenario = planScenarioOptions.find((option) => option.value === intent) ?? planScenarioOptions[0];
  const selectedLevel = levelOptions.find((option) => option.value === level) ?? levelOptions[1];
  const selectedDifficulty = difficultyDistributionOptions.find((option) => option.value === difficultyLevel)
    ?? difficultyDistributionOptions[1];
  const difficultyPreference: LearningPlanDifficultyPreference = selectedDifficulty.preference;

  const hasUnsavedInput = useMemo(
    () => intent !== DEFAULT_INTENT
      || durationWeeks !== DEFAULT_DURATION_WEEKS
      || weeklyHours !== DEFAULT_WEEKLY_HOURS
      || level !== DEFAULT_LEVEL
      || programmingLanguage !== DEFAULT_PROGRAMMING_LANGUAGE
      || difficultyLevel !== DEFAULT_DIFFICULTY_LEVEL
      || topicPreferences.length > 0
      || additionalThoughts.trim().length > 0,
    [
      additionalThoughts,
      difficultyLevel,
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
    if (confirmOnCancel && hasUnsavedInput && !window.confirm('放弃当前填写的计划问卷？')) {
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
      setValidationError('周期和每周投入必须是正整数。');
      return;
    }
    if (intent === 'TOPIC_BREAKTHROUGH' && topicPreferences.length === 0) {
      setValidationError('专项突破需要至少选择一个主题。');
      return;
    }

    setValidationError('');
    onSubmit({
      intent,
      goal: buildLearningPlanGoal({
        intentLabel: selectedScenario.label,
        durationWeeks,
        weeklyHours,
        levelLabel: selectedLevel.label,
        programmingLanguage,
        difficultyLabel: selectedDifficulty.label,
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
          <strong>计划场景</strong>
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
                {option.label}
              </button>
            ))}
          </div>
        </section>

        <div className="mini-grid">
          <label className="topic-field">
            <span>周期</span>
            <input
              aria-label="计划周期"
              disabled={loading}
              min={1}
              onChange={(event) => setDurationWeeks(Number(event.target.value))}
              type="number"
              value={durationWeeks}
            />
          </label>
          <label className="topic-field">
            <span>每周投入</span>
            <input
              aria-label="每周投入"
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
            <span>当前水平</span>
            <select
              aria-label="当前水平"
              disabled={loading}
              onChange={(event) => setLevel(event.target.value as LearningPlanLevel)}
              value={level}
            >
              {levelOptions.map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}
            </select>
          </label>
          <label className="topic-field">
            <span>编程语言</span>
            <select
              aria-label="编程语言"
              disabled={loading}
              onChange={(event) => setProgrammingLanguage(event.target.value)}
              value={programmingLanguage}
            >
              {programmingLanguageOptions.map((option) => <option key={option} value={option}>{option}</option>)}
            </select>
          </label>
        </div>

        <DifficultyDistributionControl disabled={loading} onChange={setDifficultyLevel} value={difficultyLevel} />

        <section className="question-block">
          <strong>主题偏好</strong>
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
                {option.label}
              </button>
            ))}
          </div>
        </section>

        <label className="topic-field">
          <span>补充想法</span>
          <textarea
            aria-label="补充想法"
            disabled={loading}
            onChange={(event) => setAdditionalThoughts(event.target.value)}
            rows={4}
            value={additionalThoughts}
          />
        </label>
      </div>

      <div className="modal-actions">
        {onCancel && (
          <button className="secondary-button" disabled={loading} onClick={confirmCancel} type="button">取消</button>
        )}
        <button className="primary-button" disabled={loading} onClick={submit} type="button">
          {loading ? '生成中' : submitLabel}
        </button>
      </div>
    </>
  );
}
