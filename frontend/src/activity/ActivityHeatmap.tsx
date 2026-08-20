import { Activity, Flame, TrendingUp } from 'lucide-react';
import { useMemo } from 'react';
import type { CSSProperties } from 'react';
import { useI18n } from '../i18n/I18nProvider';
import {
  activityMonthLabels,
  type ActivityCalendarData,
  type ActivityLevel,
} from './activityHeatmap';

interface ActivityHeatmapProps {
  data?: ActivityCalendarData;
  loading?: boolean;
  unavailable?: boolean;
}

const levels: ActivityLevel[] = [0, 1, 2, 3, 4];

export default function ActivityHeatmap({ data, loading = false, unavailable = false }: ActivityHeatmapProps = {}) {
  const { locale, resources } = useI18n();
  const activity = data;
  const monthLabels = useMemo(
    () => activity ? activityMonthLabels(activity.days, locale) : [],
    [activity, locale],
  );
  const columns = activity ? Math.ceil(activity.days.length / 7) : 0;
  const dateFormatter = useMemo(
    () => new Intl.DateTimeFormat(locale, {
      year: 'numeric',
      month: 'long',
      day: 'numeric',
      weekday: 'long',
    }),
    [locale],
  );

  const showState = !activity;
  const stateText = loading
    ? resources.todayPack.activityLoading
    : unavailable
      ? resources.todayPack.activityUnavailable
      : resources.todayPack.activityUnavailable;

  return (
    <section
      className="home-activity-panel"
      aria-busy={loading}
      aria-labelledby="home-activity-title"
      data-testid="activity-heatmap"
    >
      <header className="home-activity-heading">
        <div className="home-activity-title">
          <span className="home-activity-title-icon" aria-hidden="true">
            <Activity />
          </span>
          <div>
            <p className="home-activity-eyebrow">{resources.todayPack.activityEyebrow}</p>
            <h2 id="home-activity-title">{resources.todayPack.activityTitle}</h2>
            <p>{resources.todayPack.activityDescription}</p>
          </div>
        </div>
        {activity ? (
          <div className="home-activity-summary" aria-label={resources.todayPack.activityAriaLabel}>
            <ActivityStat
              icon={Activity}
              label={resources.todayPack.activitySubmissions}
              value={String(activity.totalCount)}
            />
            <ActivityStat
              icon={TrendingUp}
              label={resources.todayPack.activityActiveDays}
              value={String(activity.activeDays)}
            />
            <ActivityStat
              icon={Flame}
              label={resources.todayPack.activityCurrentStreak}
              value={String(activity.currentStreak)}
            />
          </div>
        ) : null}
      </header>

      {showState ? (
        <div className="home-activity-state" role={loading ? 'status' : undefined}>
          {stateText}
        </div>
      ) : (
        <>
          <div className="home-activity-calendar-shell">
            <div className="home-activity-weekdays" aria-hidden="true">
              {resources.todayPack.activityWeekdays.map((label, index) => (
                <span key={`${label}-${index}`}>{label}</span>
              ))}
            </div>
            <div className="home-activity-scroll" tabIndex={0} aria-label={resources.todayPack.activityAriaLabel}>
              <div
                className="home-activity-calendar"
                style={{ '--activity-columns': columns } as CSSProperties}
              >
                <div className="home-activity-months" aria-hidden="true">
                  {monthLabels.map((month) => (
                    <span key={`${month.column}-${month.label}`} style={{ gridColumn: month.column }}>
                      {month.label}
                    </span>
                  ))}
                </div>
                <div className="home-activity-grid" aria-label={resources.todayPack.activityAriaLabel}>
                  {activity.days.map((day) => {
                    const date = new Date(`${day.date}T00:00:00`);
                    const tooltip = resources.todayPack.activityTooltip(
                      dateFormatter.format(date),
                      day.count,
                    );
                    return (
                      <span
                        aria-label={tooltip}
                        className={`home-activity-cell level-${day.level}`}
                        data-testid="activity-heatmap-cell"
                        key={day.date}
                        role="img"
                        title={tooltip}
                      />
                    );
                  })}
                </div>
              </div>
            </div>
          </div>

          <footer className="home-activity-footer">
            <span>{resources.todayPack.activityLegendLow}</span>
            <div className="home-activity-legend" aria-hidden="true">
              {levels.map((level) => <span className={`home-activity-cell level-${level}`} key={level} />)}
            </div>
            <span>{resources.todayPack.activityLegendHigh}</span>
            <span className="home-activity-longest">
              {resources.todayPack.activityLongestStreak}: {activity.longestStreak}
            </span>
          </footer>
        </>
      )}
    </section>
  );
}

function ActivityStat({
  icon: Icon,
  label,
  value,
}: {
  icon: typeof Activity;
  label: string;
  value: string;
}) {
  return (
    <div className="home-activity-stat">
      <span><Icon aria-hidden="true" />{label}</span>
      <strong>{value}</strong>
    </div>
  );
}
