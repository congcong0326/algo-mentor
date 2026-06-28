import { AlertCircle, Check } from 'lucide-react';
import { useEffect, useState } from 'react';
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
  PracticeCoachStyle,
  PracticeResponseLanguage,
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

const responseLanguageOptions: PracticeResponseLanguage[] = ['ZH_CN', 'EN_US'];

export default function MyPage() {
  const { resources } = useI18n();
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
      responseLanguage: update.responseLanguage ?? aiPreference.responseLanguage,
    };
    if (
      nextRequest.coachStyle === aiPreference.coachStyle
      && nextRequest.responseLanguage === aiPreference.responseLanguage
    ) {
      return;
    }
    setAiPreference({
      ...aiPreference,
      ...nextRequest,
      coachStyleLabel: resources.aiPreference.coachStyleLabels[nextRequest.coachStyle],
      responseLanguageLabel: resources.aiPreference.responseLanguageLabels[nextRequest.responseLanguage],
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

  return (
    <section className="my-page" aria-label={resources.nav.my}>
      <article className="my-card ai-preference-card" aria-labelledby="ai-preference-title">
        <div className="ai-preference-heading">
          <div>
            <h2 id="ai-preference-title">{resources.aiPreference.title}</h2>
            <p>{resources.aiPreference.subtitle}</p>
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
                      {aiPreference.coachStyle === style ? <Check aria-hidden="true" /> : null}
                      {resources.aiPreference.coachStyleLabels[style]}
                    </span>
                    <span className="preference-option-description">
                      {resources.aiPreference.coachStyleDescriptions[style]}
                    </span>
                  </button>
                ))}
              </div>
            </fieldset>
            <fieldset className="preference-control-group">
              <legend>{resources.aiPreference.responseLanguage}</legend>
              <div className="response-language-grid">
                {responseLanguageOptions.map((language) => (
                  <button
                    aria-pressed={aiPreference.responseLanguage === language}
                    className={`preference-option ${aiPreference.responseLanguage === language ? 'selected' : ''}`}
                    disabled={preferenceSaving}
                    key={language}
                    onClick={() => void saveAiPreference({ responseLanguage: language })}
                    type="button"
                  >
                    <span className="preference-option-title">
                      {aiPreference.responseLanguage === language ? <Check aria-hidden="true" /> : null}
                      {resources.aiPreference.responseLanguageLabels[language]}
                    </span>
                    <span className="preference-option-description">
                      {resources.aiPreference.responseLanguageDescriptions[language]}
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
        <h2 id="ability-radar-title" className="visually-hidden">{resources.home.abilityRadarTitle}</h2>
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
      </article>
    </section>
  );
}
