import { FileText, Plus } from 'lucide-react';
import { useEffect, useState } from 'react';
import LearningPlanDetail from './learning-plans/LearningPlanDetail';
import LearningPlanDraftPanel from './learning-plans/LearningPlanDraftPanel';
import LearningPlanWizard from './learning-plans/LearningPlanWizard';
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
  LearningPlanDraftResponse,
  LearningPlanSummaryResponse,
} from './types/api';

type LearningPlanFlowState = 'idle' | 'creating' | 'generating' | 'collecting' | 'previewing' | 'confirming';

function apiData<T>(response: { success: boolean; data?: T; error?: { message: string } }, fallback: string): T {
  if (!response.success || response.data === undefined) {
    throw new Error(response.error?.message ?? fallback);
  }
  return response.data;
}

export default function LearningPlans() {
  const [plans, setPlans] = useState<LearningPlanSummaryResponse[]>([]);
  const [selectedPlan, setSelectedPlan] = useState<LearningPlanDetailResponse>();
  const [restorePlanId, setRestorePlanId] = useState<number>();
  const [draft, setDraft] = useState<LearningPlanDraftResponse>();
  const [flowState, setFlowState] = useState<LearningPlanFlowState>('idle');
  const [wizardResetStepSignal, setWizardResetStepSignal] = useState(0);
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

  async function submitDraft(request: LearningPlanCreateDraftRequest) {
    setFlowState('generating');
    setError('');
    try {
      const nextDraft = apiData(await createLearningPlanDraft(request), '学习计划草案创建失败');
      setDraft(nextDraft);
      setSelectedPlan(undefined);
      setRestorePlanId(undefined);
      setFlowState(nextDraft.status === 'COLLECTING' ? 'collecting' : 'previewing');
    } catch (nextError) {
      setError(nextError instanceof Error ? nextError.message : '学习计划草案创建失败');
      setFlowState('creating');
    }
  }

  async function sendFollowUp(message: string) {
    if (!draft || !message.trim()) {
      return false;
    }
    setFlowState('generating');
    setError('');
    try {
      const nextDraft = apiData(
        await sendLearningPlanDraftMessage(draft.draftId, { message: message.trim() }),
        '学习计划追问提交失败',
      );
      setDraft(nextDraft);
      setFlowState(nextDraft.status === 'COLLECTING' ? 'collecting' : 'previewing');
      return true;
    } catch (nextError) {
      setError(nextError instanceof Error ? nextError.message : '学习计划追问提交失败');
      setFlowState('collecting');
      return false;
    }
  }

  async function confirmDraft() {
    if (!draft) {
      return;
    }
    setFlowState('confirming');
    setError('');
    try {
      const confirmed = apiData(await confirmLearningPlanDraft(draft.draftId), '学习计划确认失败');
      setDraft(undefined);
      setRestorePlanId(undefined);
      setFlowState('idle');
      try {
        await refreshPlans(confirmed.planId);
      } catch (refreshError) {
        setError(refreshError instanceof Error ? refreshError.message : '学习计划列表加载失败');
      }
    } catch (nextError) {
      setError(nextError instanceof Error ? nextError.message : '学习计划确认失败');
      setFlowState('previewing');
    }
  }

  async function refreshPlans(selectedId?: number) {
    const nextPlans = apiData(await getLearningPlans(), '学习计划列表加载失败');
    setPlans(nextPlans);
    if (selectedId) {
      await loadPlan(selectedId);
    }
  }

  function startCreating() {
    setRestorePlanId(selectedPlan?.id);
    setDraft(undefined);
    setSelectedPlan(undefined);
    setError('');
    setFlowState('creating');
  }

  function cancelCreating() {
    const planIdToRestore = restorePlanId ?? plans[0]?.id;
    setDraft(undefined);
    setRestorePlanId(undefined);
    setError('');
    setFlowState('idle');
    if (planIdToRestore) {
      void loadPlan(planIdToRestore);
    }
  }

  function returnToWizard() {
    setDraft(undefined);
    setError('');
    setFlowState('creating');
    setWizardResetStepSignal((current) => current + 1);
  }

  const shouldKeepWizardMounted = flowState === 'creating' || flowState === 'generating' || draft !== undefined;

  return (
    <section className="learning-shell" aria-label="学习计划">
      <div className="learning-page-heading">
        <button className="primary-button" onClick={startCreating} type="button">
          <Plus aria-hidden="true" />
          <span>新建计划</span>
        </button>
      </div>

      {error && <p className="error-text">{error}</p>}

      <div className="learning-layout redesigned">
        <aside className="learning-sidebar">
          <div className="panel-title compact-title">
            <h2>正式计划</h2>
            <span>{plans.length} 个</span>
          </div>
          <div className="learning-list">
            {plans.length === 0 ? (
              <p className="empty-log">暂无正式计划，先新建一个学习计划。</p>
            ) : plans.map((plan) => (
              <button
                className={`plan-row ${selectedPlan?.id === plan.id ? 'selected' : ''}`}
                key={plan.id}
                onClick={() => {
                  setDraft(undefined);
                  setRestorePlanId(undefined);
                  setFlowState('idle');
                  void loadPlan(plan.id);
                }}
                type="button"
              >
                <FileText aria-hidden="true" />
                <span>
                  <strong>{plan.title}</strong>
                  <small>{plan.durationWeeks} 周 · {plan.weeklyHours} 小时/周</small>
                </span>
              </button>
            ))}
          </div>
        </aside>

        <div className="learning-main">
          {shouldKeepWizardMounted && (
            <div hidden={draft !== undefined}>
              <LearningPlanWizard
                loading={draft === undefined && flowState === 'generating'}
                onCancel={cancelCreating}
                onSubmit={submitDraft}
                resetStepSignal={wizardResetStepSignal}
              />
            </div>
          )}
          {draft ? (
            <LearningPlanDraftPanel
              draft={draft}
              loading={flowState === 'generating' || flowState === 'confirming'}
              onConfirm={confirmDraft}
              onReturnToWizard={returnToWizard}
              onSendFollowUp={sendFollowUp}
            />
          ) : !shouldKeepWizardMounted && selectedPlan ? (
            <LearningPlanDetail plan={selectedPlan} />
          ) : !shouldKeepWizardMounted ? (
            <article className="learning-panel empty-plan-panel">
              <h2>还没有学习计划</h2>
              <p>创建一个计划后，系统会在这里展示阶段、推荐题目和复盘建议。</p>
            </article>
          ) : null}
        </div>
      </div>
    </section>
  );
}
