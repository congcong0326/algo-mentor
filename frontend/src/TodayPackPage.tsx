import {
  Activity,
  ArrowRight,
  BrainCircuit,
  CalendarDays,
  CheckCircle2,
  ChevronRight,
  CircleHelp,
  ClipboardList,
  RotateCcw,
  SlidersHorizontal,
  Target,
  Trophy,
} from 'lucide-react';
import { useEffect, useMemo, useState } from 'react';
import { formatAbilityScore } from './ability/abilityProfile';
import ActivityHeatmap from './activity/ActivityHeatmap';
import { toActivityCalendarData, type ActivityCalendarData } from './activity/activityHeatmap';
import { APP_ROUTES, learningPlanDetailPath, learningPlanPracticeChatPath, learningPlanTodayPackPath } from './app/navigation';
import { formatDate, formatDifficulty, formatProblemTitle } from './i18n/formatters';
import { useI18n } from './i18n/I18nProvider';
import type { LocaleResources } from './i18n/locales';
import {
  buildStandardRhythmReference,
  compareRhythmWeeks,
  estimateRhythmWeeks,
} from './learning-plans/learningPlanRhythm';
import {
  getTodayPackHomeSummary,
  getActivityContributions,
  getAbilityHomeSummary,
  getReviewSummary,
  ApiRequestError,
  getLearningPlanTodayPack,
  LEARNING_PLAN_ACTIVE_SELECTION_MISMATCH_CODE,
  requireApiData,
  restartTodayPack,
  updateLearningPlanTodayPackRhythm,
} from './services/api';
import type {
  LearningPlanPaceStatus,
  AbilityHomeSummaryResponse,
  TodayPackProblemResponse,
  TodayPackHomeSummaryResponse,
  TodayPackResponse,
  TodayPackWorkspaceResponse,
  ReviewSummaryResponse,
} from './types/api';
import { browserTimezone, formatUpcomingReviewTime } from './utils/time';

interface TodayPackPageProps {
  onNavigate: (pathname: string) => void;
}

interface TodayPackPanelProps {
  onNavigate: (pathname: string, options?: { replace?: boolean }) => void;
  planId: number;
}

