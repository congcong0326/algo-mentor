import { ArrowLeft, BookOpenCheck, CheckCircle2, Clock3, RotateCcw } from 'lucide-react';
import { useEffect, useMemo, useRef, useState } from 'react';
import { APP_ROUTES } from './app/navigation';
import KnowledgeCardContent from './components/knowledge/KnowledgeCardContent';
import { useI18n } from './i18n/I18nProvider';

import { allKnowledgePages, knowledgeApi } from './services/knowledge';
import type { KnowledgeCard, KnowledgeReviewSummary as ReviewSummary } from './types/knowledge';

export default function KnowledgeReviewCenterPage({ onNavigate }: { onNavigate: (path: string) => void }) {
  const { resources } = useI18n();
  const [summary, setSummary] = useState<ReviewSummary>();
  const [queue, setQueue] = useState<KnowledgeCard[]>([]);
  const [index, setIndex] = useState(0);
  const [currentDetail, setCurrentDetail] = useState<KnowledgeCard>();
  const [answerVisible, setAnswerVisible] = useState(false);
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');
  const attempt = useRef<{ slug: string; rating: string; id: string } | undefined>(undefined);
  const current = queue[index];
  const completed = queue.length > 0 && index >= queue.length;
  const ratings = ['AGAIN', 'HARD', 'GOOD', 'EASY'] as const;

  async function load() {
    setLoading(true); setError(''); setAnswerVisible(false); setIndex(0); setCurrentDetail(undefined);
    try {
      const [nextSummary, cards] = await Promise.all([
        knowledgeApi.summary(),
        allKnowledgePages((page) => knowledgeApi.queue(page)),
      ]);
      setSummary(nextSummary); setQueue(cards);
    } catch (e) { setError(e instanceof Error ? e.message : '知识库复习加载失败'); }
    finally { setLoading(false); }
  }
  useEffect(() => { void load(); }, []);

  useEffect(() => {
    if (!current) { setCurrentDetail(undefined); return; }
    let active = true;
    setCurrentDetail(undefined);
    knowledgeApi.card(current.slug).then((detail) => {
      if (active) setCurrentDetail(detail);
    }).catch((e) => { if (active) setError(e instanceof Error ? e.message : '知识卡加载失败'); });
    return () => { active = false; };
  }, [current?.slug]);

  async function rate(rating: string) {
    if (!current || submitting) return;
    setSubmitting(true); setError('');
    if (attempt.current?.slug !== current.slug || attempt.current.rating !== rating) attempt.current = { slug: current.slug, rating, id: crypto.randomUUID() };
    try {
      await knowledgeApi.review(current.slug, rating, attempt.current.id);
      attempt.current = undefined;
      setIndex((value) => value + 1); setAnswerVisible(false); setCurrentDetail(undefined); setSummary((value) => value ? { ...value, dueCount: Math.max(0, value.dueCount - 1) } : value);
    } catch (e) { setError(e instanceof Error ? e.message : '复习评级提交失败'); }
    finally { setSubmitting(false); }
  }

  const progress = useMemo(() => queue.length ? `${Math.min(index + 1, queue.length)} / ${queue.length}` : '0 / 0', [index, queue.length]);
  return <section className="knowledge-review-page" aria-labelledby="knowledge-review-title">
    <header className="knowledge-review-header"><button className="secondary-button compact" onClick={() => onNavigate(APP_ROUTES.knowledge)} type="button"><ArrowLeft aria-hidden="true" /> 返回知识库</button><div><p className="knowledge-eyebrow"><BookOpenCheck aria-hidden="true" /> 知识库</p><h1 id="knowledge-review-title">知识库复习中心</h1><p>按照 FSRS 评级节奏复习八股文卡片，复习结果会自动安排下一次复习。</p></div><button className="secondary-button compact" disabled={loading || submitting} onClick={() => void load()} type="button"><RotateCcw aria-hidden="true" /> 刷新队列</button></header>
    <dl className="knowledge-review-stats"><div><dt>今日待复习</dt><dd>{summary?.dueCount ?? '—'}</dd></div><div><dt>已加入复习</dt><dd>{summary?.enrolledCount ?? '—'}</dd></div><div><dt>当前进度</dt><dd>{progress}</dd></div></dl>
    {error && <p className="error-text" role="alert">{error}</p>}
    {loading ? <div className="loading-panel" role="status">正在准备知识库复习队列…</div> : completed || !current ? <article className="knowledge-review-empty learning-panel"><CheckCircle2 aria-hidden="true" /><h2>{queue.length ? '今日知识卡复习已完成' : '今天没有待复习知识卡'}</h2><p>{queue.length ? '很好，稍后可以回到知识库继续浏览新卡片。' : '先在知识库查看卡片并完成首次评级，系统会将它加入复习队列。'}</p><div className="button-row"><button className="primary-button" onClick={() => onNavigate(APP_ROUTES.knowledge)} type="button">浏览知识库</button><button className="secondary-button" onClick={() => void load()} type="button">重新检查</button></div></article> : <article className="knowledge-review-card learning-panel"><div className="knowledge-review-card-topline"><span>第 {progress} 张</span><span className="status-pill"><Clock3 aria-hidden="true" /> {current.learningState.phase || '复习中'}</span></div><h2>{current.question}</h2>{!answerVisible ? <div className="knowledge-review-gate"><p>先独立回忆，再查看解析。</p><button className="primary-button" disabled={!currentDetail} onClick={() => setAnswerVisible(true)} type="button">{currentDetail ? '显示答案' : '正在加载答案…'}</button></div> : <><div className="knowledge-review-answer">{currentDetail && <KnowledgeCardContent card={currentDetail} />}</div><div className="knowledge-rating-grid" aria-label={resources.reviewCenter.ratingAriaLabel}>{ratings.map((rating) => <button className={`review-rating-button review-rating-button-${rating.toLowerCase()}`} disabled={submitting} key={rating} onClick={() => void rate(rating)} type="button"><strong>{resources.reviewCenter.ratingLabels[rating]}</strong><small>{resources.reviewCenter.ratingDescriptions[rating]}</small></button>)}</div></>}</article>}
  </section>;
}
