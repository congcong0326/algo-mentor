import { useEffect, useState } from 'react';
import { APP_ROUTES } from './app/navigation';
import LearningPlanCreatePage from './learning-plans/LearningPlanCreatePage';
import LearningPlanListCard from './learning-plans/LearningPlanListCard';
import LearningPlanSummaryCard from './learning-plans/LearningPlanSummaryCard';
import {
  deleteLearningPlan,
  getLearningPlans,
} from './services/api';
import type { LearningPlanConfirmResponse, LearningPlanPageResponse } from './types/api';

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

function apiData<T>(response: { success: boolean; data?: T; error?: { message: string } }, fallback: string): T {
  if (!response.success || response.data === undefined) {
    throw new Error(response.error?.message ?? fallback);
  }
  return response.data;
}

export default function LearningPlans({ pathname, onNavigate }: LearningPlansProps) {
  const [plansPage, setPlansPage] = useState<LearningPlanPageResponse>(INITIAL_PLANS_PAGE);
  const [page, setPage] = useState(1);
  const [deletingPlanId, setDeletingPlanId] = useState<number>();
  const [error, setError] = useState('');

  useEffect(() => {
    if (pathname === APP_ROUTES.learningPlanNew) {
      return undefined;
    }

    const controller = new AbortController();
    refreshPlans(1, controller.signal).catch((nextError) => {
      if (!controller.signal.aborted) {
        setError(nextError instanceof Error ? nextError.message : '学习计划列表加载失败');
      }
    });

    return () => controller.abort();
  }, [pathname]);

  async function refreshPlans(nextPage = page, signal?: AbortSignal) {
    const nextPlans = apiData(
      await getLearningPlans({ page: nextPage, pageSize: plansPage.pageSize }, signal),
      '学习计划列表加载失败',
    );
    setPlansPage(nextPlans);
    setPage(nextPlans.page);
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
      await refreshPlans(nextPage);
    } catch (nextError) {
      setError(nextError instanceof Error ? nextError.message : '学习计划删除失败');
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

  return (
    <section className="learning-shell" aria-label="学习计划">
      <LearningPlanSummaryCard
        activeCount={plansPage.activeCount}
        archivedCount={plansPage.archivedCount}
        latestCreatedAt={plansPage.latestCreatedAt}
        onCreate={() => onNavigate(APP_ROUTES.learningPlanNew)}
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
        onSelect={() => undefined}
        page={plansPage}
      />
    </section>
  );
}
