import { Activity, CalendarClock, Eye, Layers3, Plus, Trash2 } from 'lucide-react';
import type {
  LearningPlanIntent,
  LearningPlanLevel,
  LearningPlanPageResponse,
  LearningPlanStatus,
} from '../types/api';
import { intentOptions, levelOptions, programmingLanguageOptions } from './options';

interface LearningPlanListCardProps {
  page: LearningPlanPageResponse;
  selectedPlanId?: number;
  deletingPlanId?: number;
  onSelect?: (planId: number) => void;
  onCreate: () => void;
  onDelete: (planId: number) => void;
  onPageChange: (page: number) => void;
}

function getTotalPages(total: number, pageSize: number): number {
  return Math.max(1, Math.ceil(total / Math.max(1, pageSize)));
}

const intentLabels = new Map<LearningPlanIntent, string>(intentOptions.map((option) => [option.value, option.label]));
const levelLabels = new Map<LearningPlanLevel, string>(levelOptions.map((option) => [option.value, option.label]));

const statusLabels: Record<LearningPlanStatus, string> = {
  ACTIVE: '进行中',
  ARCHIVED: '已归档',
};

function formatDate(value: string): string {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return '-';
  }

  const parts = new Intl.DateTimeFormat('zh-CN', {
    month: '2-digit',
    day: '2-digit',
  }).formatToParts(date).reduce<Record<string, string>>((result, part) => {
    result[part.type] = part.value;
    return result;
  }, {});

  return `${parts.month}-${parts.day}`;
}

function formatLatestDate(value?: string | null): string {
  if (!value) {
    return '暂无';
  }

  return formatDate(value);
}

function inferProgrammingLanguage(plan: { programmingLanguage?: string; title: string; goal: string }): string {
  if (plan.programmingLanguage?.trim()) {
    return plan.programmingLanguage.trim();
  }

  const searchable = `${plan.title} ${plan.goal}`.toLocaleLowerCase();
  return programmingLanguageOptions.find((language) => (
    language === 'C'
      ? /(^|[^a-z0-9+#])c($|[^a-z0-9+#])/.test(searchable)
      : searchable.includes(language.toLocaleLowerCase())
  )) ?? '未指定';
}

export default function LearningPlanListCard({
  page,
  selectedPlanId,
  deletingPlanId,
  onSelect,
  onCreate,
  onDelete,
  onPageChange,
}: LearningPlanListCardProps) {
  const totalPages = getTotalPages(page.total, page.pageSize);
  const visibleRangeStart = page.total === 0 ? 0 : (page.page - 1) * page.pageSize + 1;
  const visibleRangeEnd = Math.min(page.total, page.page * page.pageSize);

  return (
    <section className="plan-workspace" aria-label="训练方案工作台">
      <div className="plan-overview">
        <div className="plan-overview-copy">
          <p className="eyebrow">Learning Plans</p>
          <h2 className="plan-overview-title">训练方案</h2>
          <p>按目标、时间、当前水平与自身想法生成训练方案。</p>
        </div>
        <div className="plan-overview-actions">
          <button className="primary-button compact" onClick={onCreate} type="button">
            <Plus aria-hidden="true" />
            <span>新建方案</span>
          </button>
        </div>
        <dl className="plan-stat-grid" aria-label="方案概览">
          <div className="plan-stat-card">
            <dt>进行中</dt>
            <dd>{page.activeCount}</dd>
          </div>
          <div className="plan-stat-card">
            <dt>已归档</dt>
            <dd>{page.archivedCount}</dd>
          </div>
          <div className="plan-stat-card">
            <dt>最近创建</dt>
            <dd>{formatLatestDate(page.latestCreatedAt)}</dd>
          </div>
        </dl>
      </div>

      <div className="plan-dashboard-grid">
        <section className="plan-list-card" aria-label="方案列表">
          <div className="plan-section-heading">
            <div>
              <h2>方案库</h2>
              <p>共 {page.total} 个方案</p>
            </div>
            <span>{visibleRangeStart}-{visibleRangeEnd}</span>
          </div>
          <div className="plan-list">
            {page.items.length === 0 ? (
              <div className="empty-plan-state">
                <h3>暂无正式方案</h3>
                <p>先新建一个训练方案，把目标、周期和题目安排统一起来。</p>
              </div>
            ) : (
              <div className="plan-list-stack" role="list" aria-label="方案列表">
                {page.items.map((plan) => {
                  const isDeleting = deletingPlanId === plan.id;

                  return (
                    <article
                      aria-current={selectedPlanId === plan.id ? 'true' : undefined}
                      className={`plan-list-row ${selectedPlanId === plan.id ? 'selected' : ''}`}
                      data-testid={`learning-plan-row-${plan.id}`}
                      key={plan.id}
                      role="listitem"
                    >
                      <div className="plan-row-content">
                        <div className="plan-title-line">
                          <strong>{plan.title}</strong>
                          <span className="status-badge">{statusLabels[plan.status]}</span>
                        </div>
                        <p>{plan.goal}</p>
                        <div className="plan-meta-row" aria-label="方案参数">
                          <span>{inferProgrammingLanguage(plan)}</span>
                          <span>{levelLabels.get(plan.level) ?? plan.level}</span>
                          <span>{intentLabels.get(plan.intent) ?? plan.intent}</span>
                          <span>{plan.durationWeeks} 周</span>
                          <span>{plan.weeklyHours}h/周</span>
                          <span>{formatDate(plan.createdAt)} 创建</span>
                        </div>
                      </div>
                      <div className="plan-row-actions">
                        {onSelect && (
                          <button
                            aria-label={`查看 ${plan.title}`}
                            className="icon-button"
                            onClick={() => onSelect(plan.id)}
                            title="查看"
                            type="button"
                          >
                            <Eye aria-hidden="true" />
                          </button>
                        )}
                        <button
                          aria-label={`删除 ${plan.title}`}
                          className="icon-button danger-icon-button"
                          disabled={isDeleting}
                          onClick={() => onDelete(plan.id)}
                          title={isDeleting ? '删除中' : '删除'}
                          type="button"
                        >
                          <Trash2 aria-hidden="true" />
                        </button>
                      </div>
                    </article>
                  );
                })}
              </div>
            )}
          </div>
          <div className="pagination-row">
            <span>第 {page.page} / {totalPages} 页</span>
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
        </section>

        <aside className="plan-insight-panel" aria-label="方案状态">
          <div className="plan-section-heading compact-heading">
            <div>
              <h2>当前节奏</h2>
              <p>方案执行概览</p>
            </div>
          </div>
          <div className="plan-insight-list">
            <div className="plan-insight-item">
              <Activity aria-hidden="true" />
              <div>
                <strong>{page.total === 0 ? '还没有训练节奏' : `${page.activeCount} 个方案正在推进`}</strong>
                <span>{page.archivedCount} 个方案已沉淀为历史记录</span>
              </div>
            </div>
            <div className="plan-insight-item">
              <Layers3 aria-hidden="true" />
              <div>
                <strong>按场景维护方案</strong>
                <span>面试冲刺、专题突破和长期学习不要混在同一个方案里。</span>
              </div>
            </div>
            <div className="plan-insight-item">
              <CalendarClock aria-hidden="true" />
              <div>
                <strong>最近创建：{formatLatestDate(page.latestCreatedAt)}</strong>
                <span>新方案保存后会出现在方案库顶部。</span>
              </div>
            </div>
          </div>
        </aside>
      </div>
    </section>
  );
}
