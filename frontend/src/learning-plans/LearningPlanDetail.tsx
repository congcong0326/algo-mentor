import type { LearningPlanDetailResponse } from '../types/api';
import PlanPreview from './PlanPreview';

export default function LearningPlanDetail({ plan }: { plan: LearningPlanDetailResponse }) {
  return (
    <article className="learning-panel">
      <div className="detail-heading">
        <div>
          <p className="eyebrow">{plan.intent}</p>
          <h2>{plan.title}</h2>
          <p>{plan.summary}</p>
        </div>
        <span className="status-badge">{plan.status}</span>
      </div>
      <PlanPreview plan={plan} />
    </article>
  );
}
