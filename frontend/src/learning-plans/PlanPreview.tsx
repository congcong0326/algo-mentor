import type { LearningPlanDraftPlan } from '../types/api';

export default function PlanPreview({ plan }: { plan: LearningPlanDraftPlan }) {
  return (
    <div className="plan-preview">
      <div className="summary-grid compact-summary">
        <article className="summary-card">
          <span>周期</span>
          <strong>{plan.durationWeeks} 周</strong>
        </article>
        <article className="summary-card">
          <span>水平</span>
          <strong>{plan.level}</strong>
        </article>
        <article className="summary-card">
          <span>时间</span>
          <strong>{plan.weeklyHours}h/周</strong>
        </article>
      </div>
      {plan.phases.map((phase) => (
        <section className="phase-block" key={phase.phaseIndex}>
          <div className="phase-heading">
            <h3>{phase.title}</h3>
            <span>{phase.durationWeeks} 周</span>
          </div>
          <p>{phase.focus}</p>
          <div className="tag-row">
            {phase.recommendedTags.map((tag) => <span className="tag-pill" key={tag}>{tag}</span>)}
          </div>
          <div className="problem-list compact-problems">
            {phase.problems.map((problem) => (
              <a
                className="problem-row"
                href={`/problems?keyword=${encodeURIComponent(problem.slug)}`}
                key={problem.slug}
              >
                <span className="problem-id">{problem.frontendId ?? '-'}</span>
                <span className="problem-title">
                  <strong>{problem.titleCn || problem.title}</strong>
                  <small>{problem.reason}</small>
                </span>
                <span className={`difficulty-badge ${String(problem.difficulty ?? '').toLowerCase()}`}>
                  {problem.difficulty ?? '-'}
                </span>
              </a>
            ))}
          </div>
        </section>
      ))}
    </div>
  );
}
