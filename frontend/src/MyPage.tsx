import {
  Activity,
  AlertCircle,
  BrainCircuit,
  ChevronDown,
  Gauge,
  X,
  Sparkles,
  Target,
  Trophy,
} from 'lucide-react';
import { useEffect, useState } from 'react';
import type { CSSProperties } from 'react';
import AbilityRadarChart from './ability/AbilityRadarChart';
import {
  defaultAbilityTagKeys,
  findBreakthroughTag,
  formatAbilityScore,
  summarizeAbilityProfile,
} from './ability/abilityProfile';
import MarkdownView from './components/MarkdownView';
import { useI18n } from './i18n/I18nProvider';
import {
  getAbilityProfile,
  getLearnerProfile,
  requireApiData,
} from './services/api';
import type {
  AbilityProfileResponse,
  AbilityTagScore,
  LearnerProfileEntry,
  LearnerProfileResponse,
} from './types/api';

const maxRadarTagCount = 12;
const minRadarTagCount = 3;
const memoryPreviewCount = 5;

type LearnerMemoryCategory = 'declaredFacts' | 'generalObservations' | 'tagAssessments';

const learnerMemoryCategories: LearnerMemoryCategory[] = [
  'declaredFacts',
  'generalObservations',
  'tagAssessments',
];

