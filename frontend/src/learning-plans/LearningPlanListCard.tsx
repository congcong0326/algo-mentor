import { CalendarDays, Eye, Plus, Trash2 } from 'lucide-react';
import type { CSSProperties } from 'react';
import type {
  LearningPlanPageResponse,
} from '../types/api';
import {
  formatPlanIntent,
  formatPlanLevel,
  formatPlanStatus,
  formatShortDate,
} from '../i18n/formatters';
import { useI18n } from '../i18n/I18nProvider';
import type { SupportedLocale } from '../i18n/locales';
import { programmingLanguageOptions } from './options';

interface LearningPlanListCardProps {
  page: LearningPlanPageResponse;
  selectedPlanId?: number;
  deletingPlanId?: number;
  activatingPlanId?: number;
  onSelect?: (planId: number) => void;
  onOpenTodayPack?: (planId: number) => void;
  onActivate?: (planId: number) => void;
  onCreate: () => void;
  onDelete: (planId: number) => void;
  onPageChange: (page: number) => void;
}

function getTotalPages(total: number, pageSize: number): number {
  return Math.max(1, Math.ceil(total / Math.max(1, pageSize)));
}

function formatLatestDate(value: string | null | undefined, locale: SupportedLocale, empty: string): string {
  if (!value) {
    return empty;
  }

  return formatShortDate(value, locale);
}

function normalizeProgressPercent(value: number): number {
  if (!Number.isFinite(value)) {
    return 0;
  }
  return Math.min(100, Math.max(0, value));
}

type PlanProgressRingStyle = CSSProperties & {
  '--plan-progress-angle': string;
  '--plan-progress-color': string;
  '--plan-progress-track': string;
};

function getPlanProgressColor(percent: number): string {
  if (percent <= 0) {
    return '#B8BEC7';
  }
  if (percent <= 30) {
    return '#F59E0B';
  }
  if (percent <= 70) {
    return '#06B6D4';
  }
  if (percent < 100) {
    return '#10B981';
  }
  return '#059669';
}

function getPlanProgressRingStyle(percent: number): PlanProgressRingStyle {
  return {
    '--plan-progress-angle': `${percent * 3.6}deg`,
    '--plan-progress-color': getPlanProgressColor(percent),
    '--plan-progress-track': percent === 0 ? '#B8BEC7' : '#F1F3F5',
  };
}

