import { Plus } from 'lucide-react';

interface LearningPlanSummaryCardProps {
  total: number;
  activeCount: number;
  archivedCount: number;
  latestCreatedAt?: string | null;
  onCreate: () => void;
}

function formatDate(value?: string | null): string {
  if (!value) {
    return '暂无计划';
  }

  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return '暂无计划';
  }

  return date.toISOString().slice(0, 10);
}

export default function LearningPlanSummaryCard({
  total,
  activeCount,
  archivedCount,
  latestCreatedAt,
  onCreate,
}: LearningPlanSummaryCardProps) {
  return (
    <article className="learning-panel plan-summary-card">
      <div className="plan-summary-content">
        <div>
          <p className="eyebrow">学习计划</p>
          <h2>当前共有 {total} 个计划</h2>
        </div>
        <div className="summary-metric-row">
          <span>
            <strong>{activeCount}</strong>
            <small>进行中</small>
          </span>
          <span>
            <strong>{archivedCount}</strong>
            <small>已归档</small>
          </span>
          <span>
            <strong>{formatDate(latestCreatedAt)}</strong>
            <small>最近创建</small>
          </span>
        </div>
      </div>
      <button className="primary-button" onClick={onCreate} type="button">
        <Plus aria-hidden="true" />
        <span>新建计划</span>
      </button>
    </article>
  );
}
