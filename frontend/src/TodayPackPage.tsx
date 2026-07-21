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
import AbilityRadarChart from './ability/AbilityRadarChart';
import {
  defaultAbilityTagKeys,
  findBreakthroughTag,
  formatAbilityScore,
  summarizeAbilityProfile,
} from './ability/abilityProfile';
import { APP_ROUTES, learningPlanPracticeChatPath, learningPlanTodayPackPath } from './app/navigation';
import { formatDate, formatDifficulty, formatProblemTitle } from './i18n/formatters';
import { useI18n } from './i18n/I18nProvider';
import {
  buildStandardRhythmReference,
  compareRhythmWeeks,
  estimateRhythmWeeks,
} from './learning-plans/learningPlanRhythm';
import {
  getTodayPack,
  getAbilityProfile,
  getReviewSummary,
  requireApiData,
  updateLearningPlanRhythm,
  restartTodayPack,
} from './services/api';
import type {
  LearningPlanDetailResponse,
  LearningPlanPaceStatus,
  AbilityProfileResponse,
  TodayPackProblemResponse,
  TodayPackResponse,
} from './types/api';

interface TodayPackPageProps {
  onNavigate: (pathname: string) => void;
}

interface TodayPackPanelProps {
  contractFeedback?: string;
  onNavigate: (pathname: string) => void;
  onPlanUpdated: () => Promise<void>;
  plan: LearningPlanDetailResponse;
}

function browserTimezone() {
  return Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC';
}

