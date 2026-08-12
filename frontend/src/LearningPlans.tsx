import { lazy, Suspense, useEffect, useState } from 'react';
import {
  APP_ROUTES,
  LEARNER_PROFILE_REVIEW_ORIGIN,
  REVIEW_CENTER_REVIEW_ORIGIN,
  learnerProfilePath,
  learningPlanDetailPath,
  learningPlanIdFromPath,
  learningPlanPracticeChatPath,
  learningPlanPracticeChatRouteFromPath,
  learningPlanPracticeSubmissionsPath,
  learningPlanPracticeSubmissionsOptionsFromSearch,
  learningPlanPracticeSubmissionsRouteFromPath,
  learningPlanTodayPackPath,
} from './app/navigation';
import LearningPlanCreatePage from './learning-plans/LearningPlanCreatePage';
import LearningPlanDetail from './learning-plans/LearningPlanDetail';
import LearningPlanListCard from './learning-plans/LearningPlanListCard';
import { TodayPackPanel } from './TodayPackPage';
import { useI18n } from './i18n/I18nProvider';
import {
  activateLearningPlan,
  deleteLearningPlan,
  getLearningPlanDetail,
  getLearningPlanAiRevisionCapabilities,
  getLearningPlans,
  requireApiData,
} from './services/api';
import type { LearningPlanAiRevisionCapabilities, LearningPlanConfirmResponse, LearningPlanDetailResponse, LearningPlanPageResponse } from './types/api';

