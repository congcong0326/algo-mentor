import { lazy, Suspense, useEffect, useState } from 'react';
import {
  APP_ROUTES,
  learningPlanDetailPath,
  learningPlanIdFromPath,
  learningPlanPracticeChatPath,
  learningPlanPracticeChatRouteFromPath,
} from './app/navigation';
import LearningPlanCreatePage from './learning-plans/LearningPlanCreatePage';
import LearningPlanDetail from './learning-plans/LearningPlanDetail';
import LearningPlanListCard from './learning-plans/LearningPlanListCard';
import {
  deleteLearningPlan,
  getLearningPlanDetail,
  getLearningPlans,
} from './services/api';
import type { LearningPlanConfirmResponse, LearningPlanDetailResponse, LearningPlanPageResponse } from './types/api';

interface LearningPlansProps {
  pathname: string;
  onNavigate: (pathname: string, options?: { replace?: boolean }) => void;
}

const INITIAL_PLANS_PAGE: LearningPlanPageResponse = {
  items: [],
  total: 0,
  page: 1,
  pageSize: 10,
  activeCount: 0,
  archivedCount: 0,
  latestCreatedAt: null,
};

const PracticeChatWorkbench = lazy(() => import('./learning-plans/PracticeChatWorkbench'));

function apiData<T>(response: { success: boolean; data?: T; error?: { message: string } }, fallback: string): T {
  if (!response.success || response.data === undefined) {
    throw new Error(response.error?.message ?? fallback);
  }
  return response.data;
}

export default function LearningPlans({ pathname, onNavigate }: LearningPlansProps) {
  const [plansPage, setPlansPage] = useState<LearningPlanPageResponse>(INITIAL_PLANS_PAGE);
  const [planDetail, setPlanDetail] = useState<LearningPlanDetailResponse>();
  const [page, setPage] = useState(1);
  const [deletingPlanId, setDeletingPlanId] = useState<number>();
  const [error, setError] = useState('');
  const practiceChatRoute = learningPlanPracticeChatRouteFromPath(pathname);
  const selectedPlanId = practiceChatRoute?.planId ?? learningPlanIdFromPath(pathname);

  useEffect(() => {
    if (pathname === APP_ROUTES.learningPlanNew || selectedPlanId !== undefined) {
      return undefined;
    }

    const controller = new AbortController();
    refreshPlans(1, controller.signal).catch((nextError) => {
      if (!controller.signal.aborted) {
        setError(nextError instanceof Error ? nextError.message : '训练方案列表加载失败');
      }
    });

    return () => controller.abort();
  }, [pathname, selectedPlanId]);

  useEffect(() => {
    if (selectedPlanId === undefined) {
      setPlanDetail(undefined);
      return undefined;
    }

    const controller = new AbortController();
    setError('');
    setPlanDetail(undefined);
    loadPlanDetail(selectedPlanId, controller.signal).catch((nextError) => {
      if (!controller.signal.aborted) {
        setError(nextError instanceof Error ? nextError.message : '训练方案详情加载失败');
      }
    });

    return () => controller.abort();
  }, [selectedPlanId]);

  async function refreshPlans(nextPage = page, signal?: AbortSignal) {
    const nextPlans = apiData(
      await getLearningPlans({ page: nextPage, pageSize: plansPage.pageSize }, signal),
      '训练方案列表加载失败',
    );
    setPlansPage(nextPlans);
    setPage(nextPlans.page);
  }

  async function loadPlanDetail(planId: number, signal?: AbortSignal) {
    const detail = apiData(
      await getLearningPlanDetail(planId, signal),
      '训练方案详情加载失败',
    );
    setPlanDetail(detail);
  }

  async function removePlan(planId: number) {
    if (!window.confirm('确认删除这个训练方案？')) {
      return;
    }

    setDeletingPlanId(planId);
    setError('');
    try {
      await deleteLearningPlan(planId);
      const shouldStepBack = plansPage.items.length === 1 && page > 1;
      const nextPage = shouldStepBack ? page - 1 : page;
      await refreshPlans(nextPage);
    } catch (nextError) {
      setError(nextError instanceof Error ? nextError.message : '训练方案删除失败');
    } finally {
      setDeletingPlanId(undefined);
    }
  }

  function handlePlanSaved(_confirmed: LearningPlanConfirmResponse) {
    onNavigate(APP_ROUTES.learningPlans, { replace: true });
  }

  if (pathname === APP_ROUTES.learningPlanNew) {
    return (
      <LearningPlanCreatePage
        onBackToPlans={() => onNavigate(APP_ROUTES.learningPlans)}
        onSaved={handlePlanSaved}
      />
    );
  }

  if (selectedPlanId !== undefined) {
    return (
      <section className="learning-shell" aria-label="训练方案详情">
        {error && <p className="error-text">{error}</p>}
        {!error && !planDetail ? (
          <article className="learning-panel" aria-busy="true">
            <p className="eyebrow">Learning Plan</p>
            <h2>正在加载方案详情...</h2>
          </article>
        ) : null}
        {planDetail ? (
          practiceChatRoute ? (
            <Suspense fallback={(
              <article className="learning-panel" aria-busy="true">
                <p className="eyebrow">Practice Chat</p>
                <h2>正在加载题目聊天页...</h2>
              </article>
            )}
            >
              <PracticeChatWorkbench
                onBack={() => onNavigate(learningPlanDetailPath(planDetail.id))}
                phaseIndex={practiceChatRoute.phaseIndex}
                plan={planDetail}
                problemSlug={practiceChatRoute.problemSlug}
              />
            </Suspense>
          ) : (
            <LearningPlanDetail
              onBack={() => onNavigate(APP_ROUTES.learningPlans)}
              onProblemSelect={(phaseIndex, problemSlug) => {
                onNavigate(learningPlanPracticeChatPath(planDetail.id, phaseIndex, problemSlug));
              }}
              plan={planDetail}
            />
          )
        ) : null}
      </section>
    );
  }

  return (
    <section className="learning-shell" aria-label="训练方案">
      {error && <p className="error-text">{error}</p>}

      <LearningPlanListCard
        deletingPlanId={deletingPlanId}
        onCreate={() => onNavigate(APP_ROUTES.learningPlanNew)}
        onDelete={removePlan}
        onPageChange={(nextPage) => {
          setPage(nextPage);
          void refreshPlans(nextPage);
        }}
        onSelect={(planId) => onNavigate(learningPlanDetailPath(planId))}
        page={plansPage}
      />
    </section>
  );
}
