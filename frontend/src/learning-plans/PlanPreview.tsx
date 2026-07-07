import {
  formatDifficulty,
  formatPlanLevel,
  formatProblemTitle,
  formatTopicTag,
} from '../i18n/formatters';
import { useI18n } from '../i18n/I18nProvider';
import type {
  LearningPlanDetailProblemResponse,
  LearningPlanDraftPlan,
  LearningPlanLoadIntensity,
  PracticeProgressStatus,
} from '../types/api';
import {
  getPlanLoadSummary,
  getPlanNextTrainingPackage,
  getPlanRhythmMode,
  getPlanWeeklyBuckets,
  trainingDaysPerWeek,
} from './load';

type PlanPreviewProblem = LearningPlanDraftPlan['phases'][number]['problems'][number]
  | LearningPlanDetailProblemResponse;

interface IndexedProblem {
  phaseIndex: number;
  problem: PlanPreviewProblem;
}

function hasProgressStatus(problem: PlanPreviewProblem): problem is LearningPlanDetailProblemResponse {
  return 'progressStatus' in problem;
}

function formatProgressStatus(status: PracticeProgressStatus, resources: ReturnType<typeof useI18n>['resources']) {
  const labels: Record<PracticeProgressStatus, string> = {
    NOT_STARTED: resources.learningPlans.notStarted,
    IN_PROGRESS: resources.learningPlans.inProgress,
    COMPLETED: resources.learningPlans.completed,
    SKIPPED: resources.learningPlans.skipped,
  };
  return labels[status];
}

function formatLoadIntensity(intensity: string, resources: ReturnType<typeof useI18n>['resources']) {
  const labels = resources.learningPlans.loadIntensityLabels;
  return labels[intensity as LearningPlanLoadIntensity] ?? intensity;
}

function ProblemRowContent({
  problem,
}: {
  problem: PlanPreviewProblem;
}) {
  const { locale, resources } = useI18n();
  const progressStatus = hasProgressStatus(problem) ? problem.progressStatus : undefined;

  return (
    <>
      <span className="problem-id">{problem.frontendId ?? '-'}</span>
      <span className="problem-title">
        <strong>{formatProblemTitle(problem, locale)}</strong>
        <small>{problem.reason}</small>
      </span>
      <span className="problem-badge-group">
        <span className={`difficulty-badge ${String(problem.difficulty ?? '').toLowerCase()}`}>
          {formatDifficulty(problem.difficulty, resources)}
        </span>
        {progressStatus ? (
          <span className={`progress-status-badge ${progressStatus.toLowerCase().replace('_', '-')}`}>
            {formatProgressStatus(progressStatus, resources)}
          </span>
        ) : null}
      </span>
    </>
  );
}

function buildProblemIndex(plan: LearningPlanDraftPlan) {
  const problemIndex = new Map<string, IndexedProblem>();
  plan.phases.forEach((phase) => {
    phase.problems.forEach((problem) => {
      if (!problemIndex.has(problem.slug)) {
        problemIndex.set(problem.slug, {
          phaseIndex: phase.phaseIndex,
          problem,
        });
      }
    });
  });
  return problemIndex;
}

function ProblemRow({
  onProblemSelect,
  phaseIndex,
  problem,
}: {
  onProblemSelect?: (phaseIndex: number, problemSlug: string) => void;
  phaseIndex: number;
  problem: PlanPreviewProblem;
}) {
  return onProblemSelect ? (
    <button
      className="problem-row"
      onClick={() => onProblemSelect(phaseIndex, problem.slug)}
      type="button"
    >
      <ProblemRowContent problem={problem} />
    </button>
  ) : (
    <div className="problem-row">
      <ProblemRowContent problem={problem} />
    </div>
  );
}

function MissingProblemRow({
  slug,
}: {
  slug: string;
}) {
  const { resources } = useI18n();
  const fallbackProblem: PlanPreviewProblem = {
    slug,
    title: slug,
    tags: [],
    reason: resources.learningPlans.weeklyMissingProblem,
    sortOrder: 0,
  };
  return (
    <div className="problem-row unresolved-problem-row">
      <ProblemRowContent problem={fallbackProblem} />
    </div>
  );
}