export default function TodayPackPage({ onNavigate }: TodayPackPageProps) {
  const { locale, resources } = useI18n();
  const [timezone] = useState(browserTimezone);
  const [pack, setPack] = useState<TodayPackResponse>();
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [reviewDueCount, setReviewDueCount] = useState<number>();
  const [reviewLoading, setReviewLoading] = useState(true);
  const [reviewUnavailable, setReviewUnavailable] = useState(false);
  const [abilityProfile, setAbilityProfile] = useState<AbilityProfileResponse>();
  const [abilityLoading, setAbilityLoading] = useState(true);
  const [abilityUnavailable, setAbilityUnavailable] = useState(false);
  const totalProblems = useMemo(
    () => pack?.sections.reduce((total, section) => total + section.problems.length, 0) ?? 0,
    [pack],
  );

  useEffect(() => {
    const controller = new AbortController();
    setLoading(true);
    setError('');
    void getTodayPack(timezone, 0, controller.signal)
      .then((response) => {
        setPack(requireApiData(response, '首页入口加载失败'));
      })
      .catch((nextError) => {
        if (!controller.signal.aborted) {
          setError(nextError instanceof Error ? nextError.message : '首页入口加载失败');
        }
      })
      .finally(() => {
        if (!controller.signal.aborted) {
          setLoading(false);
        }
      });
    return () => controller.abort();
  }, [timezone]);

  useEffect(() => {
    const controller = new AbortController();
    setReviewLoading(true);
    setReviewUnavailable(false);
    void getReviewSummary(controller.signal)
      .then((response) => {
        setReviewDueCount(requireApiData(response, '复习摘要加载失败').dueCount);
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
    return () => controller.abort();
  }, []);

  useEffect(() => {
    const controller = new AbortController();
    setAbilityLoading(true);
    setAbilityUnavailable(false);
    void getAbilityProfile(controller.signal)
      .then((response) => {
        setAbilityProfile(requireApiData(response, resources.home.abilityLoadFailed));
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

  const activePlan = pack?.activePlan;
  const statusText = pack
    ? todayPackStatusText(pack, totalProblems)
    : loading
      ? '正在加载'
      : '暂时无法读取今日训练状态。';
  const reviewStatusText = reviewLoading
    ? '正在加载复习状态'
    : reviewUnavailable
      ? '复习状态暂不可用'
      : reviewDueCount && reviewDueCount > 0
        ? `今日待复习 ${reviewDueCount} 题`
        : '今日已完成';
  const reviewActionLabel = reviewLoading
    ? '正在加载复习状态'
    : reviewUnavailable
      ? '复习状态暂不可用'
      : reviewDueCount && reviewDueCount > 0
        ? `开始今日复习 ${reviewDueCount} 题`
        : '今日已完成';
  const reviewActionDisabled = reviewLoading || reviewUnavailable || !reviewDueCount;
  const abilitySummary = summarizeAbilityProfile(abilityProfile);
  const breakthroughTag = findBreakthroughTag(abilityProfile, abilitySummary.strongestTag);
  const abilityRadarTags = abilityProfile
    ? defaultAbilityTagKeys(abilityProfile)
      .map((tag) => abilityProfile.tags.find((item) => item.tag === tag))
      .filter((tag): tag is AbilityProfileResponse['tags'][number] => Boolean(tag))
    : [];
  const dashboardDate = new Intl.DateTimeFormat(locale, {
    month: 'long',
    day: 'numeric',
    weekday: 'long',
  }).format(new Date(`${pack?.localDate ?? new Date().toISOString().slice(0, 10)}T00:00:00`));
  const weeklyTarget = activePlan ? activePlan.dailyProblemCount * activePlan.trainingDaysPerWeek : 0;

  return (
    <article className="today-pack-home" aria-label="首页">
      <header className="home-dashboard-heading">
        <div>
          <p className="eyebrow">{resources.home.workspaceKicker}</p>
          <h1>{resources.home.workspaceTitle}</h1>
          <p>{resources.home.workspaceSubtitle}</p>
        </div>
        <span className="home-dashboard-date">
          <CalendarDays aria-hidden="true" />
          {dashboardDate}
        </span>
      </header>

      {error && <p className="error-text" role="alert">{error}</p>}

      <div className="home-focus-grid">
        <section className="home-focus-panel training" aria-label="题包入口">
          <div className="home-focus-panel-topline">
            <span><Target aria-hidden="true" />{activePlan ? '今日题包' : '开始训练'}</span>
            {activePlan ? <small>{activePlan.title}</small> : null}
          </div>
          <div className="home-focus-copy">
            <strong>{statusText}</strong>
            <p>
              {activePlan
                ? `${activePlan.dailyProblemCount} 题/天 · 每周 ${activePlan.trainingDaysPerWeek} 天 · 计划剩余 ${activePlan.remainingProblemCount} 题`
                : '先采用一份学习方案，首页会按节奏整理每天最该完成的训练。'}
            </p>
          </div>
          {activePlan ? (
            <button
              className="primary-button"
              onClick={() => onNavigate(learningPlanTodayPackPath(activePlan.planId))}
              type="button"
            >
              <span>开始今日训练</span>
              <ArrowRight aria-hidden="true" />
            </button>
          ) : (
            <button
              className="primary-button"
              onClick={() => onNavigate(APP_ROUTES.learningPlans)}
              type="button"
            >
              <span>去方案页创建或采用一个</span>
              <ArrowRight aria-hidden="true" />
            </button>
          )}
        </section>

        <section className="home-focus-panel review" aria-busy={reviewLoading} aria-label="复习中心入口">
          <div className="home-focus-panel-topline">
            <span><Activity aria-hidden="true" />复习中心</span>
          </div>
          <div className="home-focus-copy">
            <strong>{reviewStatusText}</strong>
            <p>先复述、再评级，让错题按遗忘风险回到今天，而不是堆成一份静态清单。</p>
          </div>
          <button
            className="secondary-button"
            disabled={reviewActionDisabled}
            onClick={() => onNavigate(APP_ROUTES.reviewSession)}
            type="button"
          >
            <span>{reviewActionLabel}</span>
            <ArrowRight aria-hidden="true" />
          </button>
        </section>
      </div>

      <div className="home-dashboard-grid">
        <section className="home-ability-panel" aria-labelledby="home-ability-title">
          <div className="home-panel-heading">
            <div>
              <p className="eyebrow">ABILITY PROFILE</p>
              <h2 id="home-ability-title">学习诊断</h2>
              <p>把长期画像压缩成今天真正有用的判断。</p>
            </div>
            <button className="text-action-button" onClick={() => onNavigate(APP_ROUTES.my)} type="button">
              <span>查看完整画像</span>
              <ArrowRight aria-hidden="true" />
            </button>
          </div>
          {abilityLoading ? (
            <div className="home-panel-state" role="status">{resources.home.abilityLoading}</div>
          ) : abilityUnavailable ? (
            <div className="home-panel-state">能力画像暂不可用，今日训练入口不受影响。</div>
          ) : abilityProfile && abilityProfile.tags.length > 0 ? (
            <div className="home-ability-layout">
              <button
                aria-label="查看完整能力画像"
                className="home-ability-radar-button"
                onClick={() => onNavigate(APP_ROUTES.my)}
                type="button"
              >
                <AbilityRadarChart profile={abilityProfile} tags={abilityRadarTags} />
              </button>
              <div className="home-ability-insights">
                <div className="home-ability-stat-row">
                  <span>
                    <BrainCircuit aria-hidden="true" />
                    平均能力
                  </span>
                  <strong>{formatAbilityScore(abilitySummary.averageScore, locale)} / 10</strong>
                </div>
                <div className="home-insight-block strength">
                  <span><Trophy aria-hidden="true" />当前优势</span>
                  <strong>{abilitySummary.strongestTag?.label ?? '暂无'}</strong>
                  <p>
                    {abilitySummary.strongestTag
                      ? `已基于 ${abilitySummary.strongestTag.reviewedProblemCount} 道复盘题形成判断。`
                      : resources.myPage.noTopAbilities}
                  </p>
                </div>
                <div className="home-insight-block next">
                  <span><Target aria-hidden="true" />下一步突破</span>
                  <strong>{breakthroughTag?.label ?? '继续积累复盘数据'}</strong>
                  <p>{breakthroughTag ? `今天优先补一题“${breakthroughTag.label}”基础练习。` : resources.myPage.noTopAbilities}</p>
                </div>
              </div>
            </div>
          ) : (
            <div className="home-panel-state">{resources.home.abilityEmpty}</div>
          )}
        </section>

        <aside className="home-plan-panel" aria-labelledby="home-plan-title">
          <div className="home-panel-heading compact-heading">
            <div>
              <p className="eyebrow">CURRENT PLAN</p>
              <h2 id="home-plan-title">本周节奏</h2>
            </div>
          </div>
          {activePlan ? (
            <>
              <strong className="home-plan-title">{activePlan.title}</strong>
              <dl className="home-plan-metrics">
                <div>
                  <dt>每日训练</dt>
                  <dd>{activePlan.dailyProblemCount} 题</dd>
                </div>
                <div>
                  <dt>每周目标</dt>
                  <dd>{weeklyTarget} 题</dd>
                </div>
                <div>
                  <dt>剩余题目</dt>
                  <dd>{activePlan.remainingProblemCount} 题</dd>
                </div>
              </dl>
              <button className="secondary-button compact" onClick={() => onNavigate(APP_ROUTES.learningPlans)} type="button">
                <span>管理学习方案</span>
                <ArrowRight aria-hidden="true" />
              </button>
            </>
          ) : (
            <div className="home-empty-plan">
              <strong>{pack?.recommendedPlan?.title ?? '还没有进行中的学习方案'}</strong>
              <p>{pack?.recommendedPlan?.summary ?? '创建方案后，这里会显示每周训练节奏和剩余任务。'}</p>
              <button className="secondary-button compact" onClick={() => onNavigate(APP_ROUTES.learningPlans)} type="button">
                查看训练方案
              </button>
            </div>
          )}
        </aside>
      </div>
    </article>
  );
}

export function TodayPackPanel({ contractFeedback, onNavigate, onPlanUpdated, plan }: TodayPackPanelProps) {
  const { locale, resources } = useI18n();
  const [timezone] = useState(browserTimezone);
  const [packOffset, setPackOffset] = useState(0);
  const [pack, setPack] = useState<TodayPackResponse>();
  const [loading, setLoading] = useState(true);
  const [actionLoading, setActionLoading] = useState(false);
  const [error, setError] = useState('');
  const [rhythmDialogOpen, setRhythmDialogOpen] = useState(false);
  const [rhythmDailyProblemCount, setRhythmDailyProblemCount] = useState(plan.rhythmSettings?.dailyProblemCount ?? 1);
  const [rhythmTrainingDaysPerWeek, setRhythmTrainingDaysPerWeek] = useState(
    plan.rhythmSettings?.trainingDaysPerWeek ?? 5,
  );
  const [rhythmUpdating, setRhythmUpdating] = useState(false);
  const [rhythmError, setRhythmError] = useState('');
  const pace = plan.paceSummary;
  const contract = plan.livingContractSummary;
  const rhythmSettings = plan.rhythmSettings;
  const standardRhythm = buildStandardRhythmReference({
    recommendedWeeks: plan.durationWeeks,
    settings: undefined,
    totalProblemCount: rhythmSettings?.totalProblemCount ?? countPlanProblems(plan),
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
    void getTodayPack(timezone, packOffset, controller.signal)
      .then((response) => {
        setPack(requireApiData(response, '今日题包加载失败'));
      })
      .catch((nextError) => {
        if (!controller.signal.aborted) {
          setError(nextError instanceof Error ? nextError.message : '今日题包加载失败');
        }
      })
      .finally(() => {
        if (!controller.signal.aborted) {
          setLoading(false);
        }
      });
    return () => controller.abort();
  }, [packOffset, timezone]);

  async function restartActivePack() {
    if (!pack?.activePlan) {
      return;
    }
    if (!window.confirm('今日题包将从今天重新排布，已完成和已跳过题目不会被删除。')) {
      return;
    }
    setActionLoading(true);
    setError('');
    try {
      setPack(requireApiData(await restartTodayPack(pack.activePlan.planId, timezone), '今日题包重置失败'));
      setPackOffset(0);
    } catch (nextError) {
      setError(nextError instanceof Error ? nextError.message : '今日题包重置失败');
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
      requireApiData(
        await updateLearningPlanRhythm(plan.id, {
          dailyProblemCount: rhythmDailyProblemCount,
          trainingDaysPerWeek: rhythmTrainingDaysPerWeek,
        }),
        resources.learningPlans.rhythmUpdateFailed,
      );
      await onPlanUpdated();
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
        <h2>正在加载今日题包</h2>
      </article>
    );
  }

  return (
    <article className="learning-panel today-pack-page" aria-label="今日题包">
      <div className="today-pack-heading">
        <div>
          <p className="eyebrow">TODAY PACK</p>
          <h2>今日题包</h2>
          <p>
            {pack?.activePlan
              ? `${pack.activePlan.title} · 开始时间 ${formatDate(pack.activePlan.activatedAt, locale)} · ${pack.activePlan.dailyProblemCount} 题/天 · 今日 ${pack.localDate}`
              : '从一个推荐计划开始，之后可以在方案页自由切换。'}
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
              <span>一键清账重新开始</span>
            </button>
            <span className="toolbar-tooltip-wrap">
              <span
                aria-describedby="today-pack-restart-tooltip"
                aria-label="一键清账重新开始说明"
                className="icon-button today-pack-restart-help"
                role="img"
                tabIndex={0}
              >
                <CircleHelp aria-hidden="true" />
              </span>
              <span className="toolbar-tooltip today-pack-restart-tooltip" id="today-pack-restart-tooltip" role="tooltip">
                将题包起点重置为今天，清掉顺延积压；已完成和已跳过记录会保留。
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
          {(contract.notice || contractFeedback) && (
            <p className="living-contract-notice">{contractFeedback || contract.notice}</p>
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
            <h3>还没有采用的训练方案</h3>
            <p>回到方案页创建或采用一个方案后，今日题包会按计划节奏生成。</p>
          </div>
        </section>
      ) : null}

      {pack?.state === 'PLAN_COMPLETED' ? (
        <section className="today-pack-complete">
          <CheckCircle2 aria-hidden="true" />
          <h3>整条计划已清完</h3>
          <p>已完成或跳过当前采用计划中的所有题目。你仍然可以在方案页浏览历史题目和继续对话。</p>
        </section>
      ) : null}

      {pack?.state === 'DONE_TODAY' ? (
        <section className="today-pack-complete">
          <CheckCircle2 aria-hidden="true" />
          <h3>今天到此为止</h3>
          <p>当前没有需要补做或今天安排的新题。</p>
          <div className="today-pack-actions">
            <button className="secondary-button compact" type="button">
              到此为止
            </button>
            {pack.nextPackDate && (
              <button className="primary-button compact" onClick={() => setPackOffset((current) => current + 1)} type="button">
                再来一包
              </button>
            )}
          </div>
        </section>
      ) : null}

      {pack?.notice && <p className="today-pack-notice">{pack.notice}</p>}

      {pack && pack.packOffset > 0 && (
        <div className="today-pack-future-label">
          <CalendarDays aria-hidden="true" />
          <span>{pack.sections[0]?.date ?? pack.nextPackDate ?? pack.localDate} 的题包</span>
        </div>
      )}

      {pack?.sections.map((section) => (
        <section className="today-pack-section" key={`${section.type}-${section.date ?? 'due'}`}>
          <div className="plan-subsection-heading">
            <h3>{section.title}</h3>
            <span>{section.problems.length} 题</span>
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
                      ? `顺延 ${problem.carryoverDays} 天 · ${problem.scheduledDate}`
                      : `安排日期 ${problem.scheduledDate}`}
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
          <h3>这一天没有安排题目</h3>
          <button className="primary-button compact" onClick={() => setPackOffset((current) => current + 1)} type="button">
            再来一包
          </button>
        </section>
      )}

      {pack?.activePlan && (
        <footer className="today-pack-footer">
          <span>本包共 {totalProblems} 题</span>
          <span>{pack.timezone}</span>
          {packOffset > 0 && (
            <button className="secondary-button compact" onClick={() => setPackOffset(0)} type="button">
              回到今天
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

function todayPackStatusText(pack: TodayPackResponse, totalProblems: number): string {
  if (pack.state === 'NO_ACTIVE_PLAN') {
    return '还没有采用的训练方案';
  }
  if (pack.state === 'PLAN_COMPLETED') {
    return '整条计划已清完';
  }
  if (pack.state === 'DONE_TODAY') {
    return pack.nextPackDate ? `今天已完成，下一包 ${pack.nextPackDate}` : '今天已完成';
  }
  if (totalProblems > 0) {
    return `今日待练 ${totalProblems} 题`;
  }
  return '今日暂无安排';
}

function clampNumber(value: number, min: number, max: number) {
  if (!Number.isFinite(value)) {
    return min;
  }
  return Math.max(min, Math.min(max, Math.trunc(value)));
}

function countPlanProblems(plan: LearningPlanDetailResponse) {
  return plan.phases.reduce((total, phase) => total + phase.problems.length, 0);
}
