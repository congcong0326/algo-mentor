import MarkdownView from '../components/MarkdownView';
import type { LocaleResources } from '../i18n/locales';
import type { PracticeCodeReviewDetail } from '../types/api';
import ReviewScoreBadge from './ReviewScoreBadge';

interface ReviewDetailPanelProps {
  detail: PracticeCodeReviewDetail;
  passScore?: number;
  resources: LocaleResources;
}

export default function ReviewDetailPanel({ detail, passScore, resources }: ReviewDetailPanelProps) {
  const codeSnapshot = detail.submittedCode ?? detail.normalizedCode ?? detail.rawCode ?? '';
  const evidence = detail.evidence ?? [];
  const deductionReasons = detail.deductionReasons ?? [];
  const improvementSuggestions = detail.improvementSuggestions ?? [];

  return (
    <section className="review-detail-panel" aria-label={resources.learningPlans.reviewVersionLabel(detail.versionNo)}>
      <header className="review-detail-heading">
        <div>
          <p className="eyebrow">{resources.learningPlans.reviewVersionLabel(detail.versionNo)}</p>
          <h3>{detail.language}</h3>
        </div>
        <ReviewScoreBadge
          passed={detail.passed}
          passScore={passScore}
          resources={resources}
          score={detail.scores.total}
        />
      </header>

      <MarkdownView content={detail.reviewMarkdown} />

      {codeSnapshot && (
        <section className="review-detail-section">
          <h4>{resources.learningPlans.reviewCodeSnapshot}</h4>
          <pre><code>{codeSnapshot}</code></pre>
        </section>
      )}

      <ReviewTextList title={resources.learningPlans.reviewDeductionReasons} items={deductionReasons} />
      <ReviewTextList title={resources.learningPlans.reviewImprovementSuggestions} items={improvementSuggestions} />

      {evidence.length > 0 && (
        <section className="review-detail-section">
          <h4>{resources.learningPlans.reviewEvidence}</h4>
          <ul>
            {evidence.map((item) => (
              <li key={`${item.type}-${item.value}`}>
                <strong>{item.type}</strong>
                <span>{item.value}</span>
              </li>
            ))}
          </ul>
        </section>
      )}

      {detail.contextSummary && (
        <section className="review-detail-section">
          <h4>{resources.learningPlans.reviewContextSummary}</h4>
          <p>{detail.contextSummary}</p>
        </section>
      )}
    </section>
  );
}

function ReviewTextList({ items, title }: { items: string[]; title: string }) {
  if (items.length === 0) {
    return null;
  }

  return (
    <section className="review-detail-section">
      <h4>{title}</h4>
      <ul>
        {items.map((item) => <li key={item}>{item}</li>)}
      </ul>
    </section>
  );
}
