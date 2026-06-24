import { ArrowLeft } from 'lucide-react';
import type { LearningPlanDetailResponse } from '../types/api';
import PlanPreview from './PlanPreview';

const statusLabels = {
  ACTIVE: '进行中',
  ARCHIVED: '已归档',
} as const;

export default function LearningPlanDetail({
  onBack,
  onProblemSelect,
  plan,
}: {
  onBack: () => void;
  onProblemSelect: (phaseIndex: number, problemSlug: string) => void;
  plan: LearningPlanDetailResponse;
}) {
  return (
    <article className="learning-panel">
      <button className="secondary-button compact detail-back-button" onClick={onBack} type="button">
        <ArrowLeft aria-hidden="true" />
        <span>返回方案库</span>
      </button>
      <div className="detail-heading">
        <div>
          <p className="eyebrow">{plan.intent}</p>
          <h2>{plan.title}</h2>
          <p>{plan.summary}</p>
        </div>
        <span className="status-badge">{statusLabels[plan.status]}</span>
      </div>
      <PlanPreview onProblemSelect={onProblemSelect} plan={plan} />
    </article>
  );
}
