import { useEffect } from 'react';
import { reviewCenterPath } from './app/navigation';

/** 兼容旧书签；知识卡复习统一由复习中心承载。 */
export default function KnowledgeReviewCenterPage({ onNavigate }: { onNavigate: (path: string, options?: { replace?: boolean }) => void }) {
  useEffect(() => { onNavigate(reviewCenterPath({ mode: 'knowledge' }), { replace: true }); }, [onNavigate]);
  return <div className="loading-panel" role="status">正在打开复习中心…</div>;
}
