import { Activity, CalendarClock, Eye, Layers3, Plus, Trash2 } from 'lucide-react';
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
  onSelect?: (planId: number) => void;
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

function inferProgrammingLanguage(
  plan: { programmingLanguage?: string; title: string; goal: string },
  fallback: string,
): string {
  if (plan.programmingLanguage?.trim()) {
    return plan.programmingLanguage.trim();
  }

  const searchable = `${plan.title} ${plan.goal}`.toLocaleLowerCase();
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
  onSelect,
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
          <p className="eyebrow">Learning Plans</p>
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
            <dt>{resources.learningPlans.active}</dt>
            <dd>{page.activeCount}</dd>
          </div>
          <div className="plan-stat-card">
            <dt>{resources.learningPlans.archived}</dt>
            <dd>{page.archivedCount}</dd>
          </div>
          <div className="plan-stat-card">
            <dt>{resources.learningPlans.latestCreated}</dt>
            <dd>{latestDate}</dd>
          </div>
        </dl>
      </div>

      <div className="plan-dashboard-grid">
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
                          <span className="status-badge">{formatPlanStatus(plan.status, resources)}</span>
                        </div>
                        <p>{plan.goal}</p>
                        <div className="plan-meta-row" aria-label={resources.learningPlans.planParameters}>
                          <span>{inferProgrammingLanguage(plan, resources.learningPlans.unspecified)}</span>
                          <span>{formatPlanLevel(plan.level, resources)}</span>
                          <span>{formatPlanIntent(plan.intent, resources)}</span>
                          <span>{resources.common.week(plan.durationWeeks)}</span>
                          <span>{resources.common.hoursPerWeek(plan.weeklyHours)}</span>
                          <span>{formatShortDate(plan.createdAt, locale)} {resources.common.created}</span>
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

        <aside className="plan-insight-panel" aria-label={resources.learningPlans.rhythmOverview}>
          <div className="plan-section-heading compact-heading">
            <div>
              <h2>{resources.learningPlans.currentRhythm}</h2>
              <p>{resources.learningPlans.rhythmOverview}</p>
            </div>
          </div>
          <div className="plan-insight-list">
            <div className="plan-insight-item">
              <Activity aria-hidden="true" />
              <div>
                <strong>{page.total === 0 ? resources.learningPlans.noRhythm : resources.learningPlans.activePlans(page.activeCount)}</strong>
                <span>{resources.learningPlans.archivedPlans(page.archivedCount)}</span>
              </div>
            </div>
            <div className="plan-insight-item">
              <Layers3 aria-hidden="true" />
              <div>
                <strong>{resources.learningPlans.maintainByScenario}</strong>
                <span>{resources.learningPlans.maintainByScenarioDescription}</span>
              </div>
            </div>
            <div className="plan-insight-item">
              <CalendarClock aria-hidden="true" />
              <div>
                <strong>{resources.learningPlans.latestCreatedLabel(latestDate)}</strong>
                <span>{resources.learningPlans.latestCreatedDescription}</span>
              </div>
            </div>
          </div>
        </aside>
      </div>
    </section>
  );
}
