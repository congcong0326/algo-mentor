import { Check, FileText, MessageSquare, Plus, Send } from 'lucide-react';
import { useEffect, useState } from 'react';
import LearningPlanDetail from './learning-plans/LearningPlanDetail';
import PlanPreview from './learning-plans/PlanPreview';
import {
  confirmLearningPlanDraft,
  createLearningPlanDraft,
  getLearningPlanDetail,
  getLearningPlans,
  sendLearningPlanDraftMessage,
} from './services/api';
import type {
  LearningPlanCreateDraftRequest,
  LearningPlanDetailResponse,
  LearningPlanDifficultyPreference,
  LearningPlanDraftResponse,
  LearningPlanIntent,
  LearningPlanLevel,
  LearningPlanSummaryResponse,
} from './types/api';

const intentOptions: Array<{ label: string; value: LearningPlanIntent }> = [
  { label: '面试冲刺', value: 'INTERVIEW_SPRINT' },
  { label: '刷题目标', value: 'PRACTICE_GOAL' },
  { label: '专题突破', value: 'TOPIC_BREAKTHROUGH' },
  { label: '长期学习', value: 'LONG_TERM_LEARNING' },
  { label: '能力诊断', value: 'ABILITY_DIAGNOSIS' },
  { label: '错题复盘', value: 'MISTAKE_REVIEW' },
];

const levelOptions: Array<{ label: string; value: LearningPlanLevel }> = [
  { label: '入门', value: 'BEGINNER' },
  { label: '中级', value: 'INTERMEDIATE' },
  { label: '高级', value: 'ADVANCED' },
];

const difficultyOptions: Array<{ label: string; value: LearningPlanDifficultyPreference }> = [
  { label: 'Easy', value: 'EASY' },
  { label: 'Medium', value: 'MEDIUM' },
  { label: 'Hard', value: 'HARD' },
  { label: 'Mixed', value: 'MIXED' },
];

function splitTags(value: string): string[] {
  return value
    .split(/[,，\s]+/)
    .map((tag) => tag.trim())
    .filter(Boolean);
}

function apiData<T>(response: { success: boolean; data?: T; error?: { message: string } }, fallback: string): T {
  if (!response.success || response.data === undefined) {
    throw new Error(response.error?.message ?? fallback);
  }
  return response.data;
}

