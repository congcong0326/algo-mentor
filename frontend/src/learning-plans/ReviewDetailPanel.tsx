import { useId, useState } from 'react';
import MarkdownView from '../components/MarkdownView';
import type { LocaleResources } from '../i18n/locales';
import type { PracticeCodeReviewDetail } from '../types/api';
import ReviewScoreBadge from './ReviewScoreBadge';

type ReviewScoreDimension = keyof LocaleResources['learningPlans']['reviewScoreDimensions'];
type ReviewScoreLevel = keyof LocaleResources['learningPlans']['reviewScoreLevels'];
type ReviewScoreBasis = keyof LocaleResources['learningPlans']['reviewScoreAnalysisBasis'];

const REVIEW_SCORE_DIMENSIONS: ReadonlyArray<{ key: ReviewScoreDimension; maximum: number }> = [
  { key: 'correctness', maximum: 4 },
  { key: 'complexity', maximum: 2 },
  { key: 'edgeCases', maximum: 2 },
  { key: 'codeQuality', maximum: 1 },
  { key: 'problemFit', maximum: 1 },
];

const REVIEW_SCORE_EXPLANATION_TYPES: Record<ReviewScoreDimension, string> = {
  correctness: 'SCORE_CORRECTNESS',
  complexity: 'SCORE_COMPLEXITY',
  edgeCases: 'SCORE_EDGE_CASES',
  codeQuality: 'SCORE_CODE_QUALITY',
  problemFit: 'SCORE_PROBLEM_FIT',
};

const REVIEW_LEGACY_SCORE_EXPLANATION_TYPES: Record<ReviewScoreDimension, string[]> = {
  correctness: ['CORRECTNESS_ISSUE'],
  complexity: ['CONSTRAINT_ANALYSIS'],
  edgeCases: ['EDGE_CASE', 'MISSING_EDGE_CASE'],
  codeQuality: ['CODE_QUALITY'],
  problemFit: ['PROBLEM_FIT'],
};

const REVIEW_SCORE_BASIS_TYPES: ReviewScoreBasis[] = [
  'SERVER_EXECUTION',
  'USER_REPORTED_EXECUTION',
  'STATIC_ANALYSIS',
  'INSUFFICIENT_CONTEXT',
];

interface ReviewDetailPanelProps {
  detail: PracticeCodeReviewDetail;
  passScore?: number;
  resources: LocaleResources;
}

export default function ReviewDetailPanel({ detail, passScore, resources }: ReviewDetailPanelProps) {
  const codeSnapshot = detail.submittedCode ?? detail.normalizedCode ?? detail.rawCode ?? '';
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

      <ReviewScoreBreakdown detail={detail} resources={resources} />

      <MarkdownView content={detail.reviewMarkdown} />

      <ReviewTextList title={resources.learningPlans.reviewDeductionReasons} items={deductionReasons} />
      <ReviewTextList title={resources.learningPlans.reviewImprovementSuggestions} items={improvementSuggestions} />

      {codeSnapshot && (
        <section className="review-detail-section">
          <h4>{resources.learningPlans.reviewCodeSnapshot}</h4>
          <pre><code>{codeSnapshot}</code></pre>
        </section>
      )}
    </section>
  );
}

