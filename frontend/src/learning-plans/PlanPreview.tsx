import {
  formatDifficulty,
  formatProblemTitle,
  formatTopicTag,
} from '../i18n/formatters';
import { useI18n } from '../i18n/I18nProvider';
import type {
  LearningPlanDetailProblemResponse,
  LearningPlanDraftPlan,
  PracticeProgressStatus,
} from '../types/api';
import {
  getPlanNextTrainingPackage,
  getPlanRhythmSettings,
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

function ProblemRowContent({
  problem,
  todayPack,
}: {
  problem: PlanPreviewProblem;
  todayPack?: boolean;
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
        {todayPack ? <span className="status-badge today-pack-match-badge">此题在今日题包中</span> : null}
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
  todayPack,
}: {
  onProblemSelect?: (phaseIndex: number, problemSlug: string) => void;
  phaseIndex: number;
  problem: PlanPreviewProblem;
  todayPack?: boolean;
}) {
  return onProblemSelect ? (
    <button
      className="problem-row"
      onClick={() => onProblemSelect(phaseIndex, problem.slug)}
      type="button"
    >
      <ProblemRowContent problem={problem} todayPack={todayPack} />
    </button>
  ) : (
    <div className="problem-row">
      <ProblemRowContent problem={problem} todayPack={todayPack} />
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

interface PlanPreviewProps {
  onProblemSelect?: (phaseIndex: number, problemSlug: string) => void;
  plan: LearningPlanDraftPlan;
  todayPackProblemKeys?: ReadonlySet<string>;
}

export function PlanPackageOverview({
  onProblemSelect,
  plan,
  todayPackProblemKeys,
}: PlanPreviewProps) {
  const { resources } = useI18n();
  const nextTrainingPackage = getPlanNextTrainingPackage(plan);
  const rhythmSettings = getPlanRhythmSettings(plan);
  const problemIndex = buildProblemIndex(plan);

  return (
    <div className="plan-package-overview">
      <section className="execution-summary-strip" aria-label={resources.learningPlans.draftPreview}>
        <strong>
          {resources.learningPlans.rhythmConfigLine(
            rhythmSettings.dailyProblemCount,
            rhythmSettings.trainingDaysPerWeek,
          )}
        </strong>
        <span>{resources.learningPlans.totalProblemCountLine(rhythmSettings.totalProblemCount)}</span>
        <span>{resources.learningPlans.remainingWeeksLine(rhythmSettings.estimatedRemainingWeeks)}</span>
      </section>
      {nextTrainingPackage && (
        <section className="training-package-card" aria-label={resources.learningPlans.nextTrainingPackage}>
          <div className="plan-subsection-heading">
            <h2>{resources.learningPlans.nextTrainingPackage}</h2>
            <span>{resources.learningPlans.remainingWeeksLine(rhythmSettings.estimatedRemainingWeeks)}</span>
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
                      todayPack={todayPackProblemKeys?.has(problemKey(plan, indexedProblem.phaseIndex, slug))}
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
    </div>
  );
}

export function PlanPhaseDetails({
  onProblemSelect,
  plan,
  todayPackProblemKeys,
}: PlanPreviewProps) {
  const { resources } = useI18n();

  return (
    <section className="phase-detail-section" aria-label={resources.learningPlans.phaseDetails}>
      <div className="plan-subsection-heading">
        <h2>{resources.learningPlans.phaseDetails}</h2>
      </div>
      {plan.phases.map((phase) => (
        <section className="phase-block" key={phase.phaseIndex}>
          <div className="phase-heading">
            <h3>{phase.title}</h3>
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
                todayPack={todayPackProblemKeys?.has(problemKey(plan, phase.phaseIndex, problem.slug))}
              />
            ))}
          </div>
        </section>
      ))}
    </section>
  );
}

export default function PlanPreview({
  onProblemSelect,
  plan,
  todayPackProblemKeys,
}: PlanPreviewProps) {
  return (
    <div className="plan-preview">
      <PlanPackageOverview
        onProblemSelect={onProblemSelect}
        plan={plan}
        todayPackProblemKeys={todayPackProblemKeys}
      />
      <PlanPhaseDetails
        onProblemSelect={onProblemSelect}
        plan={plan}
        todayPackProblemKeys={todayPackProblemKeys}
      />
    </div>
  );
}

function problemKey(plan: LearningPlanDraftPlan & { id?: number }, phaseIndex: number, slug: string) {
  return 'id' in plan ? `${plan.id}:${phaseIndex}:${slug}` : `${phaseIndex}:${slug}`;
}