export default function TodayPackPage({ onNavigate }: TodayPackPageProps) {
  const { locale, resources } = useI18n();
  const [timezone] = useState(browserTimezone);
  const [pack, setPack] = useState<TodayPackHomeSummaryResponse>();
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [reviewSummary, setReviewSummary] = useState<ReviewSummaryResponse>();
  const [reviewRefreshVersion, setReviewRefreshVersion] = useState(0);
  const [reviewClock, setReviewClock] = useState(Date.now);
  const [reviewLoading, setReviewLoading] = useState(true);
  const [reviewUnavailable, setReviewUnavailable] = useState(false);
  const [abilitySummary, setAbilitySummary] = useState<AbilityHomeSummaryResponse>();
  const [abilityLoading, setAbilityLoading] = useState(true);
  const [abilityUnavailable, setAbilityUnavailable] = useState(false);
  const [activity, setActivity] = useState<ActivityCalendarData>();
  const [activityLoading, setActivityLoading] = useState(true);
  const [activityUnavailable, setActivityUnavailable] = useState(false);
  useEffect(() => {
    const controller = new AbortController();
    setLoading(true);
    setError('');
    void getTodayPackHomeSummary(timezone, controller.signal)
      .then((response) => {
        setPack(requireApiData(response, resources.todayPack.homeLoadFailed));
      })
      .catch((nextError) => {
        if (!controller.signal.aborted) {
          setError(nextError instanceof Error ? nextError.message : resources.todayPack.homeLoadFailed);
        }
      })
      .finally(() => {
        if (!controller.signal.aborted) {
          setLoading(false);
        }
      });
    return () => controller.abort();
  }, [locale, resources.todayPack.homeLoadFailed, timezone]);

  useEffect(() => {
    const controller = new AbortController();
    let refreshTimer: number | undefined;
    if (reviewRefreshVersion === 0) {
      setReviewLoading(true);
    }
    setReviewUnavailable(false);
    void getReviewSummary(controller.signal)
      .then((response) => {
        const summary = requireApiData(response, resources.todayPack.reviewSummaryLoadFailed);
        setReviewSummary(summary);
        const nextDueTime = summary.nextDueAt ? new Date(summary.nextDueAt).getTime() : Number.NaN;
        if (summary.dueCount === 0
          && summary.remainingTodayCount > 0
          && Number.isFinite(nextDueTime)
          && nextDueTime > Date.now()) {
          refreshTimer = window.setTimeout(
            () => setReviewRefreshVersion((current) => current + 1),
            nextDueTime - Date.now() + 250,
          );
        }
      })
      .catch(() => {
        if (!controller.signal.aborted) {
          setReviewUnavailable(true);
        }
      })
      .finally(() => {
        if (!controller.signal.aborted) {
          setReviewLoading(false);
        }
      });
    return () => {
      controller.abort();
      if (refreshTimer !== undefined) {
        window.clearTimeout(refreshTimer);
      }
    };
  }, [locale, resources.todayPack.reviewSummaryLoadFailed, reviewRefreshVersion]);

  useEffect(() => {
    if (reviewSummary?.dueCount !== 0 || !reviewSummary.nextDueAt) {
      return undefined;
    }
    setReviewClock(Date.now());
    const timer = window.setInterval(() => setReviewClock(Date.now()), 30_000);
    return () => window.clearInterval(timer);
  }, [reviewSummary?.dueCount, reviewSummary?.nextDueAt]);

  useEffect(() => {
    const controller = new AbortController();
    setAbilityLoading(true);
    setAbilityUnavailable(false);
    void getAbilityHomeSummary(controller.signal)
      .then((response) => {
        setAbilitySummary(requireApiData(response, resources.home.abilityLoadFailed));
      })
      .catch(() => {
        if (!controller.signal.aborted) {
          setAbilityUnavailable(true);
        }
      })
      .finally(() => {
        if (!controller.signal.aborted) {
          setAbilityLoading(false);
        }
      });
    return () => controller.abort();
  }, [resources.home.abilityLoadFailed]);

  useEffect(() => {
    const controller = new AbortController();
    setActivityLoading(true);
    setActivityUnavailable(false);
    void getActivityContributions(timezone, controller.signal)
      .then((response) => {
        setActivity(toActivityCalendarData(requireApiData(response, resources.todayPack.activityUnavailable)));
      })
      .catch(() => {
        if (!controller.signal.aborted) {
          setActivity(undefined);
          setActivityUnavailable(true);
        }
      })
      .finally(() => {
        if (!controller.signal.aborted) {
          setActivityLoading(false);
        }
      });
    return () => controller.abort();
  }, [resources.todayPack.activityUnavailable, timezone]);

  const activePlan = pack?.activePlan;
  const statusText = pack
    ? todayPackHomeStatusText(pack, resources.todayPack)
    : loading
      ? resources.todayPack.loadingStatus
      : resources.todayPack.trainingStatusUnavailable;
  const reviewDueCount = reviewSummary?.dueCount ?? 0;
  const remainingTodayCount = reviewSummary?.remainingTodayCount ?? reviewDueCount;
  const hasUpcomingReview = reviewDueCount === 0 && remainingTodayCount > 0;
  const reviewStatusText = reviewLoading
    ? resources.todayPack.reviewStatusLoading
    : reviewUnavailable
      ? resources.todayPack.reviewStatusUnavailable
      : reviewDueCount > 0
        ? resources.todayPack.reviewDue(reviewDueCount)
        : hasUpcomingReview
          ? resources.todayPack.reviewUpcoming(
            remainingTodayCount,
            formatUpcomingReviewTime(reviewSummary?.nextDueAt, reviewClock, locale),
          )
          : resources.todayPack.todayCompleted;
  const reviewActionLabel = reviewLoading
    ? resources.todayPack.reviewStatusLoading
    : reviewUnavailable
      ? resources.todayPack.reviewStatusUnavailable
      : reviewDueCount > 0
        ? resources.todayPack.reviewStart(reviewDueCount)
        : hasUpcomingReview
          ? resources.todayPack.reviewSchedule
          : resources.todayPack.todayCompleted;
  const reviewActionDisabled = reviewLoading || reviewUnavailable || remainingTodayCount === 0;
  const dashboardDate = new Intl.DateTimeFormat(locale, {
    month: 'long',
    day: 'numeric',
    weekday: 'long',
  }).format(new Date(`${pack?.localDate ?? new Date().toISOString().slice(0, 10)}T00:00:00`));
  const weeklyTarget = activePlan ? activePlan.dailyProblemCount * activePlan.trainingDaysPerWeek : 0;

  return (
    <article className="today-pack-home" aria-label={resources.todayPack.homeAriaLabel}>
      <header className="home-dashboard-heading">
        <div>
          <h1>{resources.home.workspaceTitle}</h1>
        </div>
        <span className="home-dashboard-date">
          <CalendarDays aria-hidden="true" />
          {dashboardDate}
        </span>
      </header>

      {error && <p className="error-text" role="alert">{error}</p>}

      <div className="home-focus-grid">
        <section className="home-focus-panel training" aria-label={resources.todayPack.trainingEntryAriaLabel}>
          <div className="home-focus-panel-topline">
            <span><Target aria-hidden="true" />{activePlan ? resources.todayPack.todayPack : resources.todayPack.startTraining}</span>
            {activePlan ? <small>{activePlan.title}</small> : null}
          </div>
          <div className="home-focus-copy">
            <strong>{statusText}</strong>
            <p>
              {activePlan
                ? resources.todayPack.activePlanRhythm(
                  activePlan.dailyProblemCount,
                  activePlan.trainingDaysPerWeek,
                  activePlan.remainingProblemCount,
                )
                : resources.todayPack.noPlanGuidance}
            </p>
          </div>
          {activePlan ? (
            <button
              className="primary-button"
              onClick={() => onNavigate(learningPlanTodayPackPath(activePlan.planId))}
              type="button"
            >
              <span>{resources.todayPack.startTodayTraining}</span>
              <ArrowRight aria-hidden="true" />
            </button>
          ) : (
            <button
              className="primary-button"
              onClick={() => onNavigate(APP_ROUTES.learningPlans)}
              type="button"
            >
              <span>{resources.todayPack.choosePlan}</span>
              <ArrowRight aria-hidden="true" />
            </button>
          )}
        </section>

        <section className="home-focus-panel review" aria-busy={reviewLoading} aria-label={resources.todayPack.reviewEntryAriaLabel}>
          <div className="home-focus-panel-topline">
            <span><Activity aria-hidden="true" />{resources.todayPack.reviewCenter}</span>
          </div>
          <div className="home-focus-copy">
            <strong>{reviewStatusText}</strong>
            <p>{resources.todayPack.reviewDescription}</p>
          </div>
          <button
            className="secondary-button"
            disabled={reviewActionDisabled}
            onClick={() => onNavigate(reviewDueCount > 0 ? APP_ROUTES.reviewSession : APP_ROUTES.mistakes)}
            type="button"
          >
            <span>{reviewActionLabel}</span>
            <ArrowRight aria-hidden="true" />
          </button>
        </section>
      </div>

      <ActivityHeatmap data={activity} loading={activityLoading} unavailable={activityUnavailable} />

      <div className="home-dashboard-grid">
        <section className="home-ability-panel" aria-labelledby="home-ability-title">
          <div className="home-panel-heading">
            <div>
              <h2 id="home-ability-title">{resources.todayPack.diagnosisTitle}</h2>
              <p>{resources.todayPack.diagnosisDescription}</p>
            </div>
            <button className="text-action-button" onClick={() => onNavigate(APP_ROUTES.my)} type="button">
              <span>{resources.todayPack.viewFullProfile}</span>
              <ArrowRight aria-hidden="true" />
            </button>
          </div>
          {abilityLoading ? (
            <div className="home-panel-state" role="status">{resources.home.abilityLoading}</div>
          ) : abilityUnavailable ? (
            <div className="home-panel-state">{resources.todayPack.abilityUnavailable}</div>
          ) : abilitySummary?.currentStrength ? (
            <div className="home-ability-insights">
              <div className="home-ability-stat-row">
                <span>
                  <BrainCircuit aria-hidden="true" />
                  {resources.todayPack.averageAbility}
                </span>
                <strong>{formatAbilityScore(abilitySummary.averageAbilityScore, locale)} / 10</strong>
              </div>
              <div className="home-insight-block strength">
                <span><Trophy aria-hidden="true" />{resources.todayPack.currentStrength}</span>
                <strong>{abilitySummary.currentStrength.label}</strong>
                <p>
                  {resources.todayPack.strengthEvidence(abilitySummary.currentStrength.reviewedProblemCount)}
                </p>
              </div>
              <div className="home-insight-block next">
                <span><Target aria-hidden="true" />{resources.todayPack.nextBreakthrough}</span>
                <strong>{abilitySummary.nextBreakthrough?.label ?? resources.todayPack.breakthroughFallback}</strong>
                <p>{abilitySummary.nextBreakthrough
                  ? resources.todayPack.breakthroughAdvice(abilitySummary.nextBreakthrough.label)
                  : resources.myPage.noTopAbilities}
                </p>
              </div>
            </div>
          ) : (
            <div className="home-panel-state">{resources.home.abilityEmpty}</div>
          )}
        </section>

        <aside className="home-plan-panel" aria-labelledby="home-plan-title">
          <div className="home-panel-heading compact-heading">
            <div>
              <h2 id="home-plan-title">{resources.todayPack.weeklyRhythm}</h2>
            </div>
          </div>
          {activePlan ? (
            <>
              <strong className="home-plan-title">{activePlan.title}</strong>
              <dl className="home-plan-metrics">
                <div>
                  <dt>{resources.todayPack.dailyTraining}</dt>
                  <dd>{resources.todayPack.problemCount(activePlan.dailyProblemCount)}</dd>
                </div>
                <div>
                  <dt>{resources.todayPack.weeklyTarget}</dt>
                  <dd>{resources.todayPack.problemCount(weeklyTarget)}</dd>
                </div>
                <div>
                  <dt>{resources.todayPack.remainingProblems}</dt>
                  <dd>{resources.todayPack.problemCount(activePlan.remainingProblemCount)}</dd>
                </div>
              </dl>
              <button className="secondary-button compact" onClick={() => onNavigate(APP_ROUTES.learningPlans)} type="button">
                <span>{resources.todayPack.managePlan}</span>
                <ArrowRight aria-hidden="true" />
              </button>
            </>
          ) : (
            <div className="home-empty-plan">
              <strong>{pack?.recommendedPlan?.title ?? resources.todayPack.noActivePlan}</strong>
              <p>{pack?.recommendedPlan?.summary ?? resources.todayPack.noActivePlanDescription}</p>
              <button className="secondary-button compact" onClick={() => onNavigate(APP_ROUTES.learningPlans)} type="button">
                {resources.todayPack.viewPlans}
              </button>
            </div>
          )}
        </aside>
      </div>
    </article>
  );
}

