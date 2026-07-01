import {
  Activity,
  AlertCircle,
  BrainCircuit,
  Check,
  Gauge,
  X,
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
const defaultRadarTagCount = 8;
const maxRadarTagCount = 12;
const minRadarTagCount = 3;

export default function MyPage() {
  const { locale, resources } = useI18n();
  const [abilityProfile, setAbilityProfile] = useState<AbilityProfileResponse>();
  const [abilityLoading, setAbilityLoading] = useState(true);
  const [abilityError, setAbilityError] = useState('');
  const [selectedAbilityTags, setSelectedAbilityTags] = useState<string[]>([]);
  const [abilityDialogOpen, setAbilityDialogOpen] = useState(false);
  const [abilitySelectionNotice, setAbilitySelectionNotice] = useState('');
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
      const profile = requireApiData(response, resources.home.abilityLoadFailed);
      setAbilityProfile(profile);
      setSelectedAbilityTags(defaultAbilityTagKeys(profile));
      setAbilitySelectionNotice('');
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
  const selectedAbilityTagScores = selectedAbilityTags
    .map((tag) => abilityProfile?.tags.find((item) => item.tag === tag))
    .filter((tag): tag is AbilityTagScore => Boolean(tag));
  const defaultAbilityTags = new Set(abilityProfile ? defaultAbilityTagKeys(abilityProfile) : []);
  const averageScore = formatScore(abilitySummary.averageScore, locale);
  const strongestScore = abilitySummary.strongestTag
    ? formatScore(abilitySummary.strongestTag.abilityScore, locale)
    : resources.myPage.noData;
  const breakthroughTag = findBreakthroughTag(abilityProfile, abilitySummary.strongestTag);
  const summaryCards = [
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
    {
      className: 'strength',
      icon: Trophy,
      label: resources.myPage.statPrimaryStrength,
      value: abilitySummary.strongestTag?.label ?? (abilityLoading ? resources.myPage.dataPending : resources.myPage.noData),
      detail: abilitySummary.strongestTag
        ? resources.myPage.scoreValue(strongestScore)
        : resources.myPage.noTopAbilities,
    },
  ];

  function toggleAbilityTag(tag: AbilityTagScore) {
    setSelectedAbilityTags((currentTags) => {
      if (currentTags.includes(tag.tag)) {
        if (currentTags.length <= minRadarTagCount) {
          setAbilitySelectionNotice(resources.myPage.minimumSelectionNotice(minRadarTagCount));
          return currentTags;
        }
        setAbilitySelectionNotice('');
        return currentTags.filter((selectedTag) => selectedTag !== tag.tag);
      }
      if (currentTags.length >= maxRadarTagCount) {
        setAbilitySelectionNotice(resources.myPage.maximumSelectionNotice(maxRadarTagCount));
        return currentTags;
      }
      setAbilitySelectionNotice('');
      return [...currentTags, tag.tag];
    });
  }

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
              <button
                aria-label={resources.myPage.expandAbilityProfile}
                className="ability-radar-open-button"
                onClick={() => setAbilityDialogOpen(true)}
                type="button"
              >
                <AbilityRadarChart profile={abilityProfile} tags={selectedAbilityTagScores} />
              </button>
              <aside className="ability-diagnostics" aria-labelledby="ability-diagnostics-title">
                <h3 id="ability-diagnostics-title">{resources.myPage.diagnosisSummaryTitle}</h3>
                <div className="ability-diagnostic-panel">
                  <div className="ability-diagnostic-row">
                    <span>{resources.myPage.currentStrength}</span>
                    <strong>{abilitySummary.strongestTag?.label ?? resources.myPage.noData}</strong>
                  </div>
                  <p>
                    {abilitySummary.strongestTag
                      ? resources.myPage.currentStrengthDetail(
                        abilitySummary.strongestTag.label,
                        resources.myPage.scoreValue(strongestScore),
                        abilitySummary.strongestTag.reviewedProblemCount,
                      )
                      : resources.myPage.noTopAbilities}
                  </p>
                </div>
                <div className="ability-diagnostic-panel advice">
                  <span>{resources.myPage.breakthroughAdvice}</span>
                  <p>
                    {breakthroughTag
                      ? resources.myPage.breakthroughAdviceDetail(breakthroughTag.label)
                      : resources.myPage.noTopAbilities}
                  </p>
                </div>
              </aside>
            </div>
          ) : (
            <div className="ability-state">{resources.home.abilityEmpty}</div>
          )}
        </article>

        <article className="my-card ai-preference-card" aria-labelledby="ai-preference-title">
          <div className="my-card-heading">
            <div className="my-card-title">
              <span className="my-card-title-icon" aria-hidden="true">
                <Settings2 />
              </span>
              <div>
                <p className="my-section-eyebrow">{resources.myPage.coachPanelEyebrow}</p>
                <h2 id="ai-preference-title">{resources.aiPreference.title}</h2>
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
      </div>
      {abilityDialogOpen && abilityProfile ? (
        <div className="ability-dialog-backdrop">
          <section
            aria-labelledby="ability-dialog-title"
            aria-modal="true"
            className="ability-dialog"
            role="dialog"
          >
            <header className="ability-dialog-heading">
              <div>
                <p className="my-section-eyebrow">{resources.myPage.abilityPanelEyebrow}</p>
                <h2 id="ability-dialog-title">{resources.myPage.abilityDetailTitle}</h2>
                <p>{resources.myPage.abilityDetailSubtitle(maxRadarTagCount)}</p>
              </div>
              <button
                aria-label={resources.myPage.closeAbilityDetail}
                className="icon-button"
                onClick={() => setAbilityDialogOpen(false)}
                type="button"
              >
                <X aria-hidden="true" />
              </button>
            </header>
            <div className="ability-dialog-radar-grid">
              <div className="ability-dialog-radar-stage">
                <AbilityRadarChart profile={abilityProfile} tags={selectedAbilityTagScores} />
              </div>
              <aside className="ability-dialog-selection" aria-label={resources.myPage.selectedAbilityTags}>
                <div className="ability-dialog-selection-count">
                  <strong>{resources.myPage.selectedTagCount(selectedAbilityTagScores.length, maxRadarTagCount)}</strong>
                  <span>{resources.myPage.minimumTagCount(minRadarTagCount)}</span>
                </div>
                <div className="ability-chip-list">
                  {selectedAbilityTagScores.map((tag) => (
                    defaultAbilityTags.has(tag.tag) ? (
                      <span className="ability-chip fixed" key={tag.tag}>{tag.label}</span>
                    ) : (
                      <button
                        aria-label={resources.myPage.removeSelectedTag(tag.label)}
                        className="ability-chip"
                        key={tag.tag}
                        onClick={() => toggleAbilityTag(tag)}
                        type="button"
                      >
                        <span>{tag.label}</span>
                        <X aria-hidden="true" />
                      </button>
                    )
                  ))}
                </div>
                {abilitySelectionNotice ? (
                  <p className="ability-selection-notice" role="status">{abilitySelectionNotice}</p>
                ) : null}
              </aside>
            </div>
            <section className="ability-heatmap-section" aria-labelledby="ability-heatmap-title">
              <div className="ability-heatmap-heading">
                <h3 id="ability-heatmap-title">{resources.myPage.abilityHeatmapTitle}</h3>
                <span>{resources.myPage.abilityHeatmapHint}</span>
              </div>
              <div className="ability-heatmap-grid">
                {abilityProfile.tags.map((tag) => {
                  const selected = selectedAbilityTags.includes(tag.tag);
                  const disabled = !selected && selectedAbilityTags.length >= maxRadarTagCount;
                  return (
                    <button
                      aria-label={selected ? resources.myPage.removeHeatmapTag(tag.label) : resources.myPage.addHeatmapTag(tag.label)}
                      aria-pressed={selected}
                      className={`ability-heatmap-cell ${selected ? 'selected' : ''}`}
                      data-testid="ability-heatmap-tag"
                      disabled={disabled}
                      key={tag.tag}
                      onClick={() => toggleAbilityTag(tag)}
                      style={heatmapCellStyle(tag.abilityScore)}
                      type="button"
                    >
                      <strong>{tag.label}</strong>
                      <span>{resources.myPage.scoreValue(formatScore(tag.abilityScore, locale))}</span>
                      <small>
                        {resources.myPage.reviewedProblemsValue(tag.reviewedProblemCount)}
                        {' / '}
                        {resources.myPage.catalogProblemsValue(tag.problemCount)}
                      </small>
                    </button>
                  );
                })}
              </div>
            </section>
          </section>
        </div>
      ) : null}
    </section>
  );
}

interface AbilitySummary {
  averageScore: number;
  reviewedProblems: number;
  reviewedTags: number;
  strongestTag?: AbilityTagScore;
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
    totalTags: tags.length,
  };
}

function formatScore(score: number, locale: string): string {
  return new Intl.NumberFormat(locale, {
    maximumFractionDigits: 1,
    minimumFractionDigits: 1,
  }).format(score);
}

function heatmapCellStyle(score: number): CSSProperties {
  const clampedScore = Math.max(0, Math.min(10, score));
  return { '--ability-heat-alpha': String(0.06 + clampedScore * 0.026) } as CSSProperties;
}

function defaultAbilityTagKeys(profile: AbilityProfileResponse): string[] {
  return profile.tags.slice(0, defaultRadarTagCount).map((tag) => tag.tag);
}

function findBreakthroughTag(
  profile?: AbilityProfileResponse,
  strongestTag?: AbilityTagScore,
): AbilityTagScore | undefined {
  const tags = profile?.tags ?? [];
  return tags.find((tag) => tag.reviewedProblemCount === 0)
    ?? tags.find((tag) => tag.tag !== strongestTag?.tag)
    ?? strongestTag;
}
