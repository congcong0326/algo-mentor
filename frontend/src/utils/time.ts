export function browserTimezone() {
  return Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC';
}

export function formatUpcomingReviewTime(value?: string | null, now = Date.now()) {
  if (!value) {
    return '稍后';
  }
  const dueTime = new Date(value).getTime();
  if (!Number.isFinite(dueTime)) {
    return '稍后';
  }
  const minutes = Math.max(1, Math.ceil((dueTime - now) / 60_000));
  if (minutes <= 60) {
    return `${minutes} 分钟后`;
  }
  return new Date(dueTime).toLocaleTimeString('zh-CN', {
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  });
}