interface LearningPlansProps {
  pathname: string;
  search: string;
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
const DISABLED_AI_REVISION_CAPABILITIES: LearningPlanAiRevisionCapabilities = {
  templateDraftRevisionEnabled: false,
  savedPlanRevisionEnabled: false,
  personalizedDraftRevisionEnabled: false,
};

const PracticeChatWorkbench = lazy(() => import('./learning-plans/PracticeChatWorkbench'));
const PracticeSubmissionHistoryPage = lazy(() => import('./learning-plans/PracticeSubmissionHistoryPage'));

export default function LearningPlans({ pathname, search, onNavigate }: LearningPlansProps) {
  const { resources } = useI18n();
  const [plansPage, setPlansPage] = useState<LearningPlanPageResponse>(INITIAL_PLANS_PAGE);
  const [planDetail, setPlanDetail] = useState<LearningPlanDetailResponse>();
  const [contractFeedback, setContractFeedback] = useState('');
  const [page, setPage] = useState(1);
  const [deletingPlanId, setDeletingPlanId] = useState<number>();
  const [activatingPlanId, setActivatingPlanId] = useState<number>();
  const [error, setError] = useState('');
  const [aiRevisionCapabilities, setAiRevisionCapabilities] = useState<LearningPlanAiRevisionCapabilities>(DISABLED_AI_REVISION_CAPABILITIES);
  const practiceChatRoute = learningPlanPracticeChatRouteFromPath(pathname);
  const practiceSubmissionsRoute = learningPlanPracticeSubmissionsRouteFromPath(pathname);
  const practiceSubmissionsOptions = learningPlanPracticeSubmissionsOptionsFromSearch(search);
  const isTodayPackMode = new URLSearchParams(search).get('pack') === 'today';
  const selectedPlanId = practiceChatRoute?.planId
    ?? practiceSubmissionsRoute?.planId
    ?? learningPlanIdFromPath(pathname);

  useEffect(() => {
    const controller = new AbortController();
    getLearningPlanAiRevisionCapabilities(controller.signal)
      .then((response) => setAiRevisionCapabilities(response.success && response.data
        ? response.data : DISABLED_AI_REVISION_CAPABILITIES))
      .catch(() => setAiRevisionCapabilities(DISABLED_AI_REVISION_CAPABILITIES));
    return () => controller.abort();
  }, [pathname, selectedPlanId]);

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

  useEffect(() => {
    if (planDetail && isTodayPackMode && !practiceChatRoute && !practiceSubmissionsRoute && !planDetail.active) {
      onNavigate(learningPlanDetailPath(planDetail.id), { replace: true });
    }
  }, [isTodayPackMode, onNavigate, planDetail, practiceChatRoute, practiceSubmissionsRoute]);

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

  async function activatePlan(planId: number) {
    if (!window.confirm(resources.learningPlans.activateConfirm)) {
      return;
    }

    setActivatingPlanId(planId);
    setError('');
    try {
      requireApiData(await activateLearningPlan(planId), resources.learningPlans.activateFailed);
      if (planDetail?.id === planId) {
        await refreshCurrentPlanDetail(planId);
      } else {
        await refreshPlans(page);
      }
    } catch (nextError) {
      setError(nextError instanceof Error ? nextError.message : resources.learningPlans.activateFailed);
    } finally {
      setActivatingPlanId(undefined);
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
        capabilities={aiRevisionCapabilities}
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
                    onNavigate(isTodayPackMode
                      ? learningPlanTodayPackPath(planDetail.id)
                      : learningPlanDetailPath(planDetail.id));
                  });
                }}
                onOpenSubmissions={() => {
                  const submissionsPath = learningPlanPracticeSubmissionsPath(
                    planDetail.id,
                    practiceChatRoute.phaseIndex,
                    practiceChatRoute.problemSlug,
                  );
                  onNavigate(isTodayPackMode ? `${submissionsPath}?pack=today` : submissionsPath);
                }}
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
                onBack={() => {
                  if (
                    practiceSubmissionsOptions.from === LEARNER_PROFILE_REVIEW_ORIGIN
                    && practiceSubmissionsOptions.profileAnchor
                  ) {
                    onNavigate(learnerProfilePath({ anchor: practiceSubmissionsOptions.profileAnchor }));
                    return;
                  }
                  if (practiceSubmissionsOptions.from === REVIEW_CENTER_REVIEW_ORIGIN) {
                    onNavigate(practiceSubmissionsOptions.returnTo ?? APP_ROUTES.mistakes);
                    return;
                  }
                  const chatPath = learningPlanPracticeChatPath(
                    planDetail.id,
                    practiceSubmissionsRoute.phaseIndex,
                    practiceSubmissionsRoute.problemSlug,
                  );
                  onNavigate(isTodayPackMode ? `${chatPath}?pack=today` : chatPath);
                }}
                phaseIndex={practiceSubmissionsRoute.phaseIndex}
                plan={planDetail}
                problemSlug={practiceSubmissionsRoute.problemSlug}
                requestedReviewId={practiceSubmissionsOptions.reviewId}
                returnProfileAnchor={practiceSubmissionsOptions.from === LEARNER_PROFILE_REVIEW_ORIGIN
                  ? practiceSubmissionsOptions.profileAnchor
                  : undefined}
                returnToReviewCenter={practiceSubmissionsOptions.from === REVIEW_CENTER_REVIEW_ORIGIN}
              />
            </Suspense>
          ) : isTodayPackMode && planDetail.active ? (
            <TodayPackPanel
              contractFeedback={contractFeedback}
              onNavigate={onNavigate}
              onPlanUpdated={() => {
                setContractFeedback('');
                return refreshCurrentPlanDetail(planDetail.id);
              }}
              plan={planDetail}
            />
          ) : (
            <LearningPlanDetail
              onBack={() => onNavigate(APP_ROUTES.learningPlans)}
              onPlanUpdated={() => {
                setContractFeedback('');
                return refreshCurrentPlanDetail(planDetail.id);
              }}
              onProblemSelect={(phaseIndex, problemSlug) => {
                onNavigate(learningPlanPracticeChatPath(planDetail.id, phaseIndex, problemSlug));
              }}
              plan={planDetail}
              capabilities={aiRevisionCapabilities}
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
        activatingPlanId={activatingPlanId}
        onCreate={() => onNavigate(APP_ROUTES.learningPlanNew)}
        onActivate={activatePlan}
        onDelete={removePlan}
        onPageChange={(nextPage) => {
          setPage(nextPage);
          void refreshPlans(nextPage);
        }}
        onOpenTodayPack={(planId) => onNavigate(learningPlanTodayPackPath(planId))}
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
