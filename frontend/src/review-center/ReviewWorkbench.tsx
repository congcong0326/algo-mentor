import { ArrowLeft } from 'lucide-react';
import type { ReactNode } from 'react';
import { useI18n } from '../i18n/I18nProvider';

/** 两类复习卡共用的滚动内容区与固定评级栏。 */
export default function ReviewWorkbench({ title, subtitle, progress, status, onBack, footer, children }: {
  title: string;
  subtitle?: string;
  progress: string;
  status: string;
  onBack: () => void;
  footer: ReactNode;
  children: ReactNode;
}) {
  const { resources } = useI18n();
  return <article className="review-card-workbench">
    <div className="review-card-scroll">
      <header className="review-workbench-toolbar">
        <button className="secondary-button compact" onClick={onBack} type="button">
          <ArrowLeft aria-hidden="true" /><span>{resources.reviewCenter.backToReviewCenter}</span>
        </button>
        <div className="review-workbench-heading">
          <h1 id="review-session-title" title={title}>{title}</h1>
          {subtitle && <span>{subtitle}</span>}
        </div>
        <div className="review-workbench-status"><strong>{progress}</strong><span>{status}</span></div>
      </header>
      <div className="review-card-content">{children}</div>
    </div>
    {footer}
  </article>;
}