export default function MyPage() {
  const { locale, resources } = useI18n();
  const [abilityProfile, setAbilityProfile] = useState<AbilityProfileResponse>();
  const [abilityLoading, setAbilityLoading] = useState(true);
  const [abilityError, setAbilityError] = useState('');
  const [selectedAbilityTags, setSelectedAbilityTags] = useState<string[]>([]);
  const [abilityDialogOpen, setAbilityDialogOpen] = useState(false);
  const [abilitySelectionNotice, setAbilitySelectionNotice] = useState('');
  const [learnerProfile, setLearnerProfile] = useState<LearnerProfileResponse>();
  const [learnerProfileLoading, setLearnerProfileLoading] = useState(true);
  const [learnerProfileError, setLearnerProfileError] = useState('');
  const [activeMemoryCategory, setActiveMemoryCategory] = useState<LearnerMemoryCategory>('declaredFacts');
  const [memoryExpanded, setMemoryExpanded] = useState(false);

  useEffect(() => {
    const controller = new AbortController();
    void loadAbilityProfile(controller.signal);
    void loadLearnerProfile(controller.signal);
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

  async function loadLearnerProfile(signal?: AbortSignal) {
    setLearnerProfileLoading(true);
    setLearnerProfileError('');
    try {
      const response = await getLearnerProfile(signal);
      const profile = requireApiData(response, resources.myPage.memoryLoadFailed);
      setLearnerProfile(profile);
      setMemoryExpanded(false);
      setActiveMemoryCategory((currentCategory) => (
        profile[currentCategory].length > 0
          ? currentCategory
          : learnerMemoryCategories.find((category) => profile[category].length > 0) ?? currentCategory
      ));
    } catch (error) {
      if (!signal?.aborted) {
        setLearnerProfileError(error instanceof Error ? error.message : resources.myPage.memoryLoadFailed);
      }
    } finally {
      if (!signal?.aborted) {
        setLearnerProfileLoading(false);
      }
    }
  }

  const abilitySummary = summarizeAbilityProfile(abilityProfile);
  const selectedAbilityTagScores = selectedAbilityTags
    .map((tag) => abilityProfile?.tags.find((item) => item.tag === tag))
    .filter((tag): tag is AbilityTagScore => Boolean(tag));
  const defaultAbilityTags = new Set(abilityProfile ? defaultAbilityTagKeys(abilityProfile) : []);
  const averageScore = formatAbilityScore(abilitySummary.averageScore, locale);
  const strongestScore = abilitySummary.strongestTag
    ? formatAbilityScore(abilitySummary.strongestTag.abilityScore, locale)
    : resources.myPage.noData;
  const breakthroughTag = findBreakthroughTag(abilityProfile, abilitySummary.strongestTag);
  const activeMemoryItems = learnerProfile?.[activeMemoryCategory] ?? [];
  const visibleMemoryItems = memoryExpanded
    ? activeMemoryItems
    : activeMemoryItems.slice(0, memoryPreviewCount);
  const hiddenMemoryCount = Math.max(0, activeMemoryItems.length - visibleMemoryItems.length);
  const totalMemoryCount = learnerProfile
    ? learnerMemoryCategories.reduce((total, category) => total + learnerProfile[category].length, 0)
    : 0;
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

  function selectMemoryCategory(category: LearnerMemoryCategory) {
    setActiveMemoryCategory(category);
    setMemoryExpanded(false);
  }

  function memoryTitle(entry: LearnerProfileEntry): string {
    if (entry.tag) {
      return (locale === 'zh-CN' ? entry.tag.labelZh : entry.tag.labelEn) || entry.tag.value;
    }
    return resources.myPage.memoryDimensionLabels[entry.dimension];
  }

  function memoryDate(value: string): string {
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) {
      return value;
    }
    return new Intl.DateTimeFormat(locale, {
      year: 'numeric',
      month: 'short',
      day: 'numeric',
    }).format(date);
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
            <p className="my-section-eyebrow">ABILITY COVERAGE</p>
            <h2 id={titleId}>{resources.myPage.abilityHeatmapTitle}</h2>
          </div>
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
        {abilitySelectionNotice ? (
          <p className="ability-selection-notice" role="status">{abilitySelectionNotice}</p>
        ) : null}
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

      <section className="learner-memory-section" aria-labelledby="learner-memory-title">
        <div className="learner-memory-heading">
          <div className="my-card-title">
            <span className="my-card-title-icon learner-memory-title-icon" aria-hidden="true">
              <BrainCircuit />
            </span>
            <div>
              <p className="my-section-eyebrow">{resources.myPage.memoryEyebrow}</p>
              <h2 id="learner-memory-title">{resources.myPage.memoryTitle}</h2>
              <p>{resources.myPage.memorySubtitle}</p>
            </div>
          </div>
          {learnerProfile?.updatedAt ? (
            <span className="learner-memory-latest">
              {resources.myPage.memoryUpdatedAt(memoryDate(learnerProfile.updatedAt))}
            </span>
          ) : null}
        </div>

        {learnerProfileLoading ? (
          <div className="learner-memory-state" role="status">{resources.myPage.memoryLoading}</div>
        ) : learnerProfileError ? (
          <div className="learner-memory-state error" role="alert">
            <AlertCircle aria-hidden="true" />
            <span>{learnerProfileError}</span>
            <button className="secondary-button compact" onClick={() => void loadLearnerProfile()} type="button">
              {resources.app.retry}
            </button>
          </div>
        ) : !learnerProfile || totalMemoryCount === 0 ? (
          <div className="learner-memory-state empty">{resources.myPage.memoryEmpty}</div>
        ) : (
          <>
            <div className="learner-memory-tabs" role="tablist" aria-label={resources.myPage.memoryTitle}>
              {learnerMemoryCategories.map((category) => {
                const count = learnerProfile[category].length;
                const label = resources.myPage.memoryTabs[category];
                return (
                  <button
                    aria-label={resources.myPage.memoryTabLabel(label, count)}
                    aria-controls="learner-memory-panel"
                    aria-selected={activeMemoryCategory === category}
                    className={activeMemoryCategory === category ? 'active' : ''}
                    id={`learner-memory-tab-${category}`}
                    key={category}
                    onClick={() => selectMemoryCategory(category)}
                    role="tab"
                    type="button"
                  >
                    <span>{label}</span>
                    <strong aria-hidden="true">{count}</strong>
                  </button>
                );
              })}
            </div>

            <div
              aria-labelledby={`learner-memory-tab-${activeMemoryCategory}`}
              className="learner-memory-panel"
              id="learner-memory-panel"
              role="tabpanel"
            >
              {visibleMemoryItems.length === 0 ? (
                <div className="learner-memory-state empty compact-state">
                  {resources.myPage.memoryCategoryEmpty}
                </div>
              ) : (
                <div className="learner-memory-list">
                  {visibleMemoryItems.map((entry) => (
                    <article className="learner-memory-item" key={entry.id}>
                      <header>
                        <h3>{memoryTitle(entry)}</h3>
                        <div className="learner-memory-meta">
                          {entry.revisionNo > 1 ? <span>{resources.myPage.memoryRevision(entry.revisionNo)}</span> : null}
                          <span>{resources.myPage.memoryUpdatedAt(memoryDate(entry.updatedAt))}</span>
                        </div>
                      </header>
                      <MarkdownView content={entry.contentText} />
                    </article>
                  ))}
                </div>
              )}
              {activeMemoryItems.length > memoryPreviewCount ? (
                <button
                  className="secondary-button compact learner-memory-expand"
                  onClick={() => setMemoryExpanded((expanded) => !expanded)}
                  type="button"
                >
                  <ChevronDown aria-hidden="true" className={memoryExpanded ? 'expanded' : ''} />
                  <span>
                    {memoryExpanded
                      ? resources.myPage.memoryCollapse
                      : resources.myPage.memoryShowAll(hiddenMemoryCount)}
                  </span>
                </button>
              ) : null}
            </div>
          </>
        )}
      </section>

      <div className="my-workspace-grid profile-only">
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
        {renderAbilityHeatmap('profile-ability-heatmap-title', 'my-card profile-heatmap-section')}
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
              </aside>
            </div>
            {renderAbilityHeatmap('ability-dialog-heatmap-title')}
          </section>
        </div>
      ) : null}
    </section>
  );
}

function heatmapCellStyle(score: number): CSSProperties {
  const clampedScore = Math.max(0, Math.min(10, score));
  return { '--ability-heat-alpha': String(0.06 + clampedScore * 0.026) } as CSSProperties;
}
