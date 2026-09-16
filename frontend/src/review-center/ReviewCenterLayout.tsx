import { Archive, ArchiveRestore, BookOpenCheck, ChevronLeft, ChevronRight, Eye, RefreshCw, Search, X } from 'lucide-react';
import { useEffect, useRef, type ReactNode, type Ref } from 'react';
import { reviewCenterPath, type ReviewCenterSearchOptions } from '../app/navigation';
import { useI18n } from '../i18n/I18nProvider';

export function ReviewCenterHeader({ mode, onNavigate, actionLabel, canStart, onStart }: {
  mode: ReviewCenterSearchOptions['mode'];
  onNavigate: (path: string) => void;
  actionLabel: string;
  canStart: boolean;
  onStart: () => void;
}) {
  const { resources } = useI18n();
  return <header className="mistake-header">
    <h1 id="mistake-title">{resources.reviewCenter.title}</h1>
    <div className="mistake-header-actions">
      <div className="review-mode-switch" role="group" aria-label="复习类型">
        <button type="button" aria-pressed={mode !== 'knowledge'} onClick={() => onNavigate(reviewCenterPath())}>刷题</button>
        <button type="button" aria-pressed={mode === 'knowledge'} onClick={() => onNavigate(reviewCenterPath({ mode: 'knowledge' }))}>八股文</button>
      </div>
      <button className="primary-button mistake-review-button" disabled={!canStart} onClick={onStart} type="button">
        <BookOpenCheck aria-hidden="true" /><span>{actionLabel}</span>
      </button>
    </div>
  </header>;
}

export function ReviewCenterStats({ items }: { items: { label: string; value: ReactNode }[] }) {
  const { resources } = useI18n();
  return <dl className="mistake-stat-grid" aria-label={resources.reviewCenter.overviewAriaLabel}>
    {items.map(({ label, value }) => <div key={label}><dt>{label}</dt><dd>{value}</dd></div>)}
  </dl>;
}

export function ReviewCenterToolbar({ keyword, placeholder, onSearch, checked, filterLabel, onFilter, onRefresh, disabled = false }: {
  keyword: string;
  placeholder: string;
  onSearch: (value: string) => void;
  checked: boolean;
  filterLabel: string;
  onFilter: (value: boolean) => void;
  onRefresh: () => void;
  disabled?: boolean;
}) {
  const { resources } = useI18n();
  return <section className="mistake-toolbar" aria-label={resources.reviewCenter.filtersAriaLabel}>
    <label className="search-field"><Search aria-hidden="true" /><input aria-label={placeholder} placeholder={placeholder} value={keyword} onChange={(event) => onSearch(event.target.value)} /></label>
    <label className="checkbox-control"><input type="checkbox" checked={checked} onChange={(event) => onFilter(event.target.checked)} /><span>{filterLabel}</span></label>
    <button aria-label={resources.reviewCenter.refreshCards} className="icon-button" disabled={disabled} onClick={onRefresh} type="button"><RefreshCw aria-hidden="true" /></button>
  </section>;
}

export function ReviewCenterCard({ title, meta, timeline, archived = false, pending = false, onDetail, onArchive, cardRef }: {
  title: string;
  meta: ReactNode;
  timeline?: ReactNode;
  archived?: boolean;
  pending?: boolean;
  onDetail: (trigger: HTMLButtonElement) => void;
  onArchive: () => void;
  cardRef?: Ref<HTMLElement>;
}) {
  const { resources } = useI18n();
  return <article className={`mistake-note-card${timeline ? '' : ' review-card-without-timeline'}`} ref={cardRef} tabIndex={-1}>
    <div className="mistake-note-main"><h2>{title}</h2><div className="mistake-note-meta">{meta}</div></div>
    {timeline}
    <div className="mistake-note-actions">
      <button aria-label={resources.reviewCenter.viewCardDetail(title)} className="icon-button" title={resources.reviewCenter.viewDetail} onClick={(event) => onDetail(event.currentTarget)} type="button"><Eye aria-hidden="true" /></button>
      <button aria-label={archived ? resources.reviewCenter.restoreReview : resources.reviewCenter.removeFromReview} title={archived ? resources.reviewCenter.restoreReview : resources.reviewCenter.removeFromReview} className="icon-button" disabled={pending} onClick={onArchive} type="button">
        {archived ? <ArchiveRestore aria-hidden="true" /> : <Archive aria-hidden="true" />}
      </button>
    </div>
  </article>;
}

export function ReviewCenterPagination({ page, totalPages, onPage }: { page: number; totalPages: number; onPage: (page: number) => void }) {
  const { resources } = useI18n();
  return <nav aria-label={resources.common.pageStatus(page, totalPages)} className="pagination-row mistake-pagination">
    <button aria-label={resources.common.previousPage} className="icon-button" disabled={page <= 1} onClick={() => onPage(page - 1)} type="button"><ChevronLeft aria-hidden="true" /></button>
    <span>{resources.common.pageStatus(page, totalPages)}</span>
    <button aria-label={resources.common.nextPage} className="icon-button" disabled={page >= totalPages} onClick={() => onPage(page + 1)} type="button"><ChevronRight aria-hidden="true" /></button>
  </nav>;
}

export function ReviewCenterDetail({ title, onClose, children }: { title: string; onClose: () => void; children: ReactNode }) {
  const { resources } = useI18n();
  const closeRef = useRef<HTMLButtonElement>(null);
  const dialogRef = useRef<HTMLElement>(null);
  useEffect(() => { closeRef.current?.focus(); }, []);
  return <div className="modal-backdrop">
    <section aria-labelledby="review-card-detail-title" aria-modal="true" className="mistake-detail-modal" role="dialog" ref={dialogRef} onKeyDown={(event) => {
      if (event.key === 'Escape') onClose();
      if (event.key !== 'Tab') return;
      const elements = dialogRef.current?.querySelectorAll<HTMLElement>('button:not(:disabled), a[href], input, textarea, select, summary, [tabindex="0"]');
      const first = elements?.[0];
      const last = elements?.[elements.length - 1];
      if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last?.focus(); }
      if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first?.focus(); }
    }}>
      <div className="modal-heading"><div><p className="eyebrow">Review Card</p><h2 id="review-card-detail-title">{title}</h2></div>
        <button aria-label={resources.reviewCenter.closeDetail} className="icon-button" onClick={onClose} ref={closeRef} type="button"><X aria-hidden="true" /></button>
      </div>
      {children}
    </section>
  </div>;
}
