import {
  AlertCircle,
  ArrowLeft,
  ArrowRight,
  CheckCircle2,
  Loader2,
  RefreshCw,
  Sparkles,
} from 'lucide-react';
import { useEffect, useMemo, useState } from 'react';
import { APP_ROUTES } from '../app/navigation';
import MarkdownView from '../components/MarkdownView';
import ProblemNoteEditor from '../problem-notes/ProblemNoteEditor';
import {
  getReviewCardContext,
  getReviewQueue,
  requireApiData,
  submitReviewAttempt,
} from '../services/api';
import type {
  ReviewAttempt,
  ReviewCard,
  ReviewCardContext,
  ReviewIntervalPreview,
  ReviewRating,
} from '../types/api';
import { generateClientId } from '../utils/id';

interface ReviewSessionPageProps {
  onNavigate: (path: string) => void;
}

const ratingLabels: Record<ReviewRating, string> = {
  AGAIN: '重来',
  HARD: '困难',
  GOOD: '良好',
  EASY: '简单',
};

const ratingDescriptions: Record<ReviewRating, string> = {
  AGAIN: '基本没有想起来',
  HARD: '想起来了，但过程费力或不完整',
  GOOD: '独立回忆出主要思路',
  EASY: '快速、完整地回忆出来',
};

const ratingKeys: Record<string, ReviewRating> = {
  '1': 'AGAIN',
  '2': 'HARD',
  '3': 'GOOD',
  '4': 'EASY',
};