export function TodayPackPanel({ onNavigate, planId }: TodayPackPanelProps) {
  const { locale, resources } = useI18n();
  const [timezone] = useState(browserTimezone);
  const [packOffset, setPackOffset] = useState(0);
  const [workspace, setWorkspace] = useState<TodayPackWorkspaceResponse>();
  const [loading, setLoading] = useState(true);
  const [actionLoading, setActionLoading] = useState(false);
  const [error, setError] = useState('');
  const [rhythmDialogOpen, setRhythmDialogOpen] = useState(false);
  const [rhythmDailyProblemCount, setRhythmDailyProblemCount] = useState(1);
  const [rhythmTrainingDaysPerWeek, setRhythmTrainingDaysPerWeek] = useState(5);
  const [rhythmUpdating, setRhythmUpdating] = useState(false);
  const [rhythmError, setRhythmError] = useState('');
  const pack = workspace?.pack;
  const plan = workspace?.plan;
  const pace = plan?.paceSummary;
  const contract = plan?.livingContractSummary;
  const rhythmSettings = plan?.rhythmSettings;
  const standardRhythm = buildStandardRhythmReference({
    recommendedWeeks: plan?.durationWeeks ?? 1,
    settings: rhythmSettings,
    totalProblemCount: rhythmSettings?.totalProblemCount ?? 0,
  });
  const standardRemainingWeeks = estimateRhythmWeeks(
    rhythmSettings?.remainingProblemCount ?? 0,
    standardRhythm.dailyProblemCount,
    standardRhythm.trainingDaysPerWeek,
  );
  const adjustedRemainingWeeks = estimateRhythmWeeks(
    rhythmSettings?.remainingProblemCount ?? 0,
    rhythmDailyProblemCount,
    rhythmTrainingDaysPerWeek,
  );
  const adjustedStandardDelta = compareRhythmWeeks(adjustedRemainingWeeks, standardRemainingWeeks);
  const totalProblems = useMemo(
    () => pack?.sections.reduce((total, section) => total + section.problems.length, 0) ?? 0,
    [pack],
  );

  useEffect(() => {
    const controller = new AbortController();
    setLoading(true);
    setError('');
    setWorkspace(undefined);
    void getLearningPlanTodayPack(planId, timezone, packOffset, controller.signal)
      .then((response) => {
        setWorkspace(requireApiData(response, resources.todayPack.packLoadFailed));
      })
      .catch((nextError) => {
        if (!controller.signal.aborted) {
          if (
            nextError instanceof ApiRequestError
            && nextError.code === LEARNING_PLAN_ACTIVE_SELECTION_MISMATCH_CODE
          ) {
            onNavigate(learningPlanDetailPath(planId), { replace: true });
            return;
          }
          setError(nextError instanceof Error ? nextError.message : resources.todayPack.packLoadFailed);
        }
      })
      .finally(() => {
        if (!controller.signal.aborted) {
          setLoading(false);
        }
    });
    return () => controller.abort();
  }, [locale, onNavigate, packOffset, planId, resources.todayPack.packLoadFailed, timezone]);

  async function restartActivePack() {
    if (!pack?.activePlan) {
      return;
    }
    if (!window.confirm(resources.todayPack.resetConfirm)) {
      return;
    }
    setActionLoading(true);
    setError('');
    try {
      const nextPack = requireApiData(
        await restartTodayPack(pack.activePlan.planId, timezone),
        resources.todayPack.packResetFailed,
      );
      setWorkspace((current) => current ? { ...current, pack: nextPack } : current);
      setPackOffset(0);
    } catch (nextError) {
      setError(nextError instanceof Error ? nextError.message : resources.todayPack.packResetFailed);
    } finally {
      setActionLoading(false);
    }
  }

  function openProblem(problem: TodayPackProblemResponse) {
    onNavigate(`${learningPlanPracticeChatPath(problem.planId, problem.phaseIndex, problem.slug)}?pack=today`);
  }

  function openRhythmDialog() {
    setRhythmDailyProblemCount(rhythmSettings?.dailyProblemCount ?? 1);
    setRhythmTrainingDaysPerWeek(rhythmSettings?.trainingDaysPerWeek ?? 5);
    setRhythmError('');
    setRhythmDialogOpen(true);
  }

  async function saveRhythm() {
    setRhythmUpdating(true);
    setRhythmError('');
    try {
      setWorkspace(requireApiData(
        await updateLearningPlanTodayPackRhythm(planId, {
          dailyProblemCount: rhythmDailyProblemCount,
          trainingDaysPerWeek: rhythmTrainingDaysPerWeek,
        }, timezone, packOffset),
        resources.learningPlans.rhythmUpdateFailed,
      ));
      setRhythmDialogOpen(false);
    } catch (nextError) {
      setRhythmError(nextError instanceof Error ? nextError.message : resources.learningPlans.rhythmUpdateFailed);
    } finally {
      setRhythmUpdating(false);
    }
  }

  if (loading && !pack) {
    return (
      <article className="learning-panel today-pack-page" aria-busy="true">
        <p className="eyebrow">TODAY PACK</p>
        <h2>{resources.todayPack.loadingPack}</h2>
      </article>
    );
  }

  return (
    <article className="learning-panel today-pack-page" aria-label={resources.todayPack.packAriaLabel}>
      <div className="today-pack-heading">
        <div>
          <p className="eyebrow">TODAY PACK</p>
          <h2>{resources.todayPack.todayPack}</h2>
          <p>
            {pack?.activePlan
              ? resources.todayPack.activePackSummary(
                pack.activePlan.title,
                formatDate(pack.activePlan.activatedAt, locale),
                pack.activePlan.dailyProblemCount,
                pack.localDate,
              )
              : resources.todayPack.packIntroduction}
          </p>
        </div>
        {pack?.activePlan && (
          <div className="today-pack-restart-action">
            <button
              className="secondary-button compact"
              disabled={actionLoading}
              onClick={() => void restartActivePack()}
              type="button"
            >
              <RotateCcw aria-hidden="true" />
              <span>{resources.todayPack.restart}</span>
            </button>
            <span className="toolbar-tooltip-wrap">
              <span
                aria-describedby="today-pack-restart-tooltip"
                aria-label={resources.todayPack.restartHelpAriaLabel}
                className="icon-button today-pack-restart-help"
                role="img"
                tabIndex={0}
              >
                <CircleHelp aria-hidden="true" />
              </span>
              <span className="toolbar-tooltip today-pack-restart-tooltip" id="today-pack-restart-tooltip" role="tooltip">
                {resources.todayPack.restartHelp}
              </span>
            </span>
          </div>
        )}
      </div>

      {error && <p className="error-text" role="alert">{error}</p>}

      {contract && (
        <section className={`living-contract-panel ${contract.visibleStatus.toLowerCase().replace('_', '-')}`}>
          <div className="living-contract-main">
            <div>
              <span>{resources.learningPlans.routeProgressTitle}</span>
              <strong>
                {resources.learningPlans.routeProgressLine(
                  contract.completedProblemCount,
                  contract.totalProblemCount,
                  contract.progressPercent,
                )}
              </strong>
            </div>
            <div>
              <span>{resources.learningPlans.visibleStatusLabels[contract.visibleStatus]}</span>
              <strong>
                {contract.estimatedCompletionDate
                  ? resources.learningPlans.estimatedCompletionDate(contract.estimatedCompletionDate)
                  : resources.learningPlans.unspecified}
              </strong>
            </div>
            <div>
              <span>{resources.learningPlans.openProblemsLine(contract.openProblemCount, contract.skippedProblemCount)}</span>
              <strong>{rhythmSettings ? resources.learningPlans.remainingWeeksLine(rhythmSettings.estimatedRemainingWeeks) : '-'}</strong>
            </div>
          </div>
          {contract.notice && (
            <p className="living-contract-notice">{contract.notice}</p>
          )}
          <button className="secondary-button compact" onClick={openRhythmDialog} type="button">
            <SlidersHorizontal aria-hidden="true" />
            <span>{resources.learningPlans.adjustRhythm}</span>
          </button>
          {contract.completionSummary && (
            <div className="completion-summary-panel">
              <strong>{resources.learningPlans.completionSummaryTitle}</strong>
              <span>
                {resources.learningPlans.completionSummaryLine(
                  contract.completionSummary.completionRate,
                  contract.completionSummary.totalDurationDays,
                  contract.completionSummary.completedProblemCount,
                  contract.completionSummary.skippedProblemCount,
                  contract.completionSummary.openProblemCount,
                )}
              </span>
              {contract.completionSummary.weakTags.length > 0 && (
                <span>
                  {resources.learningPlans.weakTagsLabel}：{contract.completionSummary.weakTags.join(' / ')}
                </span>
              )}
            </div>
          )}
        </section>
      )}

      {pace && (
        <section className={`pace-summary-panel ${pace.status.toLowerCase().replace('_', '-')}`}>
          <div>
            <span>{resources.learningPlans.paceTitle}</span>
            <strong>{resources.learningPlans.paceCurrentWeek(pace.currentWeek, pace.totalWeeks)}</strong>
          </div>
          <div>
            <span>{resources.learningPlans.paceStatusLabels[pace.status as LearningPlanPaceStatus]}</span>
            <strong>
              {resources.learningPlans.paceCurrentTarget(
                pace.currentBucket?.plannedProblemCount ?? 0,
                pace.currentBucket?.plannedLoadPoints ?? 0,
              )}
            </strong>
          </div>
          <div>
            <span>{resources.learningPlans.paceCurrentCompleted(pace.currentWeekCompletedProblemCount)}</span>
            <strong>{resources.learningPlans.paceLoadGap(pace.loadGapPoints)}</strong>
          </div>
          {pace.recommendation && <p>{pace.recommendation}</p>}
        </section>
      )}

      {pack?.state === 'NO_ACTIVE_PLAN' ? (
        <section className="today-pack-empty">
          <ClipboardList aria-hidden="true" />
          <div>
            <h3>{resources.todayPack.emptyPlanTitle}</h3>
            <p>{resources.todayPack.emptyPlanDescription}</p>
          </div>
        </section>
      ) : null}

      {pack?.state === 'PLAN_COMPLETED' ? (
        <section className="today-pack-complete">
          <CheckCircle2 aria-hidden="true" />
          <h3>{resources.todayPack.planCompletedTitle}</h3>
          <p>{resources.todayPack.planCompletedDescription}</p>
        </section>
      ) : null}

      {pack?.state === 'DONE_TODAY' ? (
        <section className="today-pack-complete">
          <CheckCircle2 aria-hidden="true" />
          <h3>{resources.todayPack.doneTodayTitle}</h3>
          <p>{resources.todayPack.doneTodayDescription}</p>
          <div className="today-pack-actions">
            <button className="secondary-button compact" type="button">
              {resources.todayPack.stopToday}
            </button>
            {pack.nextPackDate && (
              <button className="primary-button compact" onClick={() => setPackOffset((current) => current + 1)} type="button">
                {resources.todayPack.nextPack}
              </button>
            )}
          </div>
        </section>
      ) : null}

      {pack?.notice && <p className="today-pack-notice">{pack.notice}</p>}

      {pack && pack.packOffset > 0 && (
        <div className="today-pack-future-label">
          <CalendarDays aria-hidden="true" />
          <span>{resources.todayPack.futurePack(pack.sections[0]?.date ?? pack.nextPackDate ?? pack.localDate)}</span>
        </div>
      )}

      {pack?.sections.map((section) => (
        <section className="today-pack-section" key={`${section.type}-${section.date ?? 'due'}`}>
          <div className="plan-subsection-heading">
            <h3>{section.title}</h3>
            <span>{resources.todayPack.sectionProblemCount(section.problems.length)}</span>
          </div>
          <div className="today-pack-problem-list">
            {section.problems.map((problem) => (
              <button
                className="today-pack-problem-row"
                key={`${problem.planId}-${problem.phaseIndex}-${problem.slug}`}
                onClick={() => openProblem(problem)}
                type="button"
              >
                <span className="problem-id">{problem.frontendId ?? '-'}</span>
                <span className="problem-title">
                  <strong>{formatProblemTitle(problem, locale)}</strong>
                  <small>
                    {problem.carryoverDays > 0
                      ? resources.todayPack.carryover(problem.carryoverDays, problem.scheduledDate)
                      : resources.todayPack.scheduledDate(problem.scheduledDate)}
                  </small>
                </span>
                <span className={`difficulty-badge ${String(problem.difficulty ?? '').toLowerCase()}`}>
                  {formatDifficulty(problem.difficulty, resources)}
                </span>
                <ChevronRight aria-hidden="true" />
              </button>
            ))}
          </div>
        </section>
      ))}

      {pack?.state === 'READY' && pack.sections.length === 0 && (
        <section className="today-pack-complete">
          <CalendarDays aria-hidden="true" />
          <h3>{resources.todayPack.emptyDay}</h3>
          <button className="primary-button compact" onClick={() => setPackOffset((current) => current + 1)} type="button">
            {resources.todayPack.nextPack}
          </button>
        </section>
      )}

      {pack?.activePlan && (
        <footer className="today-pack-footer">
          <span>{resources.todayPack.packTotal(totalProblems)}</span>
          <span>{pack.timezone}</span>
          {packOffset > 0 && (
            <button className="secondary-button compact" onClick={() => setPackOffset(0)} type="button">
              {resources.todayPack.backToday}
            </button>
          )}
        </footer>
      )}

      {rhythmDialogOpen && (
        <div className="modal-backdrop" role="presentation">
          <section aria-label={resources.learningPlans.adjustRhythm} className="rhythm-dialog" role="dialog">
            <div className="modal-heading">
              <div>
                <p className="eyebrow">{resources.learningPlans.templateRhythm}</p>
                <h2>{resources.learningPlans.adjustRhythm}</h2>
              </div>
            </div>
            {rhythmError && <p className="error-text" role="alert">{rhythmError}</p>}
            <div className="rhythm-standard-reference">
              <span>{resources.learningPlans.standardRhythmTitle}</span>
              <strong>
                {resources.learningPlans.standardRhythmMainLine(
                  standardRhythm.dailyProblemCount,
                  standardRhythm.trainingDaysPerWeek,
                  standardRhythm.recommendedWeeks,
                )}
              </strong>
              <small>
                {resources.learningPlans.standardRhythmRemainingLine(
                  rhythmSettings?.remainingProblemCount ?? 0,
                  standardRemainingWeeks,
                )}
              </small>
            </div>
            <div className="rhythm-compare-grid">
              <div>
                <span>{resources.learningPlans.currentRhythm}</span>
                <strong>
                  {resources.learningPlans.rhythmConfigLine(
                    rhythmSettings?.dailyProblemCount ?? 1,
                    rhythmSettings?.trainingDaysPerWeek ?? 5,
                  )}
                </strong>
                <small>{resources.learningPlans.remainingWeeksLine(rhythmSettings?.estimatedRemainingWeeks ?? 0)}</small>
              </div>
              <div>
                <span>{resources.learningPlans.adjustedRhythm}</span>
                <strong>{resources.learningPlans.rhythmConfigLine(
                  rhythmDailyProblemCount,
                  rhythmTrainingDaysPerWeek,
                )}</strong>
                <small>{resources.learningPlans.remainingWeeksLine(adjustedRemainingWeeks)}</small>
                <small className="rhythm-standard-delta">
                  {adjustedStandardDelta < 0
                    ? resources.learningPlans.rhythmFasterThanStandard(Math.abs(adjustedStandardDelta))
                    : adjustedStandardDelta > 0
                      ? resources.learningPlans.rhythmSlowerThanStandard(adjustedStandardDelta)
                      : resources.learningPlans.rhythmSameAsStandard}
                </small>
              </div>
            </div>
            <div className="rhythm-stepper-grid">
              <label>
                <span>{resources.learningPlans.dailyProblemCount}</span>
                <input
                  aria-label={resources.learningPlans.dailyProblemCount}
                  disabled={rhythmUpdating}
                  max={10}
                  min={1}
                  onChange={(event) => setRhythmDailyProblemCount(clampNumber(event.target.valueAsNumber, 1, 10))}
                  type="number"
                  value={rhythmDailyProblemCount}
                />
              </label>
              <label>
                <span>{resources.learningPlans.trainingDaysPerWeek}</span>
                <input
                  aria-label={resources.learningPlans.trainingDaysPerWeek}
                  disabled={rhythmUpdating}
                  max={7}
                  min={1}
                  onChange={(event) => setRhythmTrainingDaysPerWeek(clampNumber(event.target.valueAsNumber, 1, 7))}
                  type="number"
                  value={rhythmTrainingDaysPerWeek}
                />
              </label>
            </div>
            <div className="modal-actions">
              <button
                className="secondary-button"
                disabled={rhythmUpdating}
                onClick={() => setRhythmDialogOpen(false)}
                type="button"
              >
                {resources.common.cancel}
              </button>
              <button className="primary-button" disabled={rhythmUpdating} onClick={() => void saveRhythm()} type="button">
                {rhythmUpdating ? resources.learningPlans.savingRhythm : resources.learningPlans.saveRhythm}
              </button>
            </div>
          </section>
        </div>
      )}
    </article>
  );
}

function todayPackHomeStatusText(
  pack: TodayPackHomeSummaryResponse,
  resources: LocaleResources['todayPack'],
): string {
  if (pack.state === 'NO_ACTIVE_PLAN') {
    return resources.statusNoActivePlan;
  }
  if (pack.state === 'PLAN_COMPLETED') {
    return resources.statusPlanCompleted;
  }
  if (pack.state === 'DONE_TODAY') {
    return pack.nextPackDate ? resources.statusDoneWithNext(pack.nextPackDate) : resources.statusDone;
  }
  if (pack.dueProblemCount > 0) {
    return resources.statusDue(pack.dueProblemCount);
  }
  return resources.statusEmpty;
}

function clampNumber(value: number, min: number, max: number) {
  if (!Number.isFinite(value)) {
    return min;
  }
  return Math.max(min, Math.min(max, Math.trunc(value)));
}
