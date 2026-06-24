import { ArrowLeft } from 'lucide-react';
import { formatPlanIntent } from '../i18n/formatters';
import { useI18n } from '../i18n/I18nProvider';
import type { LearningPlanDetailResponse } from '../types/api';
import PlanPreview from './PlanPreview';

export default function LearningPlanDetail({
  onBack,
  onProblemSelect,
  plan,
}: {
  onBack: () => void;
  onProblemSelect: (phaseIndex: number, problemSlug: string) => void;
  plan: LearningPlanDetailResponse;
}) {
  const { resources } = useI18n();

  return (
    <article className="learning-panel">
      <button className="secondary-button compact detail-back-button" onClick={onBack} type="button">
        <ArrowLeft aria-hidden="true" />
        <span>{resources.learningPlans.backToList}</span>
      </button>
      <div className="detail-heading">
        <div>
          <p className="eyebrow">{formatPlanIntent(plan.intent, resources)}</p>
          <h2>{plan.title}</h2>
          <p>{plan.summary}</p>
        </div>
      </div>
      <PlanPreview onProblemSelect={onProblemSelect} plan={plan} />
    </article>
  );
}
