import { useEffect, useRef, useState } from 'react';
import { reviewCenterPath, reviewCenterSearchOptionsFromSearch, reviewSessionPath } from '../app/navigation';
import KnowledgeCardContent from '../components/knowledge/KnowledgeCardContent';
import { useI18n } from '../i18n/I18nProvider';
import { allKnowledgePages, knowledgeApi } from '../services/knowledge';
import type { KnowledgeCard, KnowledgeReviewSummary } from '../types/knowledge';
import type { ReviewRating } from '../types/api';
import { formatUpcomingReviewTime } from '../utils/time';
import { ReviewCenterCard, ReviewCenterDetail, ReviewCenterHeader, ReviewCenterPagination, ReviewCenterStats, ReviewCenterToolbar } from './ReviewCenterLayout';
import { dueTimingLabel } from './reviewPresentation';

const pageSize = 10;

export default function KnowledgeReviewListPage({ onNavigate, search = '' }: {
  onNavigate: (path: string, options?: { replace?: boolean }) => void;
  search?: string;
}) {
  const { resources, locale } = useI18n();
  const filters = reviewCenterSearchOptionsFromSearch(search);
  const [cards, setCards] = useState<KnowledgeCard[]>([]);
  const [summary, setSummary] = useState<KnowledgeReviewSummary>();
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [pending, setPending] = useState<string>();
  const [selected, setSelected] = useState<KnowledgeCard>();
  const [detail, setDetail] = useState<KnowledgeCard>();
  const [detailError, setDetailError] = useState('');
  const requestId = useRef(0);
  const detailId = useRef(0);
  const triggerRef = useRef<HTMLButtonElement | null>(null);
  const mutationPending = useRef(false);
  const keyword = filters.keyword || '';
  const dueOnly = filters.dueOnly || false;
  const filtered = cards.filter((card) => (!dueOnly || card.learningState.isDue)
    && `${card.question} ${card.tags.join(' ')}`.toLocaleLowerCase().includes(keyword.toLocaleLowerCase()));
  const totalPages = Math.max(1, Math.ceil(filtered.length / pageSize));
  const page = Math.min(filters.page || 1, totalPages);
  const items = filtered.slice((page - 1) * pageSize, page * pageSize);
  const dueCount = summary?.dueCount || 0;
  const actionLabel = loading ? resources.reviewCenter.loadTodayReview : dueCount > 0
    ? resources.reviewCenter.startTodayReview(dueCount)
    : summary?.nextDueAt ? resources.reviewCenter.availableAt(formatUpcomingReviewTime(summary.nextDueAt, Date.now(), locale))
      : resources.reviewCenter.todayCompleted;

  useEffect(() => {
    void load();
    return () => { requestId.current++; detailId.current++; };
  }, []);

  async function load() {
    const id = ++requestId.current;
    setLoading(true); setError('');
    try {
      const [nextCards, nextSummary] = await Promise.all([
        allKnowledgePages((nextPage) => knowledgeApi.reviewCards(nextPage)), knowledgeApi.summary(),
      ]);
      if (id !== requestId.current) return;
      setCards(nextCards); setSummary(nextSummary);
    } catch (e) {
      if (id === requestId.current) setError(e instanceof Error ? e.message : resources.reviewCenter.cardLoadFailed);
    } finally {
      if (id === requestId.current) setLoading(false);
    }
  }

  function update(nextKeyword: string, nextDue: boolean, nextPage = 1) {
    onNavigate(reviewCenterPath({ mode: 'knowledge', keyword: nextKeyword, dueOnly: nextDue, page: nextPage }), { replace: true });
  }

  async function openDetail(card: KnowledgeCard, trigger: HTMLButtonElement) {
    const id = ++detailId.current;
    triggerRef.current = trigger; setSelected(card); setDetail(undefined); setDetailError('');
    try {
      const data = await knowledgeApi.card(card.slug);
      if (id === detailId.current) setDetail(data);
    } catch (e) {
      if (id === detailId.current) setDetailError(e instanceof Error ? e.message : resources.reviewCenter.detailLoadFailed);
    }
  }

  function closeDetail() {
    detailId.current++; setSelected(undefined); setDetail(undefined);
    triggerRef.current?.focus();
  }

  async function remove(card: KnowledgeCard) {
    if (mutationPending.current) return;
    mutationPending.current = true; setPending(card.slug); setError('');
    try {
      await knowledgeApi.setEnrollment(card.slug, false);
      if (selected?.slug === card.slug) closeDetail();
      await load();
    } catch (e) {
      setError(e instanceof Error ? e.message : resources.reviewCenter.archiveUpdateFailed);
    } finally {
      mutationPending.current = false; setPending(undefined);
    }
  }

  return <section className="mistake-page" aria-labelledby="mistake-title">
    <ReviewCenterHeader mode="knowledge" onNavigate={onNavigate} actionLabel={actionLabel} canStart={!loading && !error && dueCount > 0}
      onStart={() => onNavigate(reviewSessionPath({ ...filters, mode: 'knowledge', page }))} />
    <ReviewCenterStats items={[
      { label: '当前待复习', value: summary?.dueCount ?? '—' },
      { label: '复习卡片', value: summary?.enrolledCount ?? '—' },
      { label: '未到期', value: summary ? Math.max(0, summary.enrolledCount - summary.dueCount) : '—' },
    ]} />
    <ReviewCenterToolbar keyword={keyword} placeholder="搜索知识卡或标签" onSearch={(value) => update(value, dueOnly)} checked={dueOnly}
      filterLabel="仅看待复习" onFilter={(value) => update(keyword, value)} disabled={loading || Boolean(pending)} onRefresh={() => void load()} />
    {error && <p className="error-text" role="alert">{error}</p>}
    <div className="mistake-list" aria-busy={loading}>
      {loading ? <div className="loading-panel">{resources.reviewCenter.loadingCards}</div>
        : error && cards.length === 0 ? null : items.length === 0 ? <div className="loading-panel">{cards.length ? '没有匹配的知识卡。' : '暂无复习卡。在知识库中加入卡片后，可在这里开始复习。'}</div>
          : items.map((card) => <ReviewCenterCard key={card.slug} title={card.question} pending={Boolean(pending)}
            onDetail={(trigger) => void openDetail(card, trigger)} onArchive={() => void remove(card)}
            meta={<><span>八股文</span><span>{dueTimingLabel(card.learningState.dueAt || '', resources.reviewCenter)}</span>
              {card.learningState.lastRating && <span className="mistake-note-rating">{resources.reviewCenter.lastRating(resources.reviewCenter.ratingLabels[card.learningState.lastRating as ReviewRating])}</span>}
              {card.tags.map((tag) => <span key={tag}>{tag}</span>)}
            </>} />)}
    </div>
    {!loading && !error && items.length > 0 && <ReviewCenterPagination page={page} totalPages={totalPages} onPage={(value) => update(keyword, dueOnly, value)} />}
    {selected && <ReviewCenterDetail title={selected.question} onClose={closeDetail}>
      {detailError ? <p className="error-text" role="alert">{detailError}</p> : !detail ? <div className="loading-panel">{resources.reviewCenter.loadingDetail}</div>
        : <div className="mistake-detail-content"><KnowledgeCardContent card={detail} /></div>}
    </ReviewCenterDetail>}
  </section>;
}