export default function LearningPlans() {
  const [plans, setPlans] = useState<LearningPlanSummaryResponse[]>([]);
  const [selectedPlan, setSelectedPlan] = useState<LearningPlanDetailResponse>();
  const [draft, setDraft] = useState<LearningPlanDraftResponse>();
  const [goal, setGoal] = useState('');
  const [intent, setIntent] = useState<LearningPlanIntent>('INTERVIEW_SPRINT');
  const [durationWeeks, setDurationWeeks] = useState(4);
  const [level, setLevel] = useState<LearningPlanLevel>('INTERMEDIATE');
  const [weeklyHours, setWeeklyHours] = useState(6);
  const [programmingLanguage, setProgrammingLanguage] = useState('Java');
  const [difficultyPreference, setDifficultyPreference] = useState<LearningPlanDifficultyPreference>('MEDIUM');
  const [interviewOriented, setInterviewOriented] = useState(true);
  const [topicPreferences, setTopicPreferences] = useState('Array, Hash Table');
  const [followUp, setFollowUp] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    const controller = new AbortController();
    getLearningPlans(controller.signal)
      .then((response) => {
        const nextPlans = apiData(response, '学习计划列表加载失败');
        setPlans(nextPlans);
        if (nextPlans[0]) {
          return loadPlan(nextPlans[0].id, controller.signal);
        }
        return undefined;
      })
      .catch((nextError) => {
        if (!controller.signal.aborted) {
          setError(nextError instanceof Error ? nextError.message : '学习计划列表加载失败');
        }
      });

    return () => controller.abort();
  }, []);

  async function loadPlan(planId: number, signal?: AbortSignal) {
    const response = await getLearningPlanDetail(planId, signal);
    setSelectedPlan(apiData(response, '学习计划详情加载失败'));
  }

  async function submitDraft() {
    setLoading(true);
    setError('');
    try {
      const request: LearningPlanCreateDraftRequest = {
        intent,
        goal,
        durationWeeks,
        level,
        weeklyHours,
        programmingLanguage,
        difficultyPreference,
        interviewOriented,
        topicPreferences: splitTags(topicPreferences),
      };
      const nextDraft = apiData(await createLearningPlanDraft(request), '学习计划草案创建失败');
      setDraft(nextDraft);
      setSelectedPlan(undefined);
    } catch (nextError) {
      setError(nextError instanceof Error ? nextError.message : '学习计划草案创建失败');
    } finally {
      setLoading(false);
    }
  }

  async function sendFollowUp() {
    if (!draft || !followUp.trim()) {
      return;
    }
    setLoading(true);
    setError('');
    try {
      const nextDraft = apiData(
        await sendLearningPlanDraftMessage(draft.draftId, { message: followUp.trim() }),
        '学习计划追问提交失败',
      );
      setDraft(nextDraft);
      setFollowUp('');
    } catch (nextError) {
      setError(nextError instanceof Error ? nextError.message : '学习计划追问提交失败');
    } finally {
      setLoading(false);
    }
  }

  async function confirmDraft() {
    if (!draft) {
      return;
    }
    setLoading(true);
    setError('');
    try {
      const confirmed = apiData(await confirmLearningPlanDraft(draft.draftId), '学习计划确认失败');
      await refreshPlans(confirmed.planId);
      setDraft(undefined);
    } catch (nextError) {
      setError(nextError instanceof Error ? nextError.message : '学习计划确认失败');
    } finally {
      setLoading(false);
    }
  }

  async function refreshPlans(selectedId?: number) {
    const nextPlans = apiData(await getLearningPlans(), '学习计划列表加载失败');
    setPlans(nextPlans);
    if (selectedId) {
      await loadPlan(selectedId);
    }
  }

  const currentDraftPlan = draft?.draftPlan ?? undefined;

  return (
    <section className="learning-shell" aria-label="学习计划">
      <div className="learning-layout">
        <aside className="learning-sidebar">
          <div className="panel-title compact-title">
            <h2>正式计划</h2>
            <span>{plans.length} 个</span>
          </div>
          <div className="learning-list">
            {plans.length === 0 ? (
              <p className="empty-log">暂无正式计划</p>
            ) : plans.map((plan) => (
              <button className="plan-row" key={plan.id} onClick={() => void loadPlan(plan.id)} type="button">
                <FileText aria-hidden="true" />
                <span>
                  <strong>{plan.title}</strong>
                  <small>{plan.durationWeeks} 周 · {plan.weeklyHours} 小时/周</small>
                </span>
              </button>
            ))}
          </div>

          <div className="learning-form">
            <div className="panel-title compact-title">
              <h2>新建计划</h2>
              <Plus aria-hidden="true" />
            </div>
            <label className="topic-field">
              <span>意图</span>
              <select aria-label="计划意图" onChange={(event) => setIntent(event.target.value as LearningPlanIntent)} value={intent}>
                {intentOptions.map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}
              </select>
            </label>
            <label className="topic-field">
              <span>目标</span>
              <textarea
                aria-label="学习目标"
                onChange={(event) => setGoal(event.target.value)}
                rows={3}
                value={goal}
              />
            </label>
            <div className="mini-grid">
              <label className="topic-field">
                <span>周期</span>
                <input
                  aria-label="计划周期"
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
                <select aria-label="当前水平" onChange={(event) => setLevel(event.target.value as LearningPlanLevel)} value={level}>
                  {levelOptions.map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}
                </select>
              </label>
              <label className="topic-field">
                <span>难度</span>
                <select
                  aria-label="偏好难度"
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
                onChange={(event) => setProgrammingLanguage(event.target.value)}
                value={programmingLanguage}
              />
            </label>
            <label className="topic-field">
              <span>主题</span>
              <input
                aria-label="目标主题"
                onChange={(event) => setTopicPreferences(event.target.value)}
                value={topicPreferences}
              />
            </label>
            <label className="checkbox-row">
              <input
                checked={interviewOriented}
                onChange={(event) => setInterviewOriented(event.target.checked)}
                type="checkbox"
              />
              <span>面试导向</span>
            </label>
            <button className="primary-button full-width" disabled={loading || !goal.trim()} onClick={submitDraft} type="button">
              <MessageSquare aria-hidden="true" />
              <span>生成草案</span>
            </button>
          </div>
        </aside>

        <div className="learning-main">
          {error && <p className="error-text">{error}</p>}
          {draft?.status === 'COLLECTING' && (
            <article className="learning-panel">
              <div className="panel-title">
                <MessageSquare aria-hidden="true" />
                <h2>Agent 追问</h2>
              </div>
              <p>{draft.assistantMessage}</p>
              <label className="topic-field">
                <span>回答</span>
                <textarea
                  aria-label="补充回答"
                  onChange={(event) => setFollowUp(event.target.value)}
                  rows={3}
                  value={followUp}
                />
              </label>
              <button className="primary-button" disabled={loading || !followUp.trim()} onClick={sendFollowUp} type="button">
                <Send aria-hidden="true" />
                <span>发送补充</span>
              </button>
            </article>
          )}

          {currentDraftPlan && (
            <article className="learning-panel">
              <div className="panel-title">
                <FileText aria-hidden="true" />
                <h2>草案预览</h2>
              </div>
              <PlanPreview plan={currentDraftPlan} />
              <button className="primary-button" disabled={loading} onClick={confirmDraft} type="button">
                <Check aria-hidden="true" />
                <span>确认保存</span>
              </button>
            </article>
          )}

          {!draft && selectedPlan && <LearningPlanDetail plan={selectedPlan} />}
        </div>
      </div>
    </section>
  );
}
