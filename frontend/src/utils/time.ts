export function browserTimezone() {
  return Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC';
}

export function formatUpcomingReviewTime(value?: string | null, now = Date.now(), locale = 'zh-CN') {
  const isChinese = locale.toLowerCase().startsWith('zh');
  if (!value) {
    return isChinese ? '稍后' : 'later';
  }
  const dueTime = new Date(value).getTime();
  if (!Number.isFinite(dueTime)) {
    return isChinese ? '稍后' : 'later';
  }
  const minutes = Math.max(1, Math.ceil((dueTime - now) / 60_000));
  if (minutes <= 60) {
    return isChinese ? `${minutes} 分钟后` : `in ${minutes} ${minutes === 1 ? 'minute' : 'minutes'}`;
  }
  return new Date(dueTime).toLocaleTimeString(locale, {
    hour: '2-digit',
    minute: '2-digit',
  });
}
