import {
  Activity,
  AlertCircle,
  BrainCircuit,
  Check,
  CheckCircle2,
  Gauge,
  Settings2,
  Sparkles,
  Target,
  Trophy,
} from 'lucide-react';
import { useEffect, useState } from 'react';
import type { CSSProperties } from 'react';
import AbilityRadarChart from './ability/AbilityRadarChart';
import { useI18n } from './i18n/I18nProvider';
import {
  getAbilityProfile,
  getUserAiPreference,
  requireApiData,
  updateUserAiPreference,
} from './services/api';
import type {
  AbilityProfileResponse,
  AbilityTagScore,
  PracticeCoachStyle,
  UserAiPreference,
  UserAiPreferenceRequest,
} from './types/api';

const coachStyleOptions: PracticeCoachStyle[] = [
  'SOCRATIC_GUIDE',
  'DIRECT_EXPLAINER',
  'INTERVIEWER',
  'STRICT_REVIEWER',
  'SUPPORTIVE_MENTOR',
];

export default function MyPage() {
  const { locale, resources } = useI18n();
  const [abilityProfile, setAbilityProfile] = useState<AbilityProfileResponse>();
  const [abilityLoading, setAbilityLoading] = useState(true);
  const [abilityError, setAbilityError] = useState('');
  const [aiPreference, setAiPreference] = useState<UserAiPreference>();
  const [preferenceLoading, setPreferenceLoading] = useState(true);
  const [preferenceError, setPreferenceError] = useState('');
  const [preferenceSaveError, setPreferenceSaveError] = useState('');
  const [preferenceSaving, setPreferenceSaving] = useState(false);
  const [preferenceSaved, setPreferenceSaved] = useState(false);

  useEffect(() => {
    const controller = new AbortController();
    void loadAbilityProfile(controller.signal);
    void loadAiPreference(controller.signal);
    return () => controller.abort();
  }, []);

  async function loadAbilityProfile(signal?: AbortSignal) {
    setAbilityLoading(true);
    setAbilityError('');
    try {
      const response = await getAbilityProfile(signal);
      setAbilityProfile(requireApiData(response, resources.home.abilityLoadFailed));
    } catch (error) {
      if (signal?.aborted) {
        return;
      }
      setAbilityError(error instanceof Error ? error.message : resources.home.abilityLoadFailed);
    } finally {
      if (!signal?.aborted) {
        setAbilityLoading(false);
      }
    }
  }

  async function loadAiPreference(signal?: AbortSignal) {
    setPreferenceLoading(true);
    setPreferenceError('');
    setPreferenceSaveError('');
    setPreferenceSaved(false);
    try {
      const response = await getUserAiPreference(signal);
      setAiPreference(requireApiData(response, resources.aiPreference.loadFailed));
    } catch (error) {
      if (signal?.aborted) {
        return;
      }
      setPreferenceError(error instanceof Error ? error.message : resources.aiPreference.loadFailed);
    } finally {
      if (!signal?.aborted) {
        setPreferenceLoading(false);
      }
    }
  }

  async function saveAiPreference(update: UserAiPreferenceRequest) {
    if (!aiPreference || preferenceSaving) {
      return;
    }
    const previousPreference = aiPreference;
    const nextRequest = {
      coachStyle: update.coachStyle ?? aiPreference.coachStyle,
    };
    if (nextRequest.coachStyle === aiPreference.coachStyle) {
      return;
    }
    setAiPreference({
      ...aiPreference,
      ...nextRequest,
      coachStyleLabel: resources.aiPreference.coachStyleLabels[nextRequest.coachStyle],
    });
    setPreferenceSaving(true);
    setPreferenceSaved(false);
    setPreferenceSaveError('');
    try {
      const response = await updateUserAiPreference(nextRequest);
      setAiPreference(requireApiData(response, resources.aiPreference.saveFailed));
      setPreferenceSaved(true);
    } catch (error) {
      setAiPreference(previousPreference);
      setPreferenceSaveError(error instanceof Error ? error.message : resources.aiPreference.saveFailed);
    } finally {
      setPreferenceSaving(false);
    }
  }

  const abilitySummary = summarizeAbilityProfile(abilityProfile);
  const averageScore = formatScore(abilitySummary.averageScore, locale);
  const strongestScore = abilitySummary.strongestTag
    ? formatScore(abilitySummary.strongestTag.abilityScore, locale)
    : resources.myPage.noData;
  const summaryCards = [
    {
      className: 'coach',
      icon: BrainCircuit,
      label: resources.myPage.statCurrentCoach,
      value: aiPreference?.coachStyleLabel ?? (preferenceLoading ? resources.myPage.dataPending : resources.myPage.noData),
      detail: resources.myPage.readyForNextReply,
    },
    {
      className: 'scope',
      icon: Target,
      label: resources.myPage.statEvaluatedTags,
      value: abilityProfile
        ? resources.myPage.tagCoverage(abilitySummary.reviewedTags, abilitySummary.totalTags)
        : (abilityLoading ? resources.myPage.dataPending : resources.myPage.noData),
      detail: resources.home.abilityRadarSubtitle,
    },
    {
      className: 'score',
      icon: Gauge,
      label: resources.myPage.statAverageScore,
      value: abilityProfile
        ? resources.myPage.scoreValue(averageScore)
        : (abilityLoading ? resources.myPage.dataPending : resources.myPage.noData),
      detail: abilitySummary.strongestTag
        ? `${resources.myPage.strongestTag}: ${abilitySummary.strongestTag.label} ${resources.myPage.scoreValue(strongestScore)}`
        : resources.myPage.noTopAbilities,
    },
    {
      className: 'review',
      icon: Activity,
      label: resources.myPage.statReviewedProblems,
      value: abilityProfile
        ? resources.myPage.reviewedProblemsValue(abilitySummary.reviewedProblems)
        : (abilityLoading ? resources.myPage.dataPending : resources.myPage.noData),
      detail: resources.myPage.radarSummaryTitle,
    },
  ];

  return (
    <section className="my-page" aria-label={resources.nav.my}>
      <header className="my-page-hero" aria-labelledby="my-page-title">
        <div className="my-hero-copy">
          <p className="my-page-kicker">
            <Sparkles aria-hidden="true" />
            <span>{resources.myPage.profileKicker}</span>
          </p>
          <h1 id="my-page-title">{resources.myPage.title}</h1>
          <p>{resources.myPage.subtitle}</p>
        </div>
        <div className="my-hero-status" aria-live="polite">
          <span className="my-hero-status-icon" aria-hidden="true">
            <CheckCircle2 />
          </span>
          <span>{preferenceSaving ? resources.aiPreference.saving : resources.myPage.readyForNextReply}</span>
        </div>
      </header>

      <div className="my-summary-grid" aria-label={resources.myPage.radarSummaryTitle}>
        {summaryCards.map((card) => {
          const Icon = card.icon;
          return (
            <article className={`my-summary-card ${card.className}`} key={card.label}>
              <span className="my-summary-icon" aria-hidden="true">
                <Icon />
              </span>
              <div>
                <span>{card.label}</span>
                <strong>{card.value}</strong>
                <p>{card.detail}</p>
              </div>
            </article>
          );
        })}
      </div>

      <div className="my-workspace-grid">
        <article className="my-card ai-preference-card" aria-labelledby="ai-preference-title">
          <div className="my-card-heading">
            <div className="my-card-title">
              <span className="my-card-title-icon" aria-hidden="true">
                <Settings2 />
              </span>
              <div>
                <p className="my-section-eyebrow">{resources.myPage.coachPanelEyebrow}</p>
                <h2 id="ai-preference-title">{resources.aiPreference.title}</h2>
                <p>{resources.aiPreference.subtitle}</p>
              </div>
            </div>
            <div className="preference-save-status" aria-live="polite">
              {preferenceSaving ? (
                <span>{resources.aiPreference.saving}</span>
              ) : preferenceSaved ? (
                <span>{resources.aiPreference.saved}</span>
              ) : null}
            </div>
          </div>

          {preferenceLoading ? (
            <div className="preference-state" role="status">{resources.aiPreference.loading}</div>
          ) : preferenceError ? (
            <div className="preference-state error" role="alert">
              <AlertCircle aria-hidden="true" />
              <span>{preferenceError}</span>
              <button className="secondary-button compact" onClick={() => void loadAiPreference()} type="button">
                {resources.app.retry}
              </button>
            </div>
          ) : aiPreference ? (
            <div className="preference-controls">
              <div className="current-coach-strip">
                <BrainCircuit aria-hidden="true" />
                <div>
                  <span>{resources.myPage.selectedCoach}</span>
                  <strong>{aiPreference.coachStyleLabel}</strong>
                </div>
                <span>{resources.myPage.readyForNextReply}</span>
              </div>
              <fieldset className="preference-control-group">
                <legend>{resources.aiPreference.coachStyle}</legend>
                <div className="coach-style-grid">
                  {coachStyleOptions.map((style) => (
                    <button
                      aria-pressed={aiPreference.coachStyle === style}
                      className={`preference-option ${aiPreference.coachStyle === style ? 'selected' : ''}`}
                      disabled={preferenceSaving}
                      key={style}
                      onClick={() => void saveAiPreference({ coachStyle: style })}
                      type="button"
                    >
                      <span className="preference-option-title">
                        <span className="preference-option-check" aria-hidden="true">
                          {aiPreference.coachStyle === style ? <Check /> : null}
                        </span>
                        {resources.aiPreference.coachStyleLabels[style]}
                      </span>
                      <span className="preference-option-description">
                        {resources.aiPreference.coachStyleDescriptions[style]}
                      </span>
                    </button>
                  ))}
                </div>
              </fieldset>
              {preferenceSaveError ? (
                <div className="preference-save-error" role="alert">
                  <AlertCircle aria-hidden="true" />
                  <span>{preferenceSaveError}</span>
                </div>
              ) : null}
            </div>
          ) : null}
        </article>

        <article className="my-card ability-card" aria-labelledby="ability-radar-title">
          <div className="my-card-heading ability-heading">
            <div className="my-card-title">
              <span className="my-card-title-icon" aria-hidden="true">
                <Trophy />
              </span>
              <div>
                <p className="my-section-eyebrow">{resources.myPage.abilityPanelEyebrow}</p>
                <h2 id="ability-radar-title">{resources.home.abilityRadarTitle}</h2>
                <p>{resources.home.abilityRadarSubtitle}</p>
              </div>
            </div>
            <div className="ability-score-pill">
              <Gauge aria-hidden="true" />
              <span>{abilityProfile ? resources.myPage.scoreValue(averageScore) : resources.myPage.noData}</span>
            </div>
          </div>
          {abilityLoading ? (
            <div className="ability-state" role="status">{resources.home.abilityLoading}</div>
          ) : abilityError ? (
            <div className="ability-state error" role="alert">
              <AlertCircle aria-hidden="true" />
              <span>{abilityError}</span>
              <button className="secondary-button compact" onClick={() => void loadAbilityProfile()} type="button">
                {resources.app.retry}
              </button>
            </div>
          ) : abilityProfile ? (
            <div className="ability-radar-layout">
              <div className="ability-radar-stage">
                <AbilityRadarChart profile={abilityProfile} />
              </div>
              <aside className="ability-insights" aria-label={resources.myPage.topAbilities}>
                <h3>
                  <Trophy aria-hidden="true" />
                  <span>{resources.myPage.topAbilities}</span>
                </h3>
                {abilitySummary.topTags.length > 0 ? (
                  <ol className="ability-top-list">
                    {abilitySummary.topTags.map((tag, index) => (
                      <li key={tag.tag}>
                        <div className="ability-top-row">
                          <span className="ability-top-rank">{index + 1}</span>
                          <span className="ability-top-name">{tag.label}</span>
                          <strong>{resources.myPage.scoreValue(formatScore(tag.abilityScore, locale))}</strong>
                        </div>
                        <div className="ability-score-track" aria-hidden="true">
                          <span style={scoreBarStyle(tag.abilityScore)} />
                        </div>
                        <p>{resources.myPage.reviewedProblemsValue(tag.reviewedProblemCount)}</p>
                      </li>
                    ))}
                  </ol>
                ) : (
                  <p className="ability-insights-empty">{resources.myPage.noTopAbilities}</p>
                )}
              </aside>
            </div>
          ) : (
            <div className="ability-state">{resources.home.abilityEmpty}</div>
          )}
        </article>
      </div>
    </section>
  );
}

