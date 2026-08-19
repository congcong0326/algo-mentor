import {
  Activity,
  AlertCircle,
  Gauge,
  Sparkles,
  Target,
  Trophy,
} from 'lucide-react';
import { useEffect, useState } from 'react';
import type { CSSProperties } from 'react';
import AbilityBubbleChart from './ability/AbilityBubbleChart';
import {
  defaultAbilityTagKeys,
  formatAbilityScore,
  summarizeAbilityProfile,
} from './ability/abilityProfile';
import { useI18n } from './i18n/I18nProvider';
import LearnerProfileSection from './learner-profile/LearnerProfileSection';
import {
  getAbilityProfile,
  requireApiData,
} from './services/api';
import type {
  AbilityProfileResponse,
  AbilityTagScore,
} from './types/api';

const maxAbilityBubbleCount = 12;
export default function MyPage({
  onNavigate,
  onProfileAnchorHandled,
  profileAnchor,
}: {
  onNavigate?: (pathname: string) => void;
  onProfileAnchorHandled?: () => void;
  profileAnchor?: string;
} = {}) {
  const { locale, resources } = useI18n();
  const [abilityProfile, setAbilityProfile] = useState<AbilityProfileResponse>();
  const [abilityLoading, setAbilityLoading] = useState(true);
  const [abilityError, setAbilityError] = useState('');
  const [selectedAbilityTags, setSelectedAbilityTags] = useState<string[]>([]);

  useEffect(() => {
    const controller = new AbortController();
    void loadAbilityProfile(controller.signal);
    return () => controller.abort();
  }, [locale]);

  async function loadAbilityProfile(signal?: AbortSignal) {
    setAbilityLoading(true);
    setAbilityError('');
    try {
      const response = await getAbilityProfile(signal);
      const profile = requireApiData(response, resources.home.abilityLoadFailed);
      setAbilityProfile(profile);
      setSelectedAbilityTags((currentTags) => {
        const availableTags = new Set(profile.tags.map((tag) => tag.tag));
        const retainedTags = currentTags.filter((tag) => availableTags.has(tag));
        const retainedTagSet = new Set(retainedTags);
        const replacementTags = defaultAbilityTagKeys(profile)
          .filter((tag) => !retainedTagSet.has(tag));
        return [...retainedTags, ...replacementTags].slice(0, maxAbilityBubbleCount);
      });
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

  const abilitySummary = summarizeAbilityProfile(abilityProfile);
  const selectedAbilityTagScores = selectedAbilityTags
    .map((tag) => abilityProfile?.tags.find((item) => item.tag === tag))
    .filter((tag): tag is AbilityTagScore => Boolean(tag));
  const averageScore = formatAbilityScore(abilitySummary.averageScore, locale);
  const strongestScore = abilitySummary.strongestTag
    ? formatAbilityScore(abilitySummary.strongestTag.abilityScore, locale)
    : resources.myPage.noData;
  const summaryCards = [
    {
      className: 'scope',
      icon: Target,
      label: resources.myPage.statEvaluatedTags,
      value: abilityProfile
        ? resources.myPage.tagCoverage(abilitySummary.reviewedTags, abilitySummary.totalTags)
        : (abilityLoading ? resources.myPage.dataPending : resources.myPage.noData),
      detail: resources.home.abilityMapSubtitle,
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
      detail: resources.myPage.abilitySummaryTitle,
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

  function selectAbilityTag(tag: AbilityTagScore) {
    setSelectedAbilityTags((currentTags) => {
      if (currentTags.includes(tag.tag)) {
        return currentTags;
      }
      if (currentTags.length >= maxAbilityBubbleCount) {
        return [...currentTags.slice(1), tag.tag];
      }
      return [...currentTags, tag.tag];
    });
  }

  function renderAbilityHeatmap(titleId: string, className = '') {
    if (!abilityProfile) {
      return null;
    }

    return (
      <section
        className={`ability-heatmap-section ${className}`.trim()}
        aria-labelledby={titleId}
      >
        <div className="ability-heatmap-heading">
          <div>
            <p className="my-section-eyebrow">{resources.myPage.abilityHeatmapEyebrow}</p>
            <h2 id={titleId}>{resources.myPage.abilityHeatmapTitle}</h2>
          </div>
          <span>{resources.myPage.abilityHeatmapHint}</span>
        </div>
        <div className="ability-heatmap-grid">
          {abilityProfile.tags.map((tag) => {
            const selected = selectedAbilityTags.includes(tag.tag);
            return (
              <button
                aria-label={selected ? resources.myPage.selectedHeatmapTag(tag.label) : resources.myPage.addHeatmapTag(tag.label)}
                aria-pressed={selected}
                className={`ability-heatmap-cell ${selected ? 'selected' : ''}`}
                data-testid="ability-heatmap-tag"
                key={tag.tag}
                onClick={() => selectAbilityTag(tag)}
                style={heatmapCellStyle(tag.abilityScore)}
                type="button"
              >
                <strong>{tag.label}</strong>
                <span>{resources.myPage.scoreValue(formatAbilityScore(tag.abilityScore, locale))}</span>
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
    );
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
        </div>
      </header>

      <div className="my-summary-grid" aria-label={resources.myPage.abilitySummaryTitle}>
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

      <LearnerProfileSection
        onNavigate={onNavigate}
        onProfileAnchorHandled={onProfileAnchorHandled}
        profileAnchor={profileAnchor}
      />

      <div className="my-workspace-grid profile-only">
        <article className="my-card ability-card" aria-labelledby="ability-bubble-title">
          <div className="my-card-heading ability-heading">
            <div className="my-card-title">
              <span className="my-card-title-icon" aria-hidden="true">
                <Trophy />
              </span>
              <div>
                <p className="my-section-eyebrow">{resources.myPage.abilityPanelEyebrow}</p>
                <h2 id="ability-bubble-title">{resources.home.abilityMapTitle}</h2>
                <p>{resources.home.abilityMapSubtitle}</p>
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
            <>
              <div className="ability-profile-visual-grid">
                <div className="ability-profile-bubble-stage">
                  <AbilityBubbleChart profile={abilityProfile} tags={selectedAbilityTagScores} />
                </div>
                <aside className="ability-profile-selection" aria-label={resources.myPage.selectedAbilityTags}>
                  <div className="ability-profile-selection-count">
                    <strong>{resources.myPage.selectedTagCount(selectedAbilityTagScores.length, maxAbilityBubbleCount)}</strong>
                    <span>{resources.myPage.abilityReplacementHint}</span>
                  </div>
                  <div className="ability-chip-list">
                    {selectedAbilityTagScores.map((tag) => (
                      <span className="ability-chip" key={tag.tag}>{tag.label}</span>
                    ))}
                  </div>
                </aside>
              </div>
              {renderAbilityHeatmap('profile-ability-heatmap-title', 'ability-profile-heatmap')}
            </>
          ) : (
            <div className="ability-state">{resources.home.abilityEmpty}</div>
          )}
        </article>
      </div>
    </section>
  );
}

function heatmapCellStyle(score: number): CSSProperties {
  const clampedScore = Math.max(0, Math.min(10, score));
  return { '--ability-heat-alpha': String(0.06 + clampedScore * 0.026) } as CSSProperties;
}
