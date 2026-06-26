import { BrainCircuit, CalendarCheck, Library, Sparkles } from 'lucide-react';
import type { AppView } from './app/navigation';
import { useI18n } from './i18n/I18nProvider';

interface HomeDashboardProps {
  onNavigate: (view: AppView) => void;
}

export default function HomeDashboard({ onNavigate }: HomeDashboardProps) {
  const { resources } = useI18n();
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
            <button className="primary-button hero-action" onClick={() => onNavigate('learningPlans')} type="button">
              <span>{resources.home.generatePlan}</span>
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