interface AbilitySummary {
  averageScore: number;
  reviewedProblems: number;
  reviewedTags: number;
  strongestTag?: AbilityTagScore;
  topTags: AbilityTagScore[];
  totalTags: number;
}

function summarizeAbilityProfile(profile?: AbilityProfileResponse): AbilitySummary {
  const tags = profile?.tags ?? [];
  const scoredTags = [...tags].sort((left, right) => {
    if (right.abilityScore !== left.abilityScore) {
      return right.abilityScore - left.abilityScore;
    }
    return right.reviewedProblemCount - left.reviewedProblemCount;
  });
  const reviewedProblems = tags.reduce((total, tag) => total + tag.reviewedProblemCount, 0);
  return {
    averageScore: tags.length === 0
      ? 0
      : tags.reduce((total, tag) => total + tag.abilityScore, 0) / tags.length,
    reviewedProblems,
    reviewedTags: tags.filter((tag) => tag.reviewedProblemCount > 0).length,
    strongestTag: scoredTags[0],
    topTags: scoredTags
      .filter((tag) => tag.abilityScore > 0 || tag.reviewedProblemCount > 0)
      .slice(0, 5),
    totalTags: tags.length,
  };
}

function formatScore(score: number, locale: string): string {
  return new Intl.NumberFormat(locale, {
    maximumFractionDigits: 1,
    minimumFractionDigits: 1,
  }).format(score);
}

function scoreBarStyle(score: number): CSSProperties {
  const clampedScore = Math.max(0, Math.min(10, score));
  return { '--ability-score': `${clampedScore * 10}%` } as CSSProperties;
}
