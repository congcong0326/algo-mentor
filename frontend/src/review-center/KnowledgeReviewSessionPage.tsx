import { CheckCircle2 } from 'lucide-react';
import { useEffect, useLayoutEffect, useRef, useState } from 'react';
import { reviewCenterPath, reviewCenterSearchOptionsFromSearch } from '../app/navigation';
import KnowledgeCardContent from '../components/knowledge/KnowledgeCardContent';
import { useI18n } from '../i18n/I18nProvider';
import { allKnowledgePages, knowledgeApi } from '../services/knowledge';
import type { KnowledgeCard, KnowledgeReviewPreview, KnowledgeReviewResult } from '../types/knowledge';
import type { ReviewRating } from '../types/api';
import { generateClientId } from '../utils/id';
import ReviewRatingBar, { useCompactReview, useReviewShortcuts } from './ReviewRatingBar';
import ReviewWorkbench from './ReviewWorkbench';

export default function KnowledgeReviewSessionPage({ onNavigate, search = '' }: { onNavigate: (path: string) => void; search?: string }) {
  const { resources, locale } = useI18n();
  const compact = useCompactReview();
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
  const sessionRef = useRef<HTMLElement>(null);
  const answerRef = useRef<HTMLDivElement>(null);
  const scrollToQuestion = useRef(false);
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

  useLayoutEffect(() => {
    if (!compact || !answerVisible) return;
    answerRef.current?.focus({ preventScroll: true });
    answerRef.current?.scrollIntoView?.({ block: 'start', behavior: 'instant' });
  }, [compact, answerVisible]);

  useLayoutEffect(() => {
    if (!scrollToQuestion.current || (current && !context && !detailError)) return;
    scrollToQuestion.current = false;
    if (!compact) return;
    sessionRef.current?.focus({ preventScroll: true });
    sessionRef.current?.scrollIntoView?.({ block: 'start', behavior: 'instant' });
  }, [compact, context, current, detailError]);

  function next() {
    if (!result) return;
    scrollToQuestion.current = compact;
    setContext(undefined); setResult(undefined); setAnswerVisible(false);
    setIndex((value) => value + 1);
  }

  const phase = context?.card.learningState.phase;
  const status = phase && phase in resources.reviewCenter.fsrsStateLabels
    ? resources.reviewCenter.fsrsStateLabels[phase as keyof typeof resources.reviewCenter.fsrsStateLabels]
    : resources.reviewCenter.fsrsStateLabels.LEARNING;
  const nextReview = result && resources.reviewCenter.nextReview(new Date(result.dueAt).toLocaleString(locale, { hour12: false }));
  const showAnswerButton = <button className="primary-button" onClick={() => setAnswerVisible(true)} type="button">显示答案</button>;

  return <section className="review-session-page" aria-labelledby="review-session-title" ref={sessionRef} tabIndex={compact ? -1 : undefined}>
    {loading ? <div className="loading-panel">{resources.reviewCenter.preparingQueue}</div>
      : queueError || detailError ? <div className="loading-panel">
        <p role="alert" className="error-text">{queueError || detailError}</p>
        <button className="secondary-button" onClick={back} type="button">{resources.reviewCenter.backToReviewCenter}</button>
        <button className="primary-button" onClick={() => queueError ? setReload((value) => value + 1) : setDetailReload((value) => value + 1)} type="button">重新加载</button>
      </div>
        : !current ? <div className="review-complete"><CheckCircle2 aria-hidden="true" /><h2 id="review-session-title">{resources.reviewCenter.queueCompleted}</h2>
          <button className="primary-button" onClick={back} type="button">{resources.reviewCenter.backToReviewCenter}</button>
        </div>
          : !context ? <div className="loading-panel">{resources.reviewCenter.loadingDetail}</div>
            : <ReviewWorkbench title={context.card.question} subtitle="八股文" progress={`${index + 1} / ${queue.length}`} status={status} onBack={back} pageScroll
              footer={compact && !answerVisible ? <footer className="review-rating-bar">{showAnswerButton}</footer>
                : <ReviewRatingBar intervals={context.preview.options} compact={compact} submittingRating={submittingRating} selectedRating={result?.rating}
                  nextReview={nextReview} error={compact ? submitError : undefined} disabled={!answerVisible} retryRating={retryRating}
                  onRate={(rating) => void rate(rating)} onNext={next} />}>
              <section className="review-problem-content" aria-label="知识卡题目">{!compact && <p>{context.card.question}</p>}
                <div className="knowledge-tags">{context.card.tags.map((tag) => <span key={tag}>{tag}</span>)}</div>
              </section>
              {answerVisible ? <div ref={answerRef} tabIndex={compact ? -1 : undefined} role="region" aria-label="知识卡答案">
                <KnowledgeCardContent card={context.card} />
              </div> : <div className="knowledge-answer-gate"><p>先独立回忆，再查看解析。</p>{!compact && showAnswerButton}</div>}
              {!compact && submitError && <p className="error-text" role="alert">{submitError}</p>}
              {!compact && result && <section className="review-result" aria-label={resources.reviewCenter.resultAriaLabel}>
                <h3>{resources.reviewCenter.ratingLabels[result.rating]}</h3>
                <p>{nextReview}</p>
              </section>}
            </ReviewWorkbench>}
  </section>;
}
