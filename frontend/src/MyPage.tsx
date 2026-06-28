import { AlertCircle } from 'lucide-react';
import { useEffect, useState } from 'react';
import AbilityRadarChart from './ability/AbilityRadarChart';
import { useI18n } from './i18n/I18nProvider';
import { getAbilityProfile, requireApiData } from './services/api';
import type { AbilityProfileResponse } from './types/api';

export default function MyPage() {
  const { resources } = useI18n();
  const [abilityProfile, setAbilityProfile] = useState<AbilityProfileResponse>();
  const [abilityLoading, setAbilityLoading] = useState(true);
  const [abilityError, setAbilityError] = useState('');

  useEffect(() => {
    const controller = new AbortController();
    void loadAbilityProfile(controller.signal);
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

  return (
    <section className="my-page" aria-label={resources.nav.my}>
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