export default function ReviewSessionPage({ onNavigate }: ReviewSessionPageProps) {
  const [queue, setQueue] = useState<ReviewCard[]>([]);
  const [index, setIndex] = useState(0);
  const [context, setContext] = useState<ReviewCardContext>();
  const [attempt, setAttempt] = useState<ReviewAttempt>();
  const [clientAttemptId, setClientAttemptId] = useState('');
  const [loading, setLoading] = useState(true);
  const [contextLoading, setContextLoading] = useState(false);
  const [submittingRating, setSubmittingRating] = useState<ReviewRating>();
  const [noteDirty, setNoteDirty] = useState(false);
  const [error, setError] = useState('');

  const current = queue[index];
  const finished = !loading && !error && queue.length === 0;
  const progressLabel = useMemo(() => (
    queue.length > 0 ? `${Math.min(index + 1, queue.length)} / ${queue.length}` : '0 / 0'
  ), [index, queue.length]);

  useEffect(() => {
    const controller = new AbortController();
    void loadQueue(controller.signal);
    return () => controller.abort();
  }, []);

  useEffect(() => {
    if (!current) {
      setContext(undefined);
      return;
    }
    const controller = new AbortController();
    void loadContext(current.id, controller.signal);
    return () => controller.abort();
  }, [current?.id]);

  useEffect(() => {
    function handleKeyDown(event: KeyboardEvent) {
      const rating = ratingKeys[event.key];
      if (!rating || !context || attempt || submittingRating) {
        return;
      }
      event.preventDefault();
      void handleRate(rating);
    }
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [attempt, context, submittingRating]);

  async function loadQueue(signal?: AbortSignal) {
    setLoading(true);
    setError('');
    try {
      const response = await getReviewQueue(20, signal);
      const data = requireApiData(response, '复习队列加载失败');
      setQueue(data.items);
      setIndex(0);
    } catch (loadError) {
      if (!(loadError instanceof DOMException && loadError.name === 'AbortError')) {
        setQueue([]);
        setError(loadError instanceof Error ? loadError.message : '复习队列加载失败');
      }
    } finally {
      if (!signal?.aborted) {
        setLoading(false);
      }
    }
  }

  async function loadContext(cardId: number, signal?: AbortSignal) {
    setContextLoading(true);
    setContext(undefined);
    setAttempt(undefined);
    setSubmittingRating(undefined);
    setNoteDirty(false);
    setClientAttemptId(generateClientId());
    setError('');
    try {
      const response = await getReviewCardContext(cardId, signal);
      setContext(requireApiData(response, '复习卡加载失败'));
    } catch (loadError) {
      if (!(loadError instanceof DOMException && loadError.name === 'AbortError')) {
        setError(loadError instanceof Error ? loadError.message : '复习卡加载失败');
      }
    } finally {
      if (!signal?.aborted) {
        setContextLoading(false);
      }
    }
  }

  async function handleRate(rating: ReviewRating) {
    if (!current || !context || attempt || submittingRating || !clientAttemptId) {
      return;
    }
    setSubmittingRating(rating);
    setError('');
    try {
      const response = await submitReviewAttempt(current.id, clientAttemptId, rating);
      setAttempt(requireApiData(response, '复习评级提交失败'));
    } catch (submitError) {
      setError(submitError instanceof Error ? submitError.message : '复习评级提交失败');
    } finally {
      setSubmittingRating(undefined);
    }
  }

  function handleBack() {
    if (!confirmDiscardNote()) {
      return;
    }
    onNavigate(APP_ROUTES.mistakes);
  }

  function nextCard() {
    if (!attempt || !confirmDiscardNote()) {
      return;
    }
    if (index + 1 >= queue.length) {
      setQueue([]);
      setIndex(0);
      return;
    }
    setIndex((currentIndex) => currentIndex + 1);
  }

  function confirmDiscardNote() {
    return !noteDirty || window.confirm('题目笔记还有未保存修改。点击“确定”放弃修改，点击“取消”返回保存。');
  }

  function intervalFor(rating: ReviewRating) {
    return context?.intervalPreviews.find((item) => item.rating === rating);
  }

  return (
    <section className="review-session-page" aria-labelledby="review-session-title">
      <header className="review-workbench-toolbar">
        <button className="secondary-button compact" onClick={handleBack} type="button">
          <ArrowLeft aria-hidden="true" />
          <span>返回复习中心</span>
        </button>
        <div className="review-workbench-heading">
          <h1 id="review-session-title">{context?.problem.titleCn || current?.problemTitle || current?.problemSlug || '间隔复习'}</h1>
          <span>{context?.problem.difficulty || current?.problemDifficulty || '难度未知'}</span>
        </div>
        <div className="review-workbench-status">
          <strong>{progressLabel}</strong>
          <span>{context?.card.fsrsState || current?.fsrsState || 'LEARNING'}</span>
        </div>
      </header>

      {error && <p className="error-text" role="alert">{error}</p>}

      {loading ? (
        <div className="loading-panel">正在准备复习队列...</div>
      ) : finished ? (
        <div className="review-complete">
          <CheckCircle2 aria-hidden="true" />
          <h2>今日待复习已完成</h2>
          <button className="primary-button" onClick={() => onNavigate(APP_ROUTES.mistakes)} type="button">
            返回复习中心
          </button>
        </div>
      ) : contextLoading || !context ? (
        <div className="loading-panel">正在加载完整题面...</div>
      ) : (
        <article className="review-card-workbench">
          <div className="review-card-scroll">
            <section className="review-problem-content" aria-label="完整题面">
              <MarkdownView content={context.problem.contentMarkdown} />
            </section>

            <ProblemNoteEditor onDirtyChange={setNoteDirty} problemSlug={context.problem.slug} />

            <details className="review-attempt-history">
              <summary>
                <span>
                  <strong>复习记录</strong>
                  <small>{context.recentAttempts.length > 0 ? `最近 ${context.recentAttempts.length} 次` : '暂无记录'}</small>
                </span>
              </summary>
              {context.recentAttempts.length > 0 ? (
                <ol>
                  {context.recentAttempts.map((history) => (
                    <li key={history.id}>
                      <strong>{ratingLabels[history.rating]}</strong>
                      <span>{formatDateTime(history.reviewedAt)}</span>
                      <small>
                        间隔 {history.schedulingBefore.intervalDays} 天 → {history.schedulingAfter.intervalDays} 天
                      </small>
                    </li>
                  ))}
                </ol>
              ) : (
                <p>完成本题评级后，记录会显示在这里。</p>
              )}
            </details>

            {attempt && (
              <section className="review-result" aria-label="复习确认结果">
                <h3>{ratingLabels[attempt.rating]}</h3>
                <p>下次复习：{formatDueLabel(attempt.schedulingAfter.dueAt, attempt.schedulingAfter.intervalDays)}</p>
              </section>
            )}
          </div>

          <footer className="review-rating-bar" aria-label="复习评级">
            <div className="review-rating-grid">
              {(Object.keys(ratingLabels) as ReviewRating[]).map((rating, ratingIndex) => (
                <RatingButton
                  description={ratingDescriptions[rating]}
                  interval={intervalFor(rating)}
                  key={rating}
                  loading={submittingRating === rating}
                  onClick={() => void handleRate(rating)}
                  rating={rating}
                  shortcut={ratingIndex + 1}
                  selected={attempt?.rating === rating}
                  submitted={Boolean(attempt)}
                />
              ))}
            </div>
            <button className="secondary-button review-next-button" disabled={!attempt} onClick={nextCard} type="button">
              <ArrowRight aria-hidden="true" />
              <span>下一题</span>
            </button>
          </footer>
        </article>
      )}
    </section>
  );
}

function RatingButton({
  description,
  interval,
  loading,
  onClick,
  rating,
  selected,
  shortcut,
  submitted,
}: {
  description: string;
  interval?: ReviewIntervalPreview;
  loading: boolean;
  onClick: () => void;
  rating: ReviewRating;
  selected: boolean;
  shortcut: number;
  submitted: boolean;
}) {
  const label = ratingLabels[rating];
  const intervalLabel = interval ? formatDueLabel(interval.dueAt, interval.intervalDays) : '计算中';
  const Icon = rating === 'AGAIN'
    ? RefreshCw
    : rating === 'HARD'
      ? AlertCircle
      : rating === 'GOOD'
        ? CheckCircle2
        : Sparkles;

  return (
    <button
      aria-keyshortcuts={String(shortcut)}
      aria-label={`${label}，${description}，${intervalLabel}`}
      className={`review-rating-button review-rating-button-${rating.toLowerCase()}${selected ? ' is-selected' : ''}`}
      disabled={submitted || loading}
      onClick={onClick}
      type="button"
    >
      {loading ? <Loader2 aria-hidden="true" className="review-rating-icon" /> : <Icon aria-hidden="true" className="review-rating-icon" />}
      <span className="review-rating-copy">
        <span className="review-rating-label">{label}</span>
        <small>{intervalLabel}</small>
      </span>
    </button>
  );
}

function formatDueLabel(dueAt: string, intervalDays: number) {
  if (intervalDays <= 0) {
    const minutes = Math.max(1, Math.round((new Date(dueAt).getTime() - Date.now()) / 60_000));
    return Number.isFinite(minutes) && minutes < 24 * 60 ? `${minutes} 分钟后` : '稍后复习';
  }
  if (intervalDays === 1) {
    return '明天复习';
  }
  return `${intervalDays} 天后复习`;
}

function formatDateTime(value: string) {
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString('zh-CN', { hour12: false });
}
