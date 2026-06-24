import { ArrowRight, BrainCircuit, CalendarCheck, Library, ListChecks, Sparkles } from 'lucide-react';
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
  const workflowSteps = [
    ['1', resources.home.stepPickTitle, resources.home.stepPickDescription],
    ['2', resources.home.stepPracticeTitle, resources.home.stepPracticeDescription],
    ['3', resources.home.stepExplainTitle, resources.home.stepExplainDescription],
    ['∞', resources.home.stepReviewTitle, resources.home.stepReviewDescription],
  ];

  return (
    <section className="home-page" aria-label={resources.home.ariaLabel}>
      <section className="home-hero" aria-labelledby="home-title">
        <div className="home-hero-copy">
          <p className="home-kicker">{resources.home.kicker}</p>
          <h1 id="home-title">{resources.home.title}</h1>
          <p className="home-subtitle">
            {resources.home.subtitle}
          </p>
          <div className="home-hero-actions">
            <button className="primary-button hero-action" onClick={() => onNavigate('learningPlans')} type="button">
              <Sparkles aria-hidden="true" />
              <span>{resources.home.generatePlan}</span>
            </button>
            <button className="secondary-button hero-action" onClick={() => onNavigate('problems')} type="button">
              <Library aria-hidden="true" />
              <span>{resources.home.browseProblems}</span>
            </button>
          </div>
        </div>

        <div className="home-preview" aria-label={resources.home.previewLabel}>
          <div className="preview-window">
            <div className="preview-toolbar" aria-hidden="true">
              <span />
              <span />
              <span />
            </div>
            <div className="preview-content">
              <div className="preview-summary">
                <span>{resources.home.previewFocusLabel}</span>
                <strong>{resources.home.previewFocusValue}</strong>
              </div>
              <div className="preview-task active">
                <ListChecks aria-hidden="true" />
                <span>{resources.home.previewTaskOne}</span>
                <strong>{resources.home.previewTaskOneStatus}</strong>
              </div>
              <div className="preview-task">
                <BrainCircuit aria-hidden="true" />
                <span>{resources.home.previewTaskTwo}</span>
                <strong>{resources.home.previewTaskTwoStatus}</strong>
              </div>
              <div className="preview-task">
                <CalendarCheck aria-hidden="true" />
                <span>{resources.home.previewTaskThree}</span>
                <strong>{resources.home.previewTaskThreeStatus}</strong>
              </div>
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

      <section className="home-section" aria-labelledby="home-workflow-title">
        <div className="home-section-header">
          <p className="eyebrow">{resources.home.loopKicker}</p>
          <h2 id="home-workflow-title">{resources.home.loopTitle}</h2>
        </div>
        <ol className="home-workflow" aria-label={resources.home.loopLabel}>
          {workflowSteps.map(([index, title, description]) => (
            <li className="home-workflow-step" key={title}>
              <span className="workflow-index" aria-hidden="true">{index}</span>
              <strong>{title}</strong>
              <span>{description}</span>
            </li>
          ))}
        </ol>
      </section>

      <section className="home-cta" aria-label={resources.home.ctaLabel}>
        <div>
          <h2>{resources.home.ctaTitle}</h2>
          <p>{resources.home.ctaDescription}</p>
        </div>
        <button className="primary-button hero-action" onClick={() => onNavigate('learningPlans')} type="button">
          <span>{resources.home.enterPlans}</span>
          <ArrowRight aria-hidden="true" />
        </button>
      </section>
    </section>
  );
}