function ReviewScoreBreakdown({
  detail,
  resources,
}: {
  detail: PracticeCodeReviewDetail;
  resources: LocaleResources;
}) {
  const tooltipIdPrefix = useId();
  const [activeDimension, setActiveDimension] = useState<ReviewScoreDimension>();

  return (
    <section className="review-detail-section">
      <h4>{resources.learningPlans.reviewScoreBreakdown}</h4>
      <div className="review-score-breakdown">
        {REVIEW_SCORE_DIMENSIONS.map(({ key, maximum }) => {
          const score = detail.scores[key];
          const level = scoreLevel(key, score, maximum);
          const contribution = resources.learningPlans.reviewScoreContribution(score, maximum);
          const percentage = Math.max(0, Math.min(100, (score / maximum) * 100));
          const dimensionLabel = resources.learningPlans.reviewScoreDimensions[key];
          const levelLabel = resources.learningPlans.reviewScoreLevels[level];
          const tooltipId = `${tooltipIdPrefix}-${key}`;
          const tooltipActive = activeDimension === key;
          const scoreDetail = reviewScoreDetail(detail, key, resources);

          return (
            <div className="review-score-row" key={key}>
              <div className="review-score-copy">
                <strong>{dimensionLabel}</strong>
                <span className={`review-score-level ${level}`}>
                  {levelLabel}
                </span>
              </div>
              <div className="review-score-meter-wrap">
                <button
                  aria-describedby={tooltipActive ? tooltipId : undefined}
                  aria-expanded={tooltipActive}
                  aria-label={resources.learningPlans.reviewScoreDetailAction(dimensionLabel)}
                  className="review-score-meter-trigger"
                  onBlur={() => setActiveDimension(undefined)}
                  onClick={() => setActiveDimension(key)}
                  onFocus={() => setActiveDimension(key)}
                  onMouseEnter={() => setActiveDimension(key)}
                  onMouseLeave={() => setActiveDimension(undefined)}
                  type="button"
                >
                  <span
                    aria-label={`${dimensionLabel}: ${contribution}`}
                    aria-valuemax={maximum}
                    aria-valuemin={0}
                    aria-valuenow={score}
                    className={`review-score-meter ${level}`}
                    role="meter"
                  >
                    <span style={{ width: `${percentage}%` }} />
                  </span>
                </button>
                {tooltipActive && (
                  <span className="review-score-tooltip" id={tooltipId} role="tooltip">
                    <strong>{resources.learningPlans.reviewScoreTooltipTitle(
                      dimensionLabel,
                      levelLabel,
                      contribution,
                    )}</strong>
                    <span>{scoreDetail.summary}</span>
                    {scoreDetail.facts.map((fact) => <span key={fact}>{fact}</span>)}
                    {scoreDetail.basis && <span>{scoreDetail.basis}</span>}
                  </span>
                )}
              </div>
              <span className="review-score-value">{contribution}</span>
            </div>
          );
        })}
      </div>
    </section>
  );
}

function reviewScoreDetail(
  detail: PracticeCodeReviewDetail,
  dimension: ReviewScoreDimension,
  resources: LocaleResources,
) {
  const evidence = detail.evidence ?? [];
  const explanationTypes = [
    REVIEW_SCORE_EXPLANATION_TYPES[dimension],
    ...REVIEW_LEGACY_SCORE_EXPLANATION_TYPES[dimension],
  ];
  const summary = evidenceValue(evidence, explanationTypes)
    ?? (dimension === 'correctness' ? detail.deductionReasons?.[0] : undefined)
    ?? resources.learningPlans.reviewScoreDetailUnavailable;
  const facts: string[] = [];

  if (dimension === 'complexity') {
    const timeComplexity = evidenceValue(evidence, ['TIME_COMPLEXITY']);
    const spaceComplexity = evidenceValue(evidence, ['SPACE_COMPLEXITY']);
    const expectedComplexity = evidenceValue(evidence, ['EXPECTED_TIME_COMPLEXITY']);
    const constraintAnalysis = evidenceValue(evidence, ['CONSTRAINT_ANALYSIS']);
    if (timeComplexity) facts.push(resources.learningPlans.reviewScoreTimeComplexity(timeComplexity));
    if (spaceComplexity) facts.push(resources.learningPlans.reviewScoreSpaceComplexity(spaceComplexity));
    if (expectedComplexity) facts.push(resources.learningPlans.reviewScoreExpectedComplexity(expectedComplexity));
    if (constraintAnalysis && constraintAnalysis !== summary) facts.push(constraintAnalysis);
  }

  return {
    summary,
    facts,
    basis: reviewScoreBasis(evidenceValue(evidence, ['JUDGE_VERDICT']), resources),
  };
}

function evidenceValue(
  evidence: PracticeCodeReviewDetail['evidence'],
  types: string[],
): string | undefined {
  for (const type of types) {
    const value = evidence.find((item) => item.type === type)?.value;
    if (value) return value;
  }
  return undefined;
}

function reviewScoreBasis(value: string | undefined, resources: LocaleResources): string | undefined {
  const basis = REVIEW_SCORE_BASIS_TYPES.find((candidate) => value?.includes(candidate));
  return basis ? resources.learningPlans.reviewScoreAnalysisBasis[basis] : undefined;
}

function scoreLevel(key: ReviewScoreDimension, score: number, maximum: number): ReviewScoreLevel {
  if (key === 'correctness' && score <= 2) return 'weak';
  const ratio = score / maximum;
  if (ratio >= 0.9) return 'excellent';
  if (ratio >= 0.7) return 'good';
  if (ratio >= 0.4) return 'needsImprovement';
  return 'weak';
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
