import { useEffect, useState } from 'react';
import { useI18n } from '../../i18n/I18nProvider';
import { getAdminFeedbackThreads, getBetaAccessUserMembership, requireApiData } from '../../services/api';
import type { BetaAccessUserMembership, FeedbackThreadPage } from '../../types/api';

export default function AdminUserSupportSection({ userId, onNavigate }: { userId: number; onNavigate: (path: string) => void }) {
  const { locale, resources } = useI18n();
  const [membership, setMembership] = useState<BetaAccessUserMembership>();
  const [feedback, setFeedback] = useState<FeedbackThreadPage>();
  const [membershipError, setMembershipError] = useState(false);
  const [feedbackError, setFeedbackError] = useState(false);
  useEffect(() => {
    const controller = new AbortController();
    void getBetaAccessUserMembership(userId, controller.signal).then((response) => setMembership(requireApiData(response, ''))).catch(() => setMembershipError(true));
    void getAdminFeedbackThreads({ userId, status: 'OPEN', page: 1, pageSize: 1 }, controller.signal).then((response) => setFeedback(requireApiData(response, ''))).catch(() => setFeedbackError(true));
    return () => controller.abort();
  }, [locale, userId]);
  return (
    <section className="admin-user-support-section">
      <h3>{resources.adminUserSupport.title}</h3>
      <dl>
        <dt>{resources.adminUserSupport.allowlist}</dt>
        <dd>
          {membershipError
            ? resources.adminUserSupport.unavailable
            : membership
              ? membership.allowed
                ? resources.adminUserSupport.allowed(membership.allowedEmailId)
                : resources.adminUserSupport.notAllowed
              : resources.adminUserSupport.loading}
        </dd>
        <dt>{resources.adminUserSupport.openFeedback}</dt>
        <dd>
          {feedbackError
            ? resources.adminUserSupport.unavailable
            : feedback
              ? (
                <button className="text-link-button" onClick={() => onNavigate(`/admin/feedback?userId=${userId}&status=OPEN`)} type="button">
                  {resources.adminUserSupport.feedbackCount(feedback.total)}
                </button>
              )
              : resources.adminUserSupport.loading}
        </dd>
      </dl>
    </section>
  );
}