export default function PlanPreview({
  onProblemSelect,
  plan,
}: {
  onProblemSelect?: (phaseIndex: number, problemSlug: string) => void;
  plan: LearningPlanDraftPlan;
}) {
  const { resources } = useI18n();
  const loadSummary = getPlanLoadSummary(plan);
  const weeklyBuckets = getPlanWeeklyBuckets(plan);
  const nextTrainingPackage = getPlanNextTrainingPackage(plan);
  const rhythmMode = getPlanRhythmMode(plan);
  const [trainingDaysMin] = trainingDaysPerWeek(rhythmMode);
  const problemIndex = buildProblemIndex(plan);

  return (
    <div className="plan-preview">
      <section className="execution-summary-strip" aria-label={resources.learningPlans.draftPreview}>
        <strong>
          {resources.learningPlans.executionSummary(
            plan.durationWeeks,
            trainingDaysMin,
            nextTrainingPackage?.estimatedMinutes ?? Math.ceil(plan.weeklyHours * 60 / trainingDaysMin),
          )}
        </strong>
        {loadSummary && (
          <span>
            {resources.learningPlans.planRouteSummary(
              loadSummary.plannedProblemCount,
              plan.durationWeeks,
              plan.weeklyHours,
              formatLoadIntensity(String(loadSummary.intensity), resources),
            )}
          </span>
        )}
      </section>
      <div className="summary-grid compact-summary">
        <article className="summary-card">
          <span>{resources.learningPlans.previewDuration}</span>
          <strong>{resources.common.week(plan.durationWeeks)}</strong>
        </article>
        <article className="summary-card">
          <span>{resources.learningPlans.previewLevel}</span>
          <strong>{formatPlanLevel(plan.level, resources)}</strong>
        </article>
        <article className="summary-card">
          <span>{resources.learningPlans.previewTime}</span>
          <strong>{resources.common.hoursPerWeek(plan.weeklyHours)}</strong>
        </article>
        {loadSummary && (
          <article className="summary-card">
            <span>{resources.learningPlans.loadSummary}</span>
            <strong>{formatLoadIntensity(String(loadSummary.intensity), resources)}</strong>
          </article>
        )}
      </div>
      {loadSummary && (
        <section className="load-summary-strip" aria-label={resources.learningPlans.loadSummary}>
          <strong>
            {resources.learningPlans.planRouteSummary(
              loadSummary.plannedProblemCount,
              plan.durationWeeks,
              plan.weeklyHours,
              formatLoadIntensity(String(loadSummary.intensity), resources),
            )}
          </strong>
          {loadSummary.suggestions[0] && <span>{loadSummary.suggestions[0]}</span>}
        </section>
      )}
      {nextTrainingPackage && (
        <section className="training-package-card" aria-label={resources.learningPlans.nextTrainingPackage}>
          <div className="plan-subsection-heading">
            <h2>{resources.learningPlans.nextTrainingPackage}</h2>
            <span>{resources.common.week(nextTrainingPackage.weekIndex)}</span>
          </div>
          <p>
            {resources.learningPlans.nextTrainingPackageLine(
              nextTrainingPackage.newProblemCount,
              nextTrainingPackage.estimatedMinutes,
            )}
          </p>
          <p>{resources.learningPlans.nextTrainingPackageReview(nextTrainingPackage.reviewTask)}</p>
          {nextTrainingPackage.priorityProblemSlugs.length > 0 && (
            <>
              <strong className="training-package-priority-title">
                {resources.learningPlans.nextTrainingPackagePriority}
              </strong>
              <div className="problem-list compact-problems weekly-problems">
                {nextTrainingPackage.priorityProblemSlugs.map((slug, index) => {
                  const indexedProblem = problemIndex.get(slug);
                  return indexedProblem ? (
                    <ProblemRow
                      key={`${slug}-${index}`}
                      onProblemSelect={onProblemSelect}
                      phaseIndex={indexedProblem.phaseIndex}
                      problem={indexedProblem.problem}
                    />
                  ) : (
                    <MissingProblemRow key={`${slug}-${index}`} slug={slug} />
                  );
                })}
              </div>
            </>
          )}
        </section>
      )}
      {weeklyBuckets.length > 0 && (
        <section className="weekly-plan-section" aria-label={resources.learningPlans.weeklyPlan}>
          <div className="plan-subsection-heading">
            <h2>{resources.learningPlans.weeklyPlan}</h2>
          </div>
          {weeklyBuckets.map((bucket) => (
            <article className="weekly-plan-card" key={bucket.weekIndex}>
              <div className="weekly-plan-heading">
                <h3>{resources.learningPlans.weeklyPlanTitle(bucket.weekIndex, bucket.title)}</h3>
                <span>
                  {resources.learningPlans.weeklyBucketStats(
                    bucket.plannedProblemCount,
                    bucket.plannedLoadPoints,
                  )}
                </span>
              </div>
              {bucket.reviewAdvice && (
                <p className="weekly-review-advice">
                  {resources.learningPlans.weeklyReviewAdvice(bucket.reviewAdvice)}
                </p>
              )}
              {bucket.problemSlugs.length > 0 ? (
                <div className="problem-list compact-problems weekly-problems">
                  {bucket.problemSlugs.map((slug, index) => {
                    const indexedProblem = problemIndex.get(slug);
                    return indexedProblem ? (
                      <ProblemRow
                        key={`${bucket.weekIndex}-${slug}-${index}`}
                        onProblemSelect={onProblemSelect}
                        phaseIndex={indexedProblem.phaseIndex}
                        problem={indexedProblem.problem}
                      />
                    ) : (
                      <MissingProblemRow key={`${bucket.weekIndex}-${slug}-${index}`} slug={slug} />
                    );
                  })}
                </div>
              ) : (
                <p className="weekly-empty-note">{resources.learningPlans.weeklyReviewBuffer}</p>
              )}
            </article>
          ))}
        </section>
      )}
      <section className="phase-detail-section" aria-label={resources.learningPlans.phaseDetails}>
        <div className="plan-subsection-heading">
          <h2>{resources.learningPlans.phaseDetails}</h2>
        </div>
        {plan.phases.map((phase) => (
          <section className="phase-block" key={phase.phaseIndex}>
            <div className="phase-heading">
              <h3>{phase.title}</h3>
              <span>{resources.common.week(phase.durationWeeks)}</span>
            </div>
            <p>{phase.focus}</p>
            <div className="tag-row">
              {phase.recommendedTags.map((tag) => (
                <span className="tag-pill" key={tag}>{formatTopicTag(tag, resources)}</span>
              ))}
            </div>
            <div className="problem-list compact-problems">
              {phase.problems.map((problem) => (
                <ProblemRow
                  key={problem.slug}
                  onProblemSelect={onProblemSelect}
                  phaseIndex={phase.phaseIndex}
                  problem={problem}
                />
              ))}
            </div>
          </section>
        ))}
      </section>
    </div>
  );
}
