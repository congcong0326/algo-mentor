import type { LocaleResources } from '../i18n/locales';

const dayMs = 24 * 60 * 60 * 1000;

export function dueTimingLabel(dueAt: string, resources: LocaleResources['reviewCenter']) {
  const dueTime = new Date(dueAt).getTime();
  if (Number.isNaN(dueTime)) {
    return resources.dueUnknown;
  }
  const diffDays = Math.round((startOfDay(dueTime) - startOfDay(Date.now())) / dayMs);
  if (diffDays < 0) return resources.overdue(Math.abs(diffDays));
  if (diffDays === 0) return resources.dueToday;
  if (diffDays === 1) return resources.reviewTomorrow;
  return resources.reviewInDays(diffDays);
}

function startOfDay(time: number) {
  const date = new Date(time);
  date.setHours(0, 0, 0, 0);
  return date.getTime();
}