function inferProgrammingLanguage(
  plan: { programmingLanguage?: string; title: string; objective: string },
  fallback: string,
): string {
  if (plan.programmingLanguage?.trim()) {
    return plan.programmingLanguage.trim();
  }

  const searchable = `${plan.title} ${plan.objective}`.toLocaleLowerCase();
  return programmingLanguageOptions.find((language) => (
    language === 'C'
      ? /(^|[^a-z0-9+#])c($|[^a-z0-9+#])/.test(searchable)
      : searchable.includes(language.toLocaleLowerCase())
  )) ?? fallback;
}

export default function LearningPlanListCard({
  page,
  selectedPlanId,
  deletingPlanId,
  activatingPlanId,
  onSelect,
  onOpenTodayPack,
  onActivate,
  onCreate,
  onDelete,
  onPageChange,
}: LearningPlanListCardProps) {
  const { locale, resources } = useI18n();
  const totalPages = getTotalPages(page.total, page.pageSize);
  const visibleRangeStart = page.total === 0 ? 0 : (page.page - 1) * page.pageSize + 1;
  const visibleRangeEnd = Math.min(page.total, page.page * page.pageSize);
  const latestDate = formatLatestDate(page.latestCreatedAt, locale, resources.common.empty);

  return (
    <section className="plan-workspace" aria-label={resources.learningPlans.ariaLabel}>
      <div className="plan-overview">
        <div className="plan-overview-copy">
          <h2 className="plan-overview-title">{resources.learningPlans.overviewTitle}</h2>
          <p>{resources.learningPlans.overviewDescription}</p>
        </div>
        <div className="plan-overview-actions">
          <button className="primary-button compact" onClick={onCreate} type="button">
            <Plus aria-hidden="true" />
            <span>{resources.learningPlans.newPlan}</span>
          </button>
        </div>
        <dl className="plan-stat-grid" aria-label={resources.learningPlans.overviewStats}>
          <div className="plan-stat-card">
            <dt>{resources.learningPlans.total}</dt>
            <dd>{page.total}</dd>
          </div>
          <div className="plan-stat-card">
            <dt>{resources.learningPlans.latestCreated}</dt>
            <dd>{latestDate}</dd>
          </div>
        </dl>
      </div>

      <section className="plan-list-card" aria-label={resources.learningPlans.listTitle}>
        <div className="plan-section-heading">
          <div>
            <h2>{resources.learningPlans.listTitle}</h2>
            <p>{resources.learningPlans.totalPlans(page.total)}</p>
          </div>
          <span>{visibleRangeStart}-{visibleRangeEnd}</span>
        </div>
        <div className="plan-list">
          {page.items.length === 0 ? (
            <div className="empty-plan-state">
              <h3>{resources.learningPlans.emptyTitle}</h3>
              <p>{resources.learningPlans.emptyDescription}</p>
            </div>
          ) : (
            <div className="plan-list-stack" role="list" aria-label={resources.learningPlans.listTitle}>
              {page.items.map((plan) => {
                const isDeleting = deletingPlanId === plan.id;
                const isActivePlan = page.activePlanId === plan.id;
                const isActivating = activatingPlanId === plan.id;
                const progressPercent = normalizeProgressPercent(plan.progressSummary.progressPercent);
                const todayPackTooltipId = `learning-plan-today-pack-tooltip-${plan.id}`;

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
                        {isActivePlan && (
                          <span className="status-badge current-plan-badge">{resources.learningPlans.currentActive}</span>
                        )}
                        {plan.status !== 'ACTIVE' && (
                          <span className="status-badge">{formatPlanStatus(plan.status, resources)}</span>
                        )}
                      </div>
                      <div className="plan-meta-row" aria-label={resources.learningPlans.planParameters}>
                        <span>{inferProgrammingLanguage(plan, resources.learningPlans.unspecified)}</span>
                        <span>{formatPlanLevel(plan.level, resources)}</span>
                        <span>{formatPlanIntent(plan.intent, resources)}</span>
                        <span>{resources.common.week(plan.durationWeeks)}</span>
                        <span>{resources.common.hoursPerWeek(plan.weeklyHours)}</span>
                        <span>{formatShortDate(plan.createdAt, locale)} {resources.common.created}</span>
                      </div>
                    </div>
                    <div className="plan-progress-summary">
                      <div
                        aria-label={resources.learningPlans.planProgressAriaLabel(
                          plan.progressSummary.completedProblemCount,
                          plan.progressSummary.totalProblemCount,
                          progressPercent,
                        )}
                        aria-valuemax={100}
                        aria-valuemin={0}
                        aria-valuenow={progressPercent}
                        className="plan-progress-ring"
                        role="progressbar"
                        style={getPlanProgressRingStyle(progressPercent)}
                      >
                        <strong>{progressPercent}%</strong>
                      </div>
                      <div className="plan-progress-copy">
                        <strong>
                          {resources.learningPlans.planProgressCount(
                            plan.progressSummary.completedProblemCount,
                            plan.progressSummary.totalProblemCount,
                          )}
                        </strong>
                        <span>{resources.learningPlans.planProgressHelper}</span>
                      </div>
                    </div>
                    <div className="plan-row-actions">
                      {onSelect && (
                        <button
                          aria-label={resources.learningPlans.viewPlan(plan.title)}
                          className="icon-button"
                          onClick={() => onSelect(plan.id)}
                          title={resources.common.view}
                          type="button"
                        >
                          <Eye aria-hidden="true" />
                        </button>
                      )}
                      {onOpenTodayPack && isActivePlan && (
                        <span className="toolbar-tooltip-wrap plan-middle-action-wrap">
                          <button
                            aria-describedby={todayPackTooltipId}
                            aria-label={resources.learningPlans.todayPack}
                            className="icon-button plan-middle-action"
                            onClick={() => onOpenTodayPack(plan.id)}
                            type="button"
                          >
                            <CalendarDays aria-hidden="true" />
                          </button>
                          <span
                            className="toolbar-tooltip plan-row-action-tooltip"
                            id={todayPackTooltipId}
                            role="tooltip"
                          >
                            {resources.learningPlans.todayPack}
                          </span>
                        </span>
                      )}
                      {onActivate && !isActivePlan && (
                        <button
                          className="secondary-button compact plan-middle-action"
                          disabled={isActivating}
                          onClick={() => onActivate(plan.id)}
                          type="button"
                        >
                          {isActivating ? resources.learningPlans.activating : resources.learningPlans.activate}
                        </button>
                      )}
                      <button
                        aria-label={resources.learningPlans.deletePlan(plan.title)}
                        className="icon-button danger-icon-button"
                        disabled={isDeleting}
                        onClick={() => onDelete(plan.id)}
                        title={isDeleting ? resources.common.deleting : resources.common.delete}
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
          <span>{resources.common.pageStatus(page.page, totalPages)}</span>
          <button
            className="secondary-button compact"
            disabled={page.page <= 1}
            onClick={() => onPageChange(page.page - 1)}
            type="button"
          >
            {resources.common.previousPage}
          </button>
          <button
            className="secondary-button compact"
            disabled={page.page >= totalPages}
            onClick={() => onPageChange(page.page + 1)}
            type="button"
          >
            {resources.common.nextPage}
          </button>
        </div>
      </section>
    </section>
  );
}
