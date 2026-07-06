import { ArrowLeft, ArrowRight, CheckCircle2, Eye, Loader2 } from 'lucide-react';
import { useEffect, useMemo, useState } from 'react';
import { APP_ROUTES } from '../app/navigation';
import MarkdownView from '../components/MarkdownView';
import {
  confirmRecall,
  evaluateRecall,
  getReviewCard,
  getReviewProblemStatement,
  getReviewPreference,
  getReviewQueue,
  requireApiData,
  rateRecall,
} from '../services/api';
import type {
  MistakeNote,
  RecallConfirmResult,
  RecallEvaluationResult,
  ReviewCard,
  ReviewIntervalPreview,
  ReviewPreference,
  ReviewRating,
} from '../types/api';

interface ReviewSessionPageProps {
  onNavigate: (path: string) => void;
}

const ratingLabels: Record<ReviewRating, string> = {
  AGAIN: '重来',
  HARD: '困难',
  GOOD: '良好',
  EASY: '简单',
};

const ratingKeys: Record<string, ReviewRating> = {
  '1': 'AGAIN',
  '2': 'HARD',
  '3': 'GOOD',
  '4': 'EASY',
};

export default function ReviewSessionPage({ onNavigate }: ReviewSessionPageProps) {
  const [queue, setQueue] = useState<MistakeNote[]>([]);
  const [index, setIndex] = useState(0);
  const [card, setCard] = useState<ReviewCard>();
  const [recallText, setRecallText] = useState('');
  const [transientNote, setTransientNote] = useState('');
  const [reviewPreference, setReviewPreference] = useState<ReviewPreference>();
  const [evaluation, setEvaluation] = useState<RecallEvaluationResult>();
  const [confirmation, setConfirmation] = useState<RecallConfirmResult>();
  const [loading, setLoading] = useState(true);
  const [evaluating, setEvaluating] = useState(false);
  const [confirmingRating, setConfirmingRating] = useState<ReviewRating>();
  const [error, setError] = useState('');
  const [statementCache, setStatementCache] = useState<Map<number, string>>(new Map());
  const [statementLoading, setStatementLoading] = useState(false);
  const [statementError, setStatementError] = useState('');

  const current = queue[index];
  const maxChars = card?.scaffold?.maxInputChars ?? 400;
  const finished = !loading && !error && queue.length === 0;
  const loadBlocked = !loading && !current && Boolean(error);
  const currentTitle = current?.problemTitle || card?.problemRef.titleCn || current?.problemSlug;
  const statementSummary = card?.problemStatement?.summary?.trim();
  const revealed = Boolean(evaluation);
  const preferenceLoaded = Boolean(reviewPreference);
  const directRatingMode = reviewPreference?.aiSuggestionEnabled === false;
  const canShowRatingButtons = preferenceLoaded && (directRatingMode || Boolean(evaluation));
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
      setCard(undefined);
      setStatementError('');
      setStatementLoading(false);
      return;
    }
    const controller = new AbortController();
    void loadCard(current.id, controller.signal);
    return () => controller.abort();
  }, [current?.id]);

  useEffect(() => {
    function handleKeyDown(event: KeyboardEvent) {
      const rating = ratingKeys[event.key];
      if (!rating || !canShowRatingButtons || confirmation || confirmingRating) {
        return;
      }
      event.preventDefault();
      void handleConfirm(rating);
    }
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [canShowRatingButtons, confirmation, confirmingRating]);

  async function loadQueue(signal?: AbortSignal) {
    setLoading(true);
    setError('');
    setReviewPreference(undefined);
    try {
      const [preferenceResponse, queueResponse] = await Promise.all([
        getReviewPreference(signal),
        getReviewQueue(20, signal),
      ]);
      const preference = requireApiData(preferenceResponse, '复习偏好加载失败');
      const queueData = requireApiData(queueResponse, '复习队列加载失败');
      setReviewPreference(preference);
      setQueue(queueData.items);
      setIndex(0);
    } catch (loadError) {
      if (!(loadError instanceof DOMException && loadError.name === 'AbortError')) {
        setQueue([]);
        setError(loadError instanceof Error ? loadError.message : '复习偏好或队列加载失败，请重试');
      }
    } finally {
      setLoading(false);
    }
  }

  async function loadCard(noteId: number, signal?: AbortSignal) {
    setError('');
    setCard(undefined);
    setEvaluation(undefined);
    setConfirmation(undefined);
    setRecallText('');
    setTransientNote('');
    setConfirmingRating(undefined);
    setStatementError('');
    setStatementLoading(false);
    try {
      const response = await getReviewCard(noteId, signal);
      setCard(requireApiData(response, '复习卡加载失败'));
    } catch (loadError) {
      if (!(loadError instanceof DOMException && loadError.name === 'AbortError')) {
        setError(loadError instanceof Error ? loadError.message : '复习卡加载失败');
      }
    }
  }

  async function handleEvaluate() {
    if (!current || !preferenceLoaded || directRatingMode || !recallText.trim() || evaluating) {
      if (!preferenceLoaded) {
        setError('复习偏好尚未加载完成，请刷新后重试。');
      }
      return;
    }
    setEvaluating(true);
    setError('');
    try {
      const response = await evaluateRecall(current.id, recallText.trim(), transientNote.trim() || undefined);
      setEvaluation(requireApiData(response, '复述评估失败'));
    } catch (submitError) {
      setError(submitError instanceof Error ? submitError.message : '复述评估失败');
    } finally {
      setEvaluating(false);
    }
  }

  async function handleConfirm(rating: ReviewRating) {
    if (!current || !preferenceLoaded || confirmation || confirmingRating || (!directRatingMode && !evaluation)) {
      if (!preferenceLoaded) {
        setError('复习偏好尚未加载完成，请刷新后重试。');
      }
      return;
    }
    setConfirmingRating(rating);
    setError('');
    try {
      const response = directRatingMode
        ? await rateRecall(current.id, rating)
        : await confirmRecall(current.id, evaluation!.evaluationId, rating);
      setConfirmation(requireApiData(response, '复习评级提交失败'));
    } catch (submitError) {
      setError(submitError instanceof Error ? submitError.message : '复习评级提交失败');
    } finally {
      setConfirmingRating(undefined);
    }
  }

  async function loadProblemStatement(noteId: number, signal?: AbortSignal) {
    if (statementCache.has(noteId) || statementLoading) {
      return;
    }
    setStatementLoading(true);
    setStatementError('');
    try {
      const response = await getReviewProblemStatement(noteId, signal);
      const data = requireApiData(response, '题目原文加载失败');
      setStatementCache((currentCache) => {
        const nextCache = new Map(currentCache);
        nextCache.set(noteId, data.contentMarkdown);
        return nextCache;
      });
    } catch (loadError) {
      if (!(loadError instanceof DOMException && loadError.name === 'AbortError')) {
        setStatementError(loadError instanceof Error ? loadError.message : '未找到题目原文');
      }
    } finally {
      setStatementLoading(false);
    }
  }

  function nextCard() {
    if (index + 1 >= queue.length) {
      setQueue([]);
      setIndex(0);
      return;
    }
    setIndex((currentIndex) => currentIndex + 1);
  }

  function intervalFor(rating: ReviewRating) {
    return evaluation?.intervals.find((item) => item.rating === rating);
  }

  return (
    <section className="review-session-page" aria-labelledby="review-session-title">
      <header className="mistake-header">
        <div>
          <p className="eyebrow">FSRS Review</p>
          <h1 id="review-session-title">间隔复习</h1>
        </div>
        <button className="secondary-button" onClick={() => onNavigate(APP_ROUTES.mistakes)} type="button">
          <ArrowLeft aria-hidden="true" />
          <span>返回复习中心</span>
        </button>
      </header>

      {error && <p className="error-text" role="alert">{error}</p>}

      {loading ? (
        <div className="loading-panel">正在准备复习队列...</div>
      ) : loadBlocked ? (
        <div className="loading-panel">复习偏好或队列加载失败，请稍后重试。</div>
      ) : finished ? (
        <div className="review-complete">
          <CheckCircle2 aria-hidden="true" />
          <h2>今日待复习已完成</h2>
          <button className="primary-button" onClick={() => onNavigate(APP_ROUTES.mistakes)} type="button">
            返回复习中心
          </button>
        </div>
      ) : (
        <article className="review-card-panel">
          <div className="review-card-topline">
            <span>{progressLabel}</span>
            <span>{current?.fsrsState ?? card?.cardVariant ?? '...'}</span>
          </div>
          <h2>{currentTitle}</h2>

          {!revealed && (
            <section className="review-card-front" aria-label="复习卡正面">
              <p className="review-card-summary">
                先根据题目正面独立复述核心思路、关键不变量、复杂度和容易错的边界，暂不查看旧代码或题解。
              </p>
              {statementSummary && (
                <p className="review-card-summary">
                  <strong>题面摘要：</strong>
                  {statementSummary}
                </p>
              )}
            </section>
          )}

          {current && (
            <details
              className="review-problem-full"
              onToggle={(event) => {
                if (event.currentTarget.open) {
                  void loadProblemStatement(current.id);
                }
              }}
            >
              <summary>
                <Eye aria-hidden="true" />
                <span>查看题面</span>
              </summary>
              {statementLoading && !statementCache.has(current.id) ? (
                <p className="review-card-summary">正在加载题目原文...</p>
              ) : statementError ? (
                <p className="review-card-summary">{statementError}</p>
              ) : statementCache.has(current.id) ? (
                <MarkdownView content={statementCache.get(current.id) ?? ''} />
              ) : null}
            </details>
          )}

          {revealed && <p className="review-card-summary">{card?.contextSummary ?? '正在加载复习卡...'}</p>}

          {card?.prompts && card.prompts.length > 0 && (
            <ol className="review-prompts">
              {card.prompts.map((prompt) => (
                <li key={prompt.key}>
                  <strong>{prompt.label}</strong>
                  {prompt.hint && <span>{prompt.hint}</span>}
                </li>
              ))}
            </ol>
          )}

          {card?.scaffold && (
            <pre className="review-scaffold">{card.scaffold.templateMarkdown}</pre>
          )}

          {!directRatingMode && (
            <>
              <label className="review-input-label">
                <span>你的复述</span>
                <textarea
                  disabled={revealed}
                  maxLength={maxChars}
                  onChange={(event) => setRecallText(event.target.value)}
                  rows={8}
                  value={recallText}
                />
              </label>
              <div className="review-input-meta">{recallText.length} / {maxChars}</div>

              <label className="review-input-label">
                <span>本次备注</span>
                <textarea
                  disabled={revealed}
                  maxLength={2000}
                  onChange={(event) => setTransientNote(event.target.value)}
                  rows={3}
                  value={transientNote}
                />
              </label>
            </>
          )}

          {evaluation && (
            <section className="review-result" aria-label="复述评估结果">
              <h3>
                {evaluation.aiSuggested && evaluation.suggestedRating
                  ? `AI 建议：${ratingLabels[evaluation.suggestedRating]}`
                  : '请自评本次回忆'}
              </h3>
              {evaluation.gapSummary && <p>{evaluation.gapSummary}</p>}
              <dl>
                <div>
                  <dt>命中点</dt>
                  <dd>{evaluation.hitPoints.join('；') || '未记录'}</dd>
                </div>
                <div>
                  <dt>遗漏点</dt>
                  <dd>{evaluation.missedPoints.join('；') || '未记录'}</dd>
                </div>
              </dl>
            </section>
          )}

          {confirmation && (
            <section className="review-result" aria-label="复习确认结果">
              <h3>{ratingLabels[confirmation.rating]}</h3>
              <p>下次复习：{confirmation.intervalDays} 天后</p>
            </section>
          )}

          <div className="review-actions">
            {!directRatingMode && !evaluation ? (
              <button
                className="primary-button"
                disabled={!card || !preferenceLoaded || !recallText.trim() || evaluating}
                onClick={() => void handleEvaluate()}
                type="button"
              >
                {evaluating && <Loader2 aria-hidden="true" />}
                <span>{evaluating ? '评估中' : '揭示并评估'}</span>
              </button>
            ) : canShowRatingButtons ? (
              (['AGAIN', 'HARD', 'GOOD', 'EASY'] as ReviewRating[]).map((rating, ratingIndex) => (
                <RatingButton
                  interval={intervalFor(rating)}
                  key={rating}
                  loading={confirmingRating === rating}
                  onClick={() => void handleConfirm(rating)}
                  rating={rating}
                  shortcut={ratingIndex + 1}
                  selected={confirmation?.rating === rating}
                  submitted={Boolean(confirmation)}
                />
              ))
            ) : null}
            <button className="secondary-button" disabled={!confirmation} onClick={nextCard} type="button">
              <ArrowRight aria-hidden="true" />
              <span>下一题</span>
            </button>
          </div>
        </article>
      )}
    </section>
  );
}

function RatingButton({
  interval,
  loading,
  onClick,
  rating,
  selected,
  shortcut,
  submitted,
}: {
  interval?: ReviewIntervalPreview;
  loading: boolean;
  onClick: () => void;
  rating: ReviewRating;
  selected: boolean;
  shortcut: number;
  submitted: boolean;
}) {
  return (
    <button
      className={selected ? 'primary-button compact' : 'secondary-button compact'}
      disabled={submitted || loading}
      onClick={onClick}
      type="button"
    >
      {loading && <Loader2 aria-hidden="true" />}
      <span>{shortcut}. {ratingLabels[rating]}</span>
      <small>{interval ? `${interval.intervalDays} 天` : ''}</small>
    </button>
  );
}
