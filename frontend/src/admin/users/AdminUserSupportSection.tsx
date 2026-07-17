import { useEffect, useState } from 'react';
import { getAdminFeedbackThreads, getBetaAccessUserMembership, requireApiData } from '../../services/api';
import type { BetaAccessUserMembership, FeedbackThreadPage } from '../../types/api';

export default function AdminUserSupportSection({ userId, onNavigate }: { userId: number; onNavigate: (path: string) => void }) {
  const [membership, setMembership] = useState<BetaAccessUserMembership>();
  const [feedback, setFeedback] = useState<FeedbackThreadPage>();
  const [membershipError, setMembershipError] = useState(false);
  const [feedbackError, setFeedbackError] = useState(false);
  useEffect(() => {
    const controller = new AbortController();
    void getBetaAccessUserMembership(userId, controller.signal).then((response) => setMembership(requireApiData(response, ''))).catch(() => setMembershipError(true));
    void getAdminFeedbackThreads({ userId, status: 'OPEN', page: 1, pageSize: 1 }, controller.signal).then((response) => setFeedback(requireApiData(response, ''))).catch(() => setFeedbackError(true));
    return () => controller.abort();
  }, [userId]);
  return <section className="admin-user-support-section"><h3>内测准入与支持</h3><dl><dt>白名单</dt><dd>{membershipError ? '暂不可用' : membership ? membership.allowed ? `已在白名单（记录 ${membership.allowedEmailId}）` : '不在白名单' : '正在加载...'}</dd><dt>OPEN 反馈</dt><dd>{feedbackError ? '暂不可用' : feedback ? <button className="text-link-button" onClick={() => onNavigate(`/admin/feedback?userId=${userId}&status=OPEN`)} type="button">{feedback.total} 条</button> : '正在加载...'}</dd></dl></section>;
}
