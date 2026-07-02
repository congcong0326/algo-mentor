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
  PracticeProgressStatus,
} from '../types/api';

type PlanPreviewProblem = LearningPlanDraftPlan['phases'][number]['problems'][number]
  | LearningPlanDetailProblemResponse;

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

export default function PlanPreview({
  onProblemSelect,
  plan,
}: {
  onProblemSelect?: (phaseIndex: number, problemSlug: string) => void;
  plan: LearningPlanDraftPlan;
}) {
  const { resources } = useI18n();

  return (
    <div className="plan-preview">
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
              onProblemSelect ? (
                <button
                  className="problem-row"
                  key={problem.slug}
                  onClick={() => onProblemSelect(phase.phaseIndex, problem.slug)}
                  type="button"
                >
                  <ProblemRowContent problem={problem} />
                </button>
              ) : (
                <div
                  className="problem-row"
                  key={problem.slug}
                >
                  <ProblemRowContent problem={problem} />
                </div>
              )
            ))}
          </div>
        </section>
      ))}
    </div>
  );
}
