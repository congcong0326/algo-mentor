import { ArrowLeft, ArrowRight, Plus, Sparkles, X } from 'lucide-react';
import { useMemo, useState } from 'react';
import type {
  LearningPlanCreateDraftRequest,
  LearningPlanDifficultyPreference,
  LearningPlanIntent,
  LearningPlanLevel,
} from '../types/api';
import { difficultyOptions, intentOptions, levelOptions } from './options';

interface LearningPlanWizardProps {
  loading: boolean;
  onCancel: () => void;
  onSubmit: (request: LearningPlanCreateDraftRequest) => void;
}

const steps = ['目标', '时间与水平', '主题偏好', '生成与确认'] as const;

function splitTags(value: string): string[] {
  const separator = /[,，]/.test(value) ? /[,，]+/ : /\s+/;

  return value
    .split(separator)
    .map((tag) => tag.trim())
    .filter(Boolean);
}

export default function LearningPlanWizard({ loading, onCancel, onSubmit }: LearningPlanWizardProps) {
  const [stepIndex, setStepIndex] = useState(0);
  const [goal, setGoal] = useState('');
  const [intent, setIntent] = useState<LearningPlanIntent>('INTERVIEW_SPRINT');
  const [durationWeeks, setDurationWeeks] = useState(4);
  const [level, setLevel] = useState<LearningPlanLevel>('INTERMEDIATE');
  const [weeklyHours, setWeeklyHours] = useState(6);
  const [programmingLanguage, setProgrammingLanguage] = useState('Java');
  const [difficultyPreference, setDifficultyPreference] = useState<LearningPlanDifficultyPreference>('MEDIUM');
  const [interviewOriented, setInterviewOriented] = useState(true);
  const [topicInput, setTopicInput] = useState('');
  const [topicPreferences, setTopicPreferences] = useState<string[]>(['Array', 'Hash Table']);

  const numericValid = Number.isInteger(durationWeeks) && durationWeeks > 0
    && Number.isInteger(weeklyHours) && weeklyHours > 0;
  const canGoNext = useMemo(() => {
    if (stepIndex === 0) {
      return goal.trim().length > 0;
    }
    if (stepIndex === 1) {
      return numericValid;
    }
    return true;
  }, [goal, numericValid, stepIndex]);

  function addTopics() {
    const nextTags = splitTags(topicInput);
    if (nextTags.length === 0) {
      return;
    }
    setTopicPreferences((current) => Array.from(new Set([...current, ...nextTags])));
    setTopicInput('');
  }

  function removeTopic(topic: string) {
    setTopicPreferences((current) => current.filter((candidate) => candidate !== topic));
  }

  function submit() {
    onSubmit({
      intent,
      goal: goal.trim(),
      durationWeeks,
      level,
      weeklyHours,
      programmingLanguage: programmingLanguage.trim() || undefined,
      difficultyPreference,
      interviewOriented,
      topicPreferences,
    });
  }

  return (
    <article className="learning-panel wizard-panel" aria-labelledby="wizard-title">
      <div className="wizard-heading">
        <div>
          <p className="eyebrow">新建计划</p>
          <h2 id="wizard-title">{steps[stepIndex]}</h2>
        </div>
        <button className="secondary-button compact" disabled={loading} onClick={onCancel} type="button">
          <X aria-hidden="true" />
          <span>取消</span>
        </button>
      </div>

      <ol className="wizard-steps" aria-label="创建步骤">
        {steps.map((step, index) => (
          <li className={index === stepIndex ? 'active' : index < stepIndex ? 'done' : ''} key={step}>
            <span>{index + 1}</span>
            <strong>{step}</strong>
          </li>
        ))}
      </ol>

      {stepIndex === 0 && (
        <section className="wizard-step">
          <label className="topic-field">
            <span>意图</span>
            <select
              aria-label="计划意图"
              disabled={loading}
              onChange={(event) => setIntent(event.target.value as LearningPlanIntent)}
              value={intent}
            >
              {intentOptions.map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}
            </select>
          </label>
          <label className="topic-field">
            <span>目标</span>
            <textarea
              aria-label="学习目标"
              disabled={loading}
              onChange={(event) => setGoal(event.target.value)}
              placeholder="例如：6 周内用 Java 准备后端算法面试，重点补数组、哈希表和动态规划。"
              rows={4}
              value={goal}
            />
          </label>
        </section>
      )}

      {stepIndex === 1 && (
        <section className="wizard-step">
          {!numericValid && <p className="error-text">周期和每周小时数必须是正整数。</p>}
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
              <span>每周小时</span>
              <input
                aria-label="每周小时"
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
              <span>水平</span>
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
              <span>难度</span>
              <select
                aria-label="偏好难度"
                disabled={loading}
                onChange={(event) => setDifficultyPreference(event.target.value as LearningPlanDifficultyPreference)}
                value={difficultyPreference}
              >
                {difficultyOptions.map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}
              </select>
            </label>
          </div>
          <label className="topic-field">
            <span>语言</span>
            <input
              aria-label="编程语言"
              disabled={loading}
              onChange={(event) => setProgrammingLanguage(event.target.value)}
              value={programmingLanguage}
            />
          </label>
          <label className="checkbox-row">
            <input
              checked={interviewOriented}
              disabled={loading}
              onChange={(event) => setInterviewOriented(event.target.checked)}
              type="checkbox"
            />
            <span>面试导向</span>
          </label>
        </section>
      )}

      {stepIndex === 2 && (
        <section className="wizard-step">
          <label className="topic-field">
            <span>主题</span>
            <input
              aria-label="添加主题"
              disabled={loading}
              onChange={(event) => setTopicInput(event.target.value)}
              onKeyDown={(event) => {
                if (event.key === 'Enter') {
                  event.preventDefault();
                  addTopics();
                }
              }}
              placeholder="输入 Array, Hash Table 后按回车"
              value={topicInput}
            />
          </label>
          <button className="secondary-button" disabled={loading || !topicInput.trim()} onClick={addTopics} type="button">
            <Plus aria-hidden="true" />
            <span>添加主题</span>
          </button>
          <div className="tag-row">
            {topicPreferences.map((topic) => (
              <button
                aria-label={`移除主题 ${topic}`}
                className="tag-pill removable"
                disabled={loading}
                key={topic}
                onClick={() => removeTopic(topic)}
                type="button"
              >
                <span>{topic}</span>
                <X aria-hidden="true" />
              </button>
            ))}
          </div>
        </section>
      )}

      {stepIndex === 3 && (
        <section className="wizard-step">
          <div className="wizard-review">
            <span>目标</span>
            <strong>{goal}</strong>
            <span>周期</span>
            <strong>{durationWeeks} 周 · {weeklyHours} 小时/周</strong>
            <span>主题</span>
            <strong>{topicPreferences.length === 0 ? '未指定' : topicPreferences.join(', ')}</strong>
          </div>
        </section>
      )}

      <div className="wizard-actions">
        <button
          className="secondary-button"
          disabled={loading || stepIndex === 0}
          onClick={() => setStepIndex((current) => current - 1)}
          type="button"
        >
          <ArrowLeft aria-hidden="true" />
          <span>上一步</span>
        </button>
        {stepIndex < steps.length - 1 ? (
          <button
            className="primary-button"
            disabled={loading || !canGoNext}
            onClick={() => setStepIndex((current) => current + 1)}
            type="button"
          >
            <span>下一步</span>
            <ArrowRight aria-hidden="true" />
          </button>
        ) : (
          <button className="primary-button" disabled={loading || !goal.trim() || !numericValid} onClick={submit} type="button">
            <Sparkles aria-hidden="true" />
            <span>生成草案</span>
          </button>
        )}
      </div>
    </article>
  );
}
