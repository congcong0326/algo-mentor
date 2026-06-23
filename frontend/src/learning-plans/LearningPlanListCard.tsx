import { Eye, Trash2 } from 'lucide-react';
import type { LearningPlanPageResponse } from '../types/api';

interface LearningPlanListCardProps {
  page: LearningPlanPageResponse;
  selectedPlanId?: number;
  deletingPlanId?: number;
  onSelect?: (planId: number) => void;
  onDelete: (planId: number) => void;
  onPageChange: (page: number) => void;
}

function getTotalPages(total: number, pageSize: number): number {
  return Math.max(1, Math.ceil(total / Math.max(1, pageSize)));
}

export default function LearningPlanListCard({
  page,
  selectedPlanId,
  deletingPlanId,
  onSelect,
  onDelete,
  onPageChange,
}: LearningPlanListCardProps) {
  const totalPages = getTotalPages(page.total, page.pageSize);

  return (
    <article className="learning-panel plan-list-card">
      <div className="panel-title compact-title">
        <h2>计划列表</h2>
        <span>第 {page.page} / {totalPages} 页</span>
      </div>
      <div className="plan-list">
        {page.items.length === 0 ? (
          <p className="empty-log">暂无正式计划，先新建一个学习计划。</p>
        ) : page.items.map((plan) => {
          const isDeleting = deletingPlanId === plan.id;
          const meta = `${plan.durationWeeks} 周 · ${plan.weeklyHours} 小时/周 · ${plan.intent} · ${plan.level}`;

          return (
            <div
              className={`plan-list-row ${selectedPlanId === plan.id ? 'selected' : ''}`}
              data-testid={`learning-plan-row-${plan.id}`}
              key={plan.id}
            >
              <div
                aria-current={selectedPlanId === plan.id ? 'true' : undefined}
                className="plan-row-main"
              >
                <strong>{plan.title}</strong>
                <span>{plan.goal}</span>
                <small>{meta}</small>
              </div>
              <div className="plan-row-actions">
                <span className="status-badge">{plan.status}</span>
                {onSelect && (
                  <button
                    aria-label={`查看 ${plan.title}`}
                    className="secondary-button compact"
                    onClick={() => onSelect(plan.id)}
                    type="button"
                  >
                    <Eye aria-hidden="true" />
                    <span>查看</span>
                  </button>
                )}
                <button
                  aria-label={`删除 ${plan.title}`}
                  className="danger-button compact"
                  disabled={isDeleting}
                  onClick={() => onDelete(plan.id)}
                  type="button"
                >
                  <Trash2 aria-hidden="true" />
                  <span>{isDeleting ? '删除中' : '删除'}</span>
                </button>
              </div>
            </div>
          );
        })}
      </div>
      <div className="pagination-row">
        <button
          className="secondary-button compact"
          disabled={page.page <= 1}
          onClick={() => onPageChange(page.page - 1)}
          type="button"
        >
          上一页
        </button>
        <button
          className="secondary-button compact"
          disabled={page.page >= totalPages}
          onClick={() => onPageChange(page.page + 1)}
          type="button"
        >
          下一页
        </button>
      </div>
    </article>
  );
}
