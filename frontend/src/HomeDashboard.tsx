import { AlertCircle, BrainCircuit, CalendarCheck, ClipboardList, Library, RotateCw } from 'lucide-react';
import { useEffect, useState } from 'react';
import AbilityRadarChart from './ability/AbilityRadarChart';
import type { AppView } from './app/navigation';
import { useI18n } from './i18n/I18nProvider';
import { getAbilityProfile, requireApiData } from './services/api';
import type { AbilityProfileResponse } from './types/api';

interface HomeDashboardProps {
  onNavigate?: (view: AppView) => void;
  onPrimaryAction?: () => void;
  primaryActionLabel?: string;
}

export default function HomeDashboard({ onNavigate, onPrimaryAction, primaryActionLabel }: HomeDashboardProps) {
  const { resources } = useI18n();
  const isPublicHome = !!onPrimaryAction || !!primaryActionLabel;
  const [abilityProfile, setAbilityProfile] = useState<AbilityProfileResponse>();
  const [abilityLoading, setAbilityLoading] = useState(!isPublicHome);
  const [abilityError, setAbilityError] = useState('');

  useEffect(() => {
    if (isPublicHome) {
      return undefined;
    }

    const controller = new AbortController();
    void loadAbilityProfile(controller.signal);
    return () => controller.abort();
  }, [isPublicHome]);

  const featureCards = [
    {
      title: resources.home.featurePlanTitle,
      description: resources.home.featurePlanDescription,
      icon: CalendarCheck,
    },
    {
      title: resources.home.featureProblemTitle,
      description: resources.home.featureProblemDescription,
      icon: Library,
    },
    {
      title: resources.home.featureAiTitle,
      description: resources.home.featureAiDescription,
      icon: BrainCircuit,
    },
  ];
  const companyMarks = ['A', 'AWS', 'T', 'S', 'G', 'Meta', 'M', 'N', 'A'];
  const handlePrimaryAction = onPrimaryAction ?? (() => onNavigate?.('learningPlans'));

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

  if (!isPublicHome) {
    return (
      <section className="home-workbench" aria-label={resources.home.workspaceAriaLabel}>
        <div className="home-workbench-main">
          <section className="workbench-panel workbench-focus-panel" aria-labelledby="home-workbench-title">
            <p className="eyebrow">{resources.home.workspaceKicker}</p>
            <h1 id="home-workbench-title">{resources.home.workspaceTitle}</h1>
            <p>{resources.home.workspaceSubtitle}</p>
          </section>
          <section className="workbench-grid" aria-label={resources.home.workspaceSectionsLabel}>
            <article className="workbench-panel">
              <ClipboardList aria-hidden="true" />
              <h2>{resources.home.recentPracticeTitle}</h2>
              <p>{resources.home.recentPracticeEmpty}</p>
            </article>
            <article className="workbench-panel">
              <CalendarCheck aria-hidden="true" />
              <h2>{resources.home.planPreviewTitle}</h2>
              <p>{resources.home.planPreviewEmpty}</p>
            </article>
            <article className="workbench-panel">
              <RotateCw aria-hidden="true" />
              <h2>{resources.home.reviewQueueTitle}</h2>
              <p>{resources.home.reviewQueueEmpty}</p>
            </article>
          </section>
        </div>
        <aside className="ability-panel" aria-labelledby="ability-radar-title">
          <div className="ability-panel-header">
            <div>
              <h2 id="ability-radar-title">{resources.home.abilityRadarTitle}</h2>
              <p>{resources.home.abilityRadarSubtitle}</p>
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
            <AbilityRadarChart profile={abilityProfile} />
          ) : (
            <div className="ability-state">{resources.home.abilityEmpty}</div>
          )}
        </aside>
      </section>
    );
  }

  return (
    <section className="home-page" aria-label={resources.home.ariaLabel}>
      <section className="home-hero" aria-labelledby="home-title">
        <div className="home-code-backdrop" aria-hidden="true">
          <pre>{`Given an array of integers nums and an integer target,
return indices of the two numbers such that they add up to target.

Example 1:
  Input: nums = [2,7,11,15], target = 9
  Output: [0,1]

for (let i = 0; i < nums.length; i++) {
  const complement = target - nums[i];
  if (hashmap.has(complement)) return [hashmap.get(complement), i];
  hashmap.set(nums[i], i);
}`}</pre>
        </div>
        <div className="home-hero-copy">
          <p className="home-kicker">{resources.home.kicker}</p>
          <h1 id="home-title">
            {resources.home.title}
            <strong>{resources.home.titleHighlight}</strong>
          </h1>
          <p className="home-subtitle">
            {resources.home.subtitle}
          </p>
          <div className="home-hero-actions">
            <button className="primary-button hero-action" onClick={handlePrimaryAction} type="button">
              <span>{primaryActionLabel ?? resources.home.startUsing}</span>
            </button>
          </div>
          <div className="home-company-strip" aria-label={resources.home.companyStripLabel}>
            <p>{resources.home.companyStripTitle}</p>
            <div>
              {companyMarks.map((mark, index) => (
                <span className="company-mark" key={`${mark}-${index}`}>{mark}</span>
              ))}
            </div>
          </div>
        </div>
      </section>

      <section className="home-section" aria-labelledby="home-capabilities-title">
        <div className="home-section-header">
          <p className="eyebrow">{resources.home.capabilitiesKicker}</p>
          <h2 id="home-capabilities-title">{resources.home.capabilitiesTitle}</h2>
        </div>
        <div className="home-card-grid">
          {featureCards.map((card) => {
            const Icon = card.icon;
            return (
              <article className="home-feature-card" key={card.title}>
                <Icon aria-hidden="true" />
                <h3>{card.title}</h3>
                <p>{card.description}</p>
              </article>
            );
          })}
        </div>
      </section>

      <div className="home-review-anchor" aria-hidden="true">{resources.home.reviewCardCta}</div>
    </section>
  );
}
