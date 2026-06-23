import { useEffect, useState } from 'react';
import LearningPlanCreateModal from './learning-plans/LearningPlanCreateModal';
import LearningPlanDetail from './learning-plans/LearningPlanDetail';
import LearningPlanDraftPanel from './learning-plans/LearningPlanDraftPanel';
import LearningPlanListCard from './learning-plans/LearningPlanListCard';
import LearningPlanSummaryCard from './learning-plans/LearningPlanSummaryCard';
import {
  confirmLearningPlanDraft,
  createLearningPlanDraft,
  deleteLearningPlan,
  getLearningPlanDetail,
  getLearningPlans,
  sendLearningPlanDraftMessage,
} from './services/api';
import type {
  LearningPlanCreateDraftRequest,
  LearningPlanDetailResponse,
  LearningPlanDraftResponse,
  LearningPlanPageResponse,
} from './types/api';

type LearningPlanFlowState = 'idle' | 'creating' | 'generating' | 'collecting' | 'previewing' | 'confirming';

const INITIAL_PLANS_PAGE: LearningPlanPageResponse = {
  items: [],
  total: 0,
  page: 1,
  pageSize: 10,
  activeCount: 0,
  archivedCount: 0,
  latestCreatedAt: null,
};

function apiData<T>(response: { success: boolean; data?: T; error?: { message: string } }, fallback: string): T {
  if (!response.success || response.data === undefined) {
    throw new Error(response.error?.message ?? fallback);
  }
  return response.data;
}

export default function LearningPlans() {
  const [plansPage, setPlansPage] = useState<LearningPlanPageResponse>(INITIAL_PLANS_PAGE);
  const [page, setPage] = useState(1);
  const [selectedPlan, setSelectedPlan] = useState<LearningPlanDetailResponse>();
  const [draft, setDraft] = useState<LearningPlanDraftResponse>();
  const [flowState, setFlowState] = useState<LearningPlanFlowState>('idle');
  const [isCreateModalOpen, setCreateModalOpen] = useState(false);
  const [createModalKey, setCreateModalKey] = useState(0);
  const [modalError, setModalError] = useState('');
  const [deletingPlanId, setDeletingPlanId] = useState<number>();
  const [error, setError] = useState('');

  useEffect(() => {
    const controller = new AbortController();
    refreshPlans(1, undefined, controller.signal).catch((nextError) => {
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

  async function refreshPlans(nextPage = page, selectedId?: number, signal?: AbortSignal) {
    const nextPlans = apiData(
      await getLearningPlans({ page: nextPage, pageSize: plansPage.pageSize }, signal),
      '学习计划列表加载失败',
    );
    setPlansPage(nextPlans);
    setPage(nextPlans.page);

    if (selectedId) {
      await loadPlan(selectedId, signal);
      return;
    }

    if (!selectedPlan && nextPlans.items[0]) {
      await loadPlan(nextPlans.items[0].id, signal);
    }
  }

  async function submitDraft(request: LearningPlanCreateDraftRequest) {
    setFlowState('generating');
    setModalError('');
    setError('');
    try {
      const nextDraft = apiData(await createLearningPlanDraft(request), '学习计划草案创建失败');
      setDraft(nextDraft);
      setSelectedPlan(undefined);
      setCreateModalOpen(false);
      setFlowState(nextDraft.status === 'COLLECTING' ? 'collecting' : 'previewing');
    } catch (nextError) {
      setModalError(nextError instanceof Error ? nextError.message : '学习计划草案创建失败');
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

  async function regenerateFromGoal(goal: string) {
    return sendFollowUp(`请按新的目标摘要重新生成学习计划：${goal}`);
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
      setFlowState('idle');
      try {
        await refreshPlans(1, confirmed.planId);
      } catch (refreshError) {
        setError(refreshError instanceof Error ? refreshError.message : '学习计划列表加载失败');
      }
    } catch (nextError) {
      setError(nextError instanceof Error ? nextError.message : '学习计划确认失败');
      setFlowState('previewing');
    }
  }

  async function removePlan(planId: number) {
    if (!window.confirm('确认删除这个学习计划？')) {
      return;
    }

    setDeletingPlanId(planId);
    setError('');
    try {
      await deleteLearningPlan(planId);
      const shouldStepBack = plansPage.items.length === 1 && page > 1;
      const nextPage = shouldStepBack ? page - 1 : page;
      setSelectedPlan((current) => (current?.id === planId ? undefined : current));
      await refreshPlans(nextPage);
    } catch (nextError) {
      setError(nextError instanceof Error ? nextError.message : '学习计划删除失败');
    } finally {
      setDeletingPlanId(undefined);
    }
  }

  function openCreateModal() {
    setModalError('');
    setCreateModalKey((current) => current + 1);
    setCreateModalOpen(true);
    setFlowState('creating');
  }

  return (
    <section className="learning-shell" aria-label="学习计划">
      <LearningPlanSummaryCard
        activeCount={plansPage.activeCount}
        archivedCount={plansPage.archivedCount}
        latestCreatedAt={plansPage.latestCreatedAt}
        onCreate={openCreateModal}
        total={plansPage.total}
      />

      {error && <p className="error-text">{error}</p>}

      <LearningPlanListCard
        deletingPlanId={deletingPlanId}
        onDelete={removePlan}
        onPageChange={(nextPage) => {
          setPage(nextPage);
          void refreshPlans(nextPage);
        }}
        onSelect={(planId) => {
          setDraft(undefined);
          setFlowState('idle');
          void loadPlan(planId);
        }}
        page={plansPage}
        selectedPlanId={selectedPlan?.id}
      />

      <div className="learning-detail-area">
        {draft ? (
          <LearningPlanDraftPanel
            draft={draft}
            loading={flowState === 'generating' || flowState === 'confirming'}
            onConfirm={confirmDraft}
            onRegenerateGoal={regenerateFromGoal}
            onReturnToWizard={openCreateModal}
            onSendFollowUp={sendFollowUp}
          />
        ) : selectedPlan ? (
          <LearningPlanDetail plan={selectedPlan} />
        ) : (
          <article className="learning-panel empty-plan-panel">
            <h2>还没有学习计划</h2>
            <p>创建一个计划后，系统会在这里展示阶段、推荐题目和复盘建议。</p>
          </article>
        )}
      </div>

      <LearningPlanCreateModal
        error={modalError}
        key={createModalKey}
        loading={flowState === 'generating'}
        onClose={() => {
          setCreateModalOpen(false);
          setFlowState('idle');
        }}
        onSubmit={submitDraft}
        open={isCreateModalOpen}
      />
    </section>
  );
}
