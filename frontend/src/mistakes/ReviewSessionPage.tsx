import { ArrowLeft, ArrowRight, CheckCircle2, Loader2 } from 'lucide-react';
import { useEffect, useMemo, useState } from 'react';
import { APP_ROUTES } from '../app/navigation';
import {
  getReviewCard,
  getReviewQueue,
  requireApiData,
  submitRecall,
} from '../services/api';
import type { MistakeNote, RecallReviewResult, ReviewCard } from '../types/api';

interface ReviewSessionPageProps {
  onNavigate: (path: string) => void;
}

const gradeLabels: Record<string, string> = {
  FORGOT: '忘了',
  BARELY: '勉强想起',
  MASTERED: '掌握',
  FLUENT: '熟练',
};

export default function ReviewSessionPage({ onNavigate }: ReviewSessionPageProps) {
  const [queue, setQueue] = useState<MistakeNote[]>([]);
  const [index, setIndex] = useState(0);
  const [card, setCard] = useState<ReviewCard>();
  const [recallText, setRecallText] = useState('');
  const [transientNote, setTransientNote] = useState('');
  const [result, setResult] = useState<RecallReviewResult>();
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');

  const current = queue[index];
  const maxChars = card?.scaffold?.maxInputChars ?? 400;
  const finished = !loading && queue.length === 0;
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
      return;
    }
    const controller = new AbortController();
    void loadCard(current.id, controller.signal);
    return () => controller.abort();
  }, [current?.id]);

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
        setError(loadError instanceof Error ? loadError.message : '复习队列加载失败');
      }
    } finally {
      setLoading(false);
    }
  }

  async function loadCard(noteId: number, signal?: AbortSignal) {
    setError('');
    setCard(undefined);
    setResult(undefined);
    setRecallText('');
    setTransientNote('');
    try {
      const response = await getReviewCard(noteId, signal);
      setCard(requireApiData(response, '复习卡加载失败'));
    } catch (loadError) {
      if (!(loadError instanceof DOMException && loadError.name === 'AbortError')) {
        setError(loadError instanceof Error ? loadError.message : '复习卡加载失败');
      }
    }
  }

  async function handleSubmit() {
    if (!current || !recallText.trim() || submitting) {
      return;
    }
    setSubmitting(true);
    setError('');
    try {
      const response = await submitRecall(current.id, recallText.trim(), transientNote.trim() || undefined);
      setResult(requireApiData(response, '复述提交失败'));
    } catch (submitError) {
      setError(submitError instanceof Error ? submitError.message : '复述提交失败');
    } finally {
      setSubmitting(false);
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

  return (
    <section className="review-session-page" aria-labelledby="review-session-title">
      <header className="mistake-header">
        <div>
          <p className="eyebrow">Recall Review</p>
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
            <span>{card?.cardVariant ?? '...'}</span>
          </div>
          <h2>{card?.problemRef.titleCn || current?.problemSlug}</h2>
          <p className="review-card-summary">{card?.contextSummary ?? '正在加载复习卡...'}</p>

          <ol className="review-prompts">
            {(card?.prompts ?? []).map((prompt) => (
              <li key={prompt.key}>
                <strong>{prompt.label}</strong>
                {prompt.hint && <span>{prompt.hint}</span>}
              </li>
            ))}
          </ol>

          {card?.scaffold && (
            <pre className="review-scaffold">{card.scaffold.templateMarkdown}</pre>
          )}

          <label className="review-input-label">
            <span>你的复述</span>
            <textarea
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
              maxLength={2000}
              onChange={(event) => setTransientNote(event.target.value)}
              rows={3}
              value={transientNote}
            />
          </label>

          {result && (
            <section className="review-result" aria-label="复述判定结果">
              <h3>{gradeLabels[result.grade] ?? result.grade}</h3>
              <p>{result.gapSummary}</p>
              <dl>
                <div>
                  <dt>下次复习</dt>
                  <dd>{result.intervalDays} 天后</dd>
                </div>
                <div>
                  <dt>命中点</dt>
                  <dd>{result.hitPoints.join('；') || '未记录'}</dd>
                </div>
                <div>
                  <dt>遗漏点</dt>
                  <dd>{result.missedPoints.join('；') || '未记录'}</dd>
                </div>
              </dl>
            </section>
          )}

          <div className="review-actions">
            <button
              className="primary-button"
              disabled={!card || !recallText.trim() || submitting || !!result}
              onClick={() => void handleSubmit()}
              type="button"
            >
              {submitting && <Loader2 aria-hidden="true" />}
              <span>{submitting ? '判定中' : '我讲完了'}</span>
            </button>
            <button className="secondary-button" disabled={!result} onClick={nextCard} type="button">
              <ArrowRight aria-hidden="true" />
              <span>下一题</span>
            </button>
          </div>
        </article>
      )}
    </section>
  );
}
