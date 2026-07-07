import { lazy, Suspense, useEffect, useState } from 'react';
import {
  APP_ROUTES,
  learningPlanDetailPath,
  learningPlanIdFromPath,
  learningPlanPracticeChatPath,
  learningPlanPracticeChatRouteFromPath,
  learningPlanPracticeSubmissionsPath,
  learningPlanPracticeSubmissionsRouteFromPath,
} from './app/navigation';
import LearningPlanCreatePage from './learning-plans/LearningPlanCreatePage';
import LearningPlanDetail from './learning-plans/LearningPlanDetail';
import LearningPlanListCard from './learning-plans/LearningPlanListCard';
import { useI18n } from './i18n/I18nProvider';
import {
  deleteLearningPlan,
  getLearningPlanDetail,
  getLearningPlans,
  requireApiData,
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
const PracticeSubmissionHistoryPage = lazy(() => import('./learning-plans/PracticeSubmissionHistoryPage'));

export default function LearningPlans({ pathname, onNavigate }: LearningPlansProps) {
  const { resources } = useI18n();
  const [plansPage, setPlansPage] = useState<LearningPlanPageResponse>(INITIAL_PLANS_PAGE);
  const [planDetail, setPlanDetail] = useState<LearningPlanDetailResponse>();
  const [contractFeedback, setContractFeedback] = useState('');
  const [page, setPage] = useState(1);
  const [deletingPlanId, setDeletingPlanId] = useState<number>();
  const [error, setError] = useState('');
  const practiceChatRoute = learningPlanPracticeChatRouteFromPath(pathname);
  const practiceSubmissionsRoute = learningPlanPracticeSubmissionsRouteFromPath(pathname);
  const selectedPlanId = practiceChatRoute?.planId
    ?? practiceSubmissionsRoute?.planId
    ?? learningPlanIdFromPath(pathname);

  useEffect(() => {
    if (pathname === APP_ROUTES.learningPlanNew || selectedPlanId !== undefined) {
      return undefined;
    }

    const controller = new AbortController();
    refreshPlans(1, controller.signal).catch((nextError) => {
      if (!controller.signal.aborted) {
        setError(nextError instanceof Error ? nextError.message : resources.learningPlans.listLoadFailed);
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
        setError(nextError instanceof Error ? nextError.message : resources.learningPlans.detailLoadFailed);
      }
    });

    return () => controller.abort();
  }, [selectedPlanId]);

  async function refreshPlans(nextPage = page, signal?: AbortSignal) {
    const nextPlans = requireApiData(
      await getLearningPlans({ page: nextPage, pageSize: plansPage.pageSize }, signal),
      resources.learningPlans.listLoadFailed,
    );
    setPlansPage(nextPlans);
    setPage(nextPlans.page);
  }

  async function loadPlanDetail(planId: number, signal?: AbortSignal) {
    const detail = requireApiData(
      await getLearningPlanDetail(planId, signal),
      resources.learningPlans.detailLoadFailed,
    );
    setContractFeedback((current) => contractEstimateFeedback(planDetail, detail, resources.learningPlans.contractDateMovedEarlier, resources.learningPlans.contractDateMovedLater) || current);
    setPlanDetail(detail);
  }

  async function refreshCurrentPlanDetail(planId: number) {
    setError('');
    await loadPlanDetail(planId).catch((nextError) => {
      setError(nextError instanceof Error ? nextError.message : resources.learningPlans.detailLoadFailed);
      throw nextError;
    });
  }

  async function removePlan(planId: number) {
    if (!window.confirm(resources.learningPlans.confirmDelete)) {
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
      setError(nextError instanceof Error ? nextError.message : resources.learningPlans.deleteFailed);
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
      <section className="learning-shell" aria-label={resources.learningPlans.detailAriaLabel}>
        {error && <p className="error-text">{error}</p>}
        {!error && !planDetail ? (
          <article className="learning-panel" aria-busy="true">
            <p className="eyebrow">{resources.learningPlans.learningPlanEyebrow}</p>
            <h2>{resources.learningPlans.loadingDetail}</h2>
          </article>
        ) : null}
        {planDetail ? (
          practiceChatRoute ? (
            <Suspense fallback={(
              <article className="learning-panel" aria-busy="true">
                <p className="eyebrow">{resources.learningPlans.practiceChatEyebrow}</p>
                <h2>{resources.learningPlans.loadingPracticeChat}</h2>
              </article>
            )}
            >
              <PracticeChatWorkbench
                onBack={() => {
                  void refreshCurrentPlanDetail(planDetail.id).finally(() => {
                    onNavigate(learningPlanDetailPath(planDetail.id));
                  });
                }}
                onOpenSubmissions={() => onNavigate(learningPlanPracticeSubmissionsPath(
                  planDetail.id,
                  practiceChatRoute.phaseIndex,
                  practiceChatRoute.problemSlug,
                ))}
                onProgressUpdated={() => refreshCurrentPlanDetail(planDetail.id)}
                phaseIndex={practiceChatRoute.phaseIndex}
                plan={planDetail}
                problemSlug={practiceChatRoute.problemSlug}
              />
            </Suspense>
          ) : practiceSubmissionsRoute ? (
            <Suspense fallback={(
              <article className="learning-panel" aria-busy="true">
                <p className="eyebrow">{resources.learningPlans.practiceChatEyebrow}</p>
                <h2>{resources.learningPlans.reviewLoading}</h2>
              </article>
            )}
            >
              <PracticeSubmissionHistoryPage
                onBackToChat={() => onNavigate(learningPlanPracticeChatPath(
                  planDetail.id,
                  practiceSubmissionsRoute.phaseIndex,
                  practiceSubmissionsRoute.problemSlug,
                ))}
                phaseIndex={practiceSubmissionsRoute.phaseIndex}
                plan={planDetail}
                problemSlug={practiceSubmissionsRoute.problemSlug}
              />
            </Suspense>
          ) : (
            <LearningPlanDetail
              contractFeedback={contractFeedback}
              onBack={() => onNavigate(APP_ROUTES.learningPlans)}
              onPlanUpdated={() => {
                setContractFeedback('');
                return refreshCurrentPlanDetail(planDetail.id);
              }}
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
    <section className="learning-shell" aria-label={resources.learningPlans.ariaLabel}>
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

function contractEstimateFeedback(
  previous: LearningPlanDetailResponse | undefined,
  next: LearningPlanDetailResponse,
  movedEarlier: (date: string) => string,
  movedLater: (date: string) => string,
) {
  if (!previous || previous.id !== next.id) {
    return '';
  }
  const previousDate = previous.livingContractSummary?.estimatedCompletionDate;
  const nextDate = next.livingContractSummary?.estimatedCompletionDate;
  if (!previousDate || !nextDate || previousDate === nextDate) {
    return '';
  }
  return nextDate < previousDate ? movedEarlier(nextDate) : movedLater(nextDate);
}
