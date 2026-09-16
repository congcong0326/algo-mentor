import { CheckCircle2 } from 'lucide-react';
import { useEffect, useRef, useState } from 'react';
import { reviewCenterPath, reviewCenterSearchOptionsFromSearch } from '../app/navigation';
import KnowledgeCardContent from '../components/knowledge/KnowledgeCardContent';
import { useI18n } from '../i18n/I18nProvider';
import { allKnowledgePages, knowledgeApi } from '../services/knowledge';
import type { KnowledgeCard, KnowledgeReviewPreview, KnowledgeReviewResult } from '../types/knowledge';
import type { ReviewRating } from '../types/api';
import { generateClientId } from '../utils/id';
import ReviewRatingBar, { useReviewShortcuts } from './ReviewRatingBar';
import ReviewWorkbench from './ReviewWorkbench';

export default function KnowledgeReviewSessionPage({ onNavigate, search = '' }: { onNavigate: (path: string) => void; search?: string }) {
  const { resources, locale } = useI18n();
  const [queue, setQueue] = useState<KnowledgeCard[]>([]);
  const [index, setIndex] = useState(0);
  const [loading, setLoading] = useState(true);
  const [queueError, setQueueError] = useState('');
  const [detailError, setDetailError] = useState('');
  const [submitError, setSubmitError] = useState('');
  const [context, setContext] = useState<{ card: KnowledgeCard; preview: KnowledgeReviewPreview }>();
  const [answerVisible, setAnswerVisible] = useState(false);
  const [result, setResult] = useState<KnowledgeReviewResult>();
  const [submittingRating, setSubmittingRating] = useState<ReviewRating>();
  const [retryRating, setRetryRating] = useState<ReviewRating>();
  const [reload, setReload] = useState(0);
  const [detailReload, setDetailReload] = useState(0);
  const pending = useRef(false);
  const attempt = useRef<{ slug: string; rating: ReviewRating; id: string } | undefined>(undefined);
  const mounted = useRef(true);
  const current = queue[index];
  const back = () => onNavigate(reviewCenterPath({ ...reviewCenterSearchOptionsFromSearch(search), mode: 'knowledge' }));

  useEffect(() => {
    mounted.current = true;
    return () => { mounted.current = false; };
  }, []);

  useEffect(() => {
    let active = true;
    setLoading(true); setQueueError('');
    allKnowledgePages((page) => knowledgeApi.queue(page)).then((cards) => {
      if (active) { setQueue(cards); setIndex(0); }
    }).catch((e) => {
      if (active) setQueueError(e instanceof Error ? e.message : resources.reviewCenter.queueLoadFailed);
    }).finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [reload]);

  useEffect(() => {
    let active = true;
    setContext(undefined); setDetailError(''); setSubmitError(''); setAnswerVisible(false); setResult(undefined); setRetryRating(undefined);
    attempt.current = undefined;
    if (current) {
      Promise.all([knowledgeApi.card(current.slug), knowledgeApi.reviewPreview(current.slug)]).then(([card, preview]) => {
        if (active) setContext({ card, preview });
      }).catch((e) => { if (active) setDetailError(e instanceof Error ? e.message : resources.reviewCenter.detailLoadFailed); });
    }
    return () => { active = false; };
  }, [current?.slug, detailReload]);

  async function rate(rating: ReviewRating) {
    if (!current || !context || !answerVisible || result || pending.current || (retryRating && retryRating !== rating)) return;
    pending.current = true; setSubmittingRating(rating); setSubmitError('');
    // 请求结果未知时保留同一评级与 UUID，只能原样重试。
    attempt.current ??= { slug: current.slug, rating, id: generateClientId() };
    try {
      const data = await knowledgeApi.review(attempt.current.slug, attempt.current.rating, attempt.current.id);
      if (mounted.current) { setResult(data); setRetryRating(undefined); }
    } catch (e) {
      if (mounted.current) {
        setRetryRating(rating);
        setSubmitError(`${e instanceof Error ? e.message : resources.reviewCenter.ratingSubmitFailed}。请重试本次评级。`);
      }
    } finally {
      pending.current = false;
      if (mounted.current) setSubmittingRating(undefined);
    }
  }

  useReviewShortcuts(Boolean(context && answerVisible && !result && !submittingRating), (rating) => void rate(rating));

  function next() {
    if (!result) return;
    setContext(undefined); setResult(undefined); setAnswerVisible(false);
    setIndex((value) => value + 1);
  }

  const phase = context?.card.learningState.phase;
  const status = phase && phase in resources.reviewCenter.fsrsStateLabels
    ? resources.reviewCenter.fsrsStateLabels[phase as keyof typeof resources.reviewCenter.fsrsStateLabels]
    : resources.reviewCenter.fsrsStateLabels.LEARNING;

  return <section className="review-session-page" aria-labelledby="review-session-title">
    {loading ? <div className="loading-panel">{resources.reviewCenter.preparingQueue}</div>
      : queueError || detailError ? <div className="loading-panel">
        <p role="alert" className="error-text">{queueError || detailError}</p>
        <button className="secondary-button" onClick={back} type="button">{resources.reviewCenter.backToReviewCenter}</button>
        <button className="primary-button" onClick={() => queueError ? setReload((value) => value + 1) : setDetailReload((value) => value + 1)} type="button">重新加载</button>
      </div>
        : !current ? <div className="review-complete"><CheckCircle2 aria-hidden="true" /><h2>{resources.reviewCenter.queueCompleted}</h2>
          <button className="primary-button" onClick={back} type="button">{resources.reviewCenter.backToReviewCenter}</button>
        </div>
          : !context ? <div className="loading-panel">{resources.reviewCenter.loadingDetail}</div>
            : <ReviewWorkbench title={context.card.question} subtitle="八股文" progress={`${index + 1} / ${queue.length}`} status={status} onBack={back}
              footer={<ReviewRatingBar intervals={context.preview.options} submittingRating={submittingRating} selectedRating={result?.rating}
                disabled={!answerVisible} retryRating={retryRating} onRate={(rating) => void rate(rating)} onNext={next} />}>
              <section className="review-problem-content" aria-label="知识卡题目"><p>{context.card.question}</p>
                <div className="knowledge-tags">{context.card.tags.map((tag) => <span key={tag}>{tag}</span>)}</div>
              </section>
              {!answerVisible ? <div className="knowledge-answer-gate"><p>先独立回忆，再查看解析。</p>
                <button className="primary-button" onClick={() => setAnswerVisible(true)} type="button">显示答案</button>
              </div> : <KnowledgeCardContent card={context.card} />}
              {submitError && <p className="error-text" role="alert">{submitError}</p>}
              {result && <section className="review-result" aria-label={resources.reviewCenter.resultAriaLabel}>
                <h3>{resources.reviewCenter.ratingLabels[result.rating]}</h3>
                <p>{resources.reviewCenter.nextReview(new Date(result.dueAt).toLocaleString(locale, { hour12: false }))}</p>
              </section>}
            </ReviewWorkbench>}
  </section>;
}
