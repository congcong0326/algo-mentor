import { Archive, BookOpenCheck, Eye, RefreshCw, Search, X } from 'lucide-react';
import { useEffect, useMemo, useRef, useState } from 'react';
import { APP_ROUTES } from '../app/navigation';
import MarkdownView from '../components/MarkdownView';
import ProblemNoteEditor from '../problem-notes/ProblemNoteEditor';
import {
  archiveReviewCard,
  getReviewCardContext,
  getReviewSummary,
  listReviewCards,
  requireApiData,
} from '../services/api';
import type {
  ReviewCard,
  ReviewCardContext,
  ReviewCardSource,
  ReviewRating,
  ReviewSummaryResponse,
} from '../types/api';
import { formatUpcomingReviewTime } from '../utils/time';

interface MistakeNotebookPageProps {
  onNavigate: (path: string) => void;
}

const sourceLabels: Record<ReviewCardSource, string> = {
  REVIEW_FAILED: '错题',
  REVIEW_PASSED: '复习',
  USER_MARKED: '手动标记',
};

const ratingLabels: Record<ReviewRating, string> = {
  AGAIN: '重来',
  HARD: '困难',
  GOOD: '良好',
  EASY: '简单',
};

const dayMs = 24 * 60 * 60 * 1000;

export default function MistakeNotebookPage({ onNavigate }: MistakeNotebookPageProps) {
  const [items, setItems] = useState<ReviewCard[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [keyword, setKeyword] = useState('');
  const [mistakeOnly, setMistakeOnly] = useState(false);
  const [actionError, setActionError] = useState('');
  const [detailCard, setDetailCard] = useState<ReviewCard>();
  const [context, setContext] = useState<ReviewCardContext>();
  const [contextLoading, setContextLoading] = useState(false);
  const [contextError, setContextError] = useState('');
  const [reviewSummary, setReviewSummary] = useState<ReviewSummaryResponse>();
  const detailRequestId = useRef(0);
  const detailTriggerButtonRef = useRef<HTMLButtonElement | null>(null);
  const detailCloseButtonRef = useRef<HTMLButtonElement>(null);

  const stats = useMemo(() => {
    const active = items.filter((item) => !item.archived);
    const due = active.filter((item) => new Date(item.dueAt).getTime() <= Date.now());
    const mistakes = active.filter((item) => item.source === 'REVIEW_FAILED' || item.lapses > 0);
    return { active: active.length, due: due.length, mistakes: mistakes.length };
  }, [items]);
  const currentDueCount = reviewSummary?.dueCount ?? stats.due;
  const remainingTodayCount = reviewSummary?.remainingTodayCount ?? currentDueCount;
  const reviewActionLabel = reviewSummary === undefined && loading
    ? '加载今日复习...'
    : currentDueCount > 0
      ? `开始今日复习 ${currentDueCount} 题`
      : remainingTodayCount > 0
        ? `${formatUpcomingReviewTime(reviewSummary?.nextDueAt)}可复习`
        : '今日已完成';

  useEffect(() => {
    const controller = new AbortController();
    void load(controller.signal);
    return () => controller.abort();
  }, [keyword, mistakeOnly]);

  useEffect(() => {
    const controller = new AbortController();
    void loadSummary(controller.signal);
    return () => controller.abort();
  }, []);

  useEffect(() => {
    if (detailCard) {
      detailCloseButtonRef.current?.focus();
    }
  }, [detailCard]);

  async function load(signal?: AbortSignal) {
    setLoading(true);
    setError('');
    try {
      const response = await listReviewCards({ keyword, mistakeOnly, limit: 80 }, signal);
      const cards = requireApiData(response, '复习卡加载失败');
      setItems(cards);
      if (detailCard && !cards.some((item) => item.id === detailCard.id)) {
        closeDetail();
      }
    } catch (loadError) {
      if (!(loadError instanceof DOMException && loadError.name === 'AbortError')) {
        setError(loadError instanceof Error ? loadError.message : '复习卡加载失败');
      }
    } finally {
      if (!signal?.aborted) {
        setLoading(false);
      }
    }
  }

  async function loadSummary(signal?: AbortSignal) {
    try {
      const response = await getReviewSummary(signal);
      setReviewSummary(requireApiData(response, '复习摘要加载失败'));
    } catch (loadError) {
      if (!(loadError instanceof DOMException && loadError.name === 'AbortError')) {
        setReviewSummary(undefined);
      }
    }
  }

  async function handleArchive(card: ReviewCard) {
    setActionError('');
    try {
      const response = await archiveReviewCard(card.id, !card.archived);
      const updated = requireApiData(response, '更新归档状态失败');
      setItems((current) => current.map((item) => (item.id === updated.id ? updated : item)));
      void loadSummary();
    } catch (archiveError) {
      setActionError(archiveError instanceof Error ? archiveError.message : '更新归档状态失败');
    }
  }

  async function handleOpenDetail(card: ReviewCard, triggerButton: HTMLButtonElement) {
    const requestId = detailRequestId.current + 1;
    detailRequestId.current = requestId;
    detailTriggerButtonRef.current = triggerButton;
    setDetailCard(card);
    setContext(undefined);
    setContextError('');
    setContextLoading(true);
    try {
      const response = await getReviewCardContext(card.id);
      if (detailRequestId.current === requestId) {
        setContext(requireApiData(response, '复习卡详情加载失败'));
      }
    } catch (loadError) {
      if (detailRequestId.current === requestId) {
        setContextError(loadError instanceof Error ? loadError.message : '复习卡详情加载失败');
      }
    } finally {
      if (detailRequestId.current === requestId) {
        setContextLoading(false);
      }
    }
  }

  function closeDetail() {
    detailRequestId.current += 1;
    setDetailCard(undefined);
    setContext(undefined);
    setContextError('');
    setContextLoading(false);
    if (detailTriggerButtonRef.current?.isConnected) {
      detailTriggerButtonRef.current.focus();
    }
    detailTriggerButtonRef.current = null;
  }

  return (
    <section className="mistake-page" aria-labelledby="mistake-title">
      <header className="mistake-header">
        <div>
          <p className="eyebrow">Review Center</p>
          <h1 id="mistake-title">复习中心</h1>
        </div>
        <button
          className="primary-button"
          disabled={currentDueCount === 0}
          onClick={() => currentDueCount > 0 && onNavigate(APP_ROUTES.reviewSession)}
          type="button"
        >
          <BookOpenCheck aria-hidden="true" />
          <span>{reviewActionLabel}</span>
        </button>
      </header>

      <section className="mistake-stat-grid" aria-label="复习概览">
        <div><span>今日剩余</span><strong>{remainingTodayCount}</strong></div>
        <div><span>复习题</span><strong>{stats.active}</strong></div>
        <div><span>错题</span><strong>{stats.mistakes}</strong></div>
      </section>

      <section className="mistake-toolbar" aria-label="复习筛选">
        <label className="search-field">
          <Search aria-hidden="true" />
          <input
            onChange={(event) => setKeyword(event.target.value)}
            placeholder="搜索题目或笔记"
            value={keyword}
          />
        </label>
        <label className="checkbox-control">
          <input checked={mistakeOnly} onChange={(event) => setMistakeOnly(event.target.checked)} type="checkbox" />
          <span>仅看错题</span>
        </label>
        <button aria-label="刷新复习卡" className="icon-button" onClick={() => void load()} type="button">
          <RefreshCw aria-hidden="true" />
        </button>
      </section>

      {(error || actionError) && <p className="error-text" role="alert">{error || actionError}</p>}

      <div className="mistake-list" aria-busy={loading}>
        {loading ? (
          <div className="loading-panel">正在加载复习卡...</div>
        ) : items.length === 0 ? (
          <div className="loading-panel">暂无复习卡。</div>
        ) : items.map((card) => (
          <article className="mistake-note-card" key={card.id}>
            <div className="mistake-note-main">
              <h2>{card.problemTitle || card.problemSlug}</h2>
              <p>{sourceLabels[card.source]} · {dueTimingLabel(card.dueAt)}</p>
              {(card.lastRating || card.lapses > 0) && (
                <p className="mistake-note-muted">
                  {card.lastRating ? `上次：${ratingLabels[card.lastRating]}` : ''}
                  {card.lastRating && card.lapses > 0 ? ' · ' : ''}
                  {card.lapses > 0 ? `忘记过 ${card.lapses} 次` : ''}
                </p>
              )}
            </div>
            <div className="mistake-note-actions">
              <button
                aria-label={`查看复习卡详情 ${card.problemTitle || card.problemSlug}`}
                className="icon-button"
                onClick={(event) => void handleOpenDetail(card, event.currentTarget)}
                title="查看详情"
                type="button"
              >
                <Eye aria-hidden="true" />
              </button>
              <button className="secondary-button compact" onClick={() => void handleArchive(card)} type="button">
                <Archive aria-hidden="true" />
                <span>{card.archived ? '恢复复习' : '移出复习'}</span>
              </button>
            </div>
          </article>
        ))}
      </div>

      {detailCard && (
        <div className="modal-backdrop">
          <section aria-labelledby="review-card-detail-title" aria-modal="true" className="mistake-detail-modal" role="dialog">
            <div className="modal-heading">
              <div>
                <p className="eyebrow">Review Card</p>
                <h2 id="review-card-detail-title">{detailCard.problemTitle || detailCard.problemSlug}</h2>
              </div>
              <button aria-label="关闭复习卡详情" className="icon-button" onClick={closeDetail} ref={detailCloseButtonRef} type="button">
                <X aria-hidden="true" />
              </button>
            </div>

            {contextLoading ? (
              <div className="loading-panel">正在加载复习卡详情...</div>
            ) : contextError ? (
              <p className="error-text" role="alert">{contextError}</p>
            ) : context ? (
              <div className="mistake-detail-content">
                <section className="review-problem-content"><MarkdownView content={context.problem.contentMarkdown} /></section>
                <ProblemNoteEditor problemSlug={context.problem.slug} />
                <section className="mistake-detail-section">
                  <h3>最近复习记录</h3>
                  {context.recentAttempts.length > 0 ? (
                    <ol className="review-history-list">
                      {context.recentAttempts.map((attempt) => (
                        <li key={attempt.id}>
                          <div className="review-history-meta">
                            <strong>{ratingLabels[attempt.rating]}</strong>
                            <span>{formatDateTime(attempt.reviewedAt)}</span>
                          </div>
                          <small>间隔 {attempt.schedulingBefore.intervalDays} 天 → {attempt.schedulingAfter.intervalDays} 天</small>
                        </li>
                      ))}
                    </ol>
                  ) : <p>暂无复习记录。</p>}
                </section>
              </div>
            ) : null}
          </section>
        </div>
      )}
    </section>
  );
}

function dueTimingLabel(dueAt: string) {
  const dueTime = new Date(dueAt).getTime();
  if (Number.isNaN(dueTime)) {
    return '复习时间待确认';
  }
  const diffDays = Math.round((startOfDay(dueTime) - startOfDay(Date.now())) / dayMs);
  if (diffDays < 0) return `已逾期 ${Math.abs(diffDays)} 天`;
  if (diffDays === 0) return '今日到期';
  if (diffDays === 1) return '明天复习';
  return `${diffDays} 天后复习`;
}

function startOfDay(time: number) {
  const date = new Date(time);
  date.setHours(0, 0, 0, 0);
  return date.getTime();
}

function formatDateTime(value: string) {
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString('zh-CN', { hour12: false });
}
