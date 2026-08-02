import {
  AlertCircle,
  BookOpenCheck,
  BrainCircuit,
  Check,
  ChevronDown,
  CircleHelp,
  Eye,
  EyeOff,
  KeyRound,
  LogOut,
  Settings2,
  UserRound,
  X,
} from 'lucide-react';
import { type FormEvent, useEffect, useState } from 'react';
import { useI18n } from './i18n/I18nProvider';
import {
  getReviewPreference,
  getUserAiPreference,
  requireApiData,
  updateReviewPreference,
  updateUserAiPreference,
  updateUserPassword,
} from './services/api';
import type {
  CurrentUser,
  PracticeCoachStyle,
  ReviewPreference,
  ReviewPreferenceRequest,
  UserAiPreference,
  UserAiPreferenceRequest,
  UserPasswordUpdateResponse,
} from './types/api';

const coachStyleOptions: PracticeCoachStyle[] = ['GUIDED', 'DIRECT'];

interface SettingsPageProps {
  currentUser: CurrentUser;
  logoutPending?: boolean;
  onCurrentUserUpdated?: (user: CurrentUser) => void;
  onLogout: () => void;
  passwordLoginEnabled?: boolean;
}

interface ReviewSettingHelpProps {
  description: string;
  label: string;
  tooltipId: string;
}

function ReviewSettingHelp({ description, label, tooltipId }: ReviewSettingHelpProps) {
  const { resources } = useI18n();

  return (
    <span className="toolbar-tooltip-wrap review-setting-tooltip-wrap">
      <span
        aria-describedby={tooltipId}
        aria-label={`${label}${resources.settingsPage.helpSuffix}`}
        className="icon-button review-setting-help"
        role="img"
        tabIndex={0}
      >
        <CircleHelp aria-hidden="true" />
      </span>
      <span className="toolbar-tooltip review-setting-tooltip" id={tooltipId} role="tooltip">
        {description}
      </span>
    </span>
  );
}

export default function SettingsPage({
  currentUser,
  logoutPending = false,
  onCurrentUserUpdated,
  onLogout,
  passwordLoginEnabled = true,
}: SettingsPageProps) {
  const { resources } = useI18n();
  const [aiPreference, setAiPreference] = useState<UserAiPreference>();
  const [preferenceLoading, setPreferenceLoading] = useState(true);
  const [preferenceError, setPreferenceError] = useState('');
  const [preferenceSaveError, setPreferenceSaveError] = useState('');
  const [preferenceSaving, setPreferenceSaving] = useState(false);
  const [preferenceSaved, setPreferenceSaved] = useState(false);
  const [reviewPreference, setReviewPreference] = useState<ReviewPreference>();
  const [reviewPreferenceLoading, setReviewPreferenceLoading] = useState(true);
  const [reviewPreferenceError, setReviewPreferenceError] = useState('');
  const [reviewPreferenceSaveError, setReviewPreferenceSaveError] = useState('');
  const [reviewPreferenceSaving, setReviewPreferenceSaving] = useState(false);
  const [passwordDialogOpen, setPasswordDialogOpen] = useState(false);
  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [showPasswords, setShowPasswords] = useState(false);
  const [passwordSaving, setPasswordSaving] = useState(false);
  const [passwordError, setPasswordError] = useState('');
  const [passwordSuccess, setPasswordSuccess] = useState('');
  const userLabel = currentUser.displayName || currentUser.email || resources.app.unknownUser(currentUser.id);
  const passwordSession = currentUser.sessionAuthenticationMethod === 'PASSWORD';
  const passwordManagementAvailable = passwordLoginEnabled
    && (passwordSession
      || currentUser.sessionAuthenticationMethod === 'OIDC'
      || currentUser.sessionAuthenticationMethod === 'OAUTH2');
  const passwordEmailAvailable = Boolean(currentUser.email?.trim());

  useEffect(() => {
    const controller = new AbortController();
    void loadAiPreference(controller.signal);
    void loadReviewPreference(controller.signal);
    return () => controller.abort();
  }, []);

  useEffect(() => {
    if (!passwordDialogOpen) {
      return undefined;
    }
    function closeOnEscape(event: KeyboardEvent) {
      if (event.key === 'Escape' && !passwordSaving) {
        closePasswordDialog();
      }
    }
    document.addEventListener('keydown', closeOnEscape);
    return () => document.removeEventListener('keydown', closeOnEscape);
  }, [passwordDialogOpen, passwordSaving]);

  async function loadAiPreference(signal?: AbortSignal) {
    setPreferenceLoading(true);
    setPreferenceError('');
    setPreferenceSaveError('');
    setPreferenceSaved(false);
    try {
      const response = await getUserAiPreference(signal);
      setAiPreference(requireApiData(response, resources.aiPreference.loadFailed));
    } catch (error) {
      if (!signal?.aborted) {
        setPreferenceError(error instanceof Error ? error.message : resources.aiPreference.loadFailed);
      }
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
    const nextRequest = { coachStyle: update.coachStyle ?? aiPreference.coachStyle };
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

  async function loadReviewPreference(signal?: AbortSignal) {
    setReviewPreferenceLoading(true);
    setReviewPreferenceError('');
    setReviewPreferenceSaveError('');
    try {
      const response = await getReviewPreference(signal);
      setReviewPreference(requireApiData(response, resources.settingsPage.reviewLoadFailed));
    } catch (error) {
      if (!signal?.aborted) {
        setReviewPreferenceError(error instanceof Error ? error.message : resources.settingsPage.reviewLoadFailed);
      }
    } finally {
      if (!signal?.aborted) {
        setReviewPreferenceLoading(false);
      }
    }
  }

  async function saveReviewPreference(update: ReviewPreferenceRequest) {
    if (!reviewPreference || reviewPreferenceSaving) {
      return;
    }
    const previousPreference = reviewPreference;
    setReviewPreference({ ...reviewPreference, ...update });
    setReviewPreferenceSaving(true);
    setReviewPreferenceSaveError('');
    try {
      const response = await updateReviewPreference(update);
      setReviewPreference(requireApiData(response, resources.settingsPage.reviewSaveFailed));
    } catch (error) {
      setReviewPreference(previousPreference);
      setReviewPreferenceSaveError(error instanceof Error ? error.message : resources.settingsPage.reviewSaveFailed);
    } finally {
      setReviewPreferenceSaving(false);
    }
  }

  function openPasswordDialog() {
    if (!passwordLoginEnabled) {
      return;
    }
    setCurrentPassword('');
    setNewPassword('');
    setConfirmPassword('');
    setShowPasswords(false);
    setPasswordError('');
    setPasswordDialogOpen(true);
  }

  function closePasswordDialog() {
    if (!passwordSaving) {
      setPasswordDialogOpen(false);
      setPasswordError('');
    }
  }

  async function savePassword(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!passwordLoginEnabled || passwordSaving) {
      return;
    }
    setPasswordError('');
    if (newPassword.length < 8) {
      setPasswordError(resources.settingsPage.passwordTooShort);
      return;
    }
    if (newPassword !== confirmPassword) {
      setPasswordError(resources.settingsPage.passwordMismatch);
      return;
    }
    setPasswordSaving(true);
    try {
      const result = requireApiData<UserPasswordUpdateResponse>(
        await updateUserPassword({
          ...(passwordSession ? { currentPassword } : {}),
          newPassword,
          confirmPassword,
        }),
        resources.settingsPage.passwordUpdateFailed);
      onCurrentUserUpdated?.({ ...currentUser, passwordConfigured: result.passwordConfigured });
      setPasswordSuccess(result.operation === 'CREATED'
        ? resources.settingsPage.passwordCreated
        : resources.settingsPage.passwordUpdated);
      setPasswordDialogOpen(false);
    } catch (error) {
      setPasswordError(error instanceof Error ? error.message : resources.settingsPage.passwordUpdateFailed);
    } finally {
      setPasswordSaving(false);
    }
  }

  return (
    <section className="settings-page" aria-label={resources.settingsPage.ariaLabel}>
      <header className="settings-page-header">
        <p className="my-page-kicker">
          <Settings2 aria-hidden="true" />
          <span>{resources.settingsPage.kicker}</span>
        </p>
        <h1>{resources.settingsPage.title}</h1>
        <p>{resources.settingsPage.subtitle}</p>
      </header>

      <section className="settings-section" aria-labelledby="settings-ai-title">
        <div className="settings-section-copy">
          <span className="settings-section-icon" aria-hidden="true"><BrainCircuit /></span>
          <div>
            <h2 id="settings-ai-title">{resources.settingsPage.learningTitle}</h2>
            <p>{resources.settingsPage.learningDescription}</p>
          </div>
        </div>
        <div className="settings-section-control">
          <div className="preference-save-status" aria-live="polite">
            {preferenceSaving ? resources.aiPreference.saving : preferenceSaved ? resources.aiPreference.saved : null}
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
                  <span>{resources.settingsPage.currentCoach}</span>
                  <strong>{resources.aiPreference.coachStyleLabels[aiPreference.coachStyle]}</strong>
                </div>
              </div>
              <fieldset className="preference-control-group">
                <legend>{resources.aiPreference.coachStyle}</legend>
                <div className="coach-style-grid settings-coach-style-grid">
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
        </div>
      </section>

      <section className="settings-section" aria-labelledby="settings-review-title">
        <div className="settings-section-copy">
          <span className="settings-section-icon" aria-hidden="true"><BookOpenCheck /></span>
          <div>
            <h2 id="settings-review-title">{resources.settingsPage.reviewTitle}</h2>
            <p>{resources.settingsPage.reviewDescription}</p>
          </div>
        </div>
        <div className="settings-section-control">
          <div className="preference-save-status" aria-live="polite">
            {reviewPreferenceSaving ? resources.settingsPage.reviewSaving : null}
          </div>
          {reviewPreferenceLoading ? (
            <div className="preference-state" role="status">{resources.settingsPage.reviewLoading}</div>
          ) : reviewPreferenceError ? (
            <div className="preference-state error" role="alert">
              <AlertCircle aria-hidden="true" />
              <span>{reviewPreferenceError}</span>
              <button className="secondary-button compact" onClick={() => void loadReviewPreference()} type="button">
                {resources.app.retry}
              </button>
            </div>
          ) : reviewPreference ? (
            <div className="preference-controls">
              <details className="advanced-settings">
                <summary>
                  <span>
                    <strong>{resources.settingsPage.advancedReviewTitle}</strong>
                    <small>{resources.settingsPage.advancedReviewDescription}</small>
                  </span>
                  <ChevronDown aria-hidden="true" />
                </summary>
                <fieldset className="preference-control-group advanced-settings-fields">
                  <legend>{resources.settingsPage.fsrsParameters}</legend>
                  <ReviewNumberField
                    description={resources.settingsPage.desiredRetentionDescription}
                    disabled={reviewPreferenceSaving}
                    label={resources.settingsPage.desiredRetention}
                    max="0.97"
                    min="0.70"
                    onBlur={(value) => void saveReviewPreference({ desiredRetention: value })}
                    onChange={(value) => setReviewPreference({ ...reviewPreference, desiredRetention: value })}
                    step="0.01"
                    tooltipId="review-desired-retention-tooltip"
                    value={reviewPreference.desiredRetention}
                  />
                  <ReviewNumberField
                    description={resources.settingsPage.dailyNewLimitDescription}
                    disabled={reviewPreferenceSaving}
                    label={resources.settingsPage.dailyNewLimit}
                    min="0"
                    onBlur={(value) => void saveReviewPreference({ dailyNewLimit: value })}
                    onChange={(value) => setReviewPreference({ ...reviewPreference, dailyNewLimit: value })}
                    tooltipId="review-daily-new-limit-tooltip"
                    value={reviewPreference.dailyNewLimit}
                  />
                  <ReviewNumberField
                    description={resources.settingsPage.dailyLearningLimitDescription}
                    disabled={reviewPreferenceSaving}
                    label={resources.settingsPage.dailyLearningLimit}
                    min="1"
                    onBlur={(value) => void saveReviewPreference({ dailyLearningLimit: value })}
                    onChange={(value) => setReviewPreference({ ...reviewPreference, dailyLearningLimit: value })}
                    tooltipId="review-daily-learning-limit-tooltip"
                    value={reviewPreference.dailyLearningLimit}
                  />
                  <ReviewNumberField
                    description={resources.settingsPage.dailyReviewLimitDescription}
                    disabled={reviewPreferenceSaving}
                    label={resources.settingsPage.dailyReviewLimit}
                    min="1"
                    onBlur={(value) => void saveReviewPreference({ dailyReviewLimit: value })}
                    onChange={(value) => setReviewPreference({ ...reviewPreference, dailyReviewLimit: value })}
                    tooltipId="review-daily-review-limit-tooltip"
                    value={reviewPreference.dailyReviewLimit}
                  />
                  <ReviewNumberField
                    description={resources.settingsPage.maximumIntervalDaysDescription}
                    disabled={reviewPreferenceSaving}
                    label={resources.settingsPage.maximumIntervalDays}
                    min="1"
                    onBlur={(value) => void saveReviewPreference({ maximumIntervalDays: value })}
                    onChange={(value) => setReviewPreference({ ...reviewPreference, maximumIntervalDays: value })}
                    tooltipId="review-maximum-interval-tooltip"
                    value={reviewPreference.maximumIntervalDays}
                  />
                  <label className="settings-toggle-row advanced-settings-toggle">
                    <span>
                      <strong>{resources.settingsPage.enableFuzzing}</strong>
                      <small>{resources.settingsPage.enableFuzzingDescription}</small>
                    </span>
                    <input
                      checked={reviewPreference.enableFuzzing}
                      disabled={reviewPreferenceSaving}
                      onChange={(event) => void saveReviewPreference({ enableFuzzing: event.target.checked })}
                      type="checkbox"
                    />
                  </label>
                </fieldset>
              </details>
              {reviewPreferenceSaveError ? (
                <div className="preference-save-error" role="alert">
                  <AlertCircle aria-hidden="true" />
                  <span>{reviewPreferenceSaveError}</span>
                </div>
              ) : null}
            </div>
          ) : null}
        </div>
      </section>

      <section className="settings-section" aria-labelledby="settings-account-title">
        <div className="settings-section-copy">
          <span className="settings-section-icon" aria-hidden="true"><UserRound /></span>
          <div>
            <h2 id="settings-account-title">{resources.settingsPage.accountTitle}</h2>
            <p>{resources.settingsPage.accountDescription}</p>
          </div>
        </div>
        <div className="settings-section-control account-settings-control">
          <div className="account-settings-row">
            <div>
              <span>{resources.settingsPage.signedInAs}</span>
              <strong>{userLabel}</strong>
              <small>{currentUser.email} · {resources.settingsPage.activeStatus}</small>
            </div>
            <button className="secondary-button compact" disabled={logoutPending} onClick={onLogout} type="button">
              <LogOut aria-hidden="true" />
              <span>{logoutPending ? resources.app.loggingOut : resources.app.logout}</span>
            </button>
          </div>
          {passwordLoginEnabled ? (
            <div className="account-settings-row password-settings-row">
              <div>
                <span>{resources.settingsPage.passwordTitle}</span>
                <strong>{currentUser.passwordConfigured
                  ? resources.settingsPage.passwordConfigured
                  : !passwordEmailAvailable
                    ? resources.settingsPage.passwordEmailUnavailable
                    : resources.settingsPage.passwordNotConfigured}</strong>
                {passwordSuccess ? <small aria-live="polite">{passwordSuccess}</small> : null}
              </div>
              {passwordManagementAvailable && (currentUser.passwordConfigured || passwordEmailAvailable) ? (
                <button className="secondary-button compact" onClick={openPasswordDialog} type="button">
                  <KeyRound aria-hidden="true" />
                  <span>{currentUser.passwordConfigured
                    ? resources.settingsPage.changePassword
                    : resources.settingsPage.setPassword}</span>
                </button>
              ) : null}
            </div>
          ) : null}
        </div>
      </section>
      {passwordLoginEnabled && passwordDialogOpen ? (
        <div className="password-dialog-backdrop" onMouseDown={(event) => {
          if (event.target === event.currentTarget) {
            closePasswordDialog();
          }
        }}>
          <form
            aria-describedby="password-dialog-description"
            aria-labelledby="password-dialog-title"
            aria-modal="true"
            className="password-dialog"
            onSubmit={(event) => void savePassword(event)}
            role="dialog"
          >
            <header>
              <div>
                <h2 id="password-dialog-title">{currentUser.passwordConfigured
                  ? resources.settingsPage.passwordDialogChangeTitle
                  : resources.settingsPage.passwordDialogSetTitle}</h2>
                <p id="password-dialog-description">{currentUser.passwordConfigured
                  ? resources.settingsPage.passwordDialogChangeDescription
                  : resources.settingsPage.passwordDialogSetDescription}</p>
              </div>
              <button
                aria-label={resources.common.close}
                className="icon-button"
                disabled={passwordSaving}
                onClick={closePasswordDialog}
                title={resources.common.close}
                type="button"
              >
                <X aria-hidden="true" />
              </button>
            </header>
            {passwordError ? <p className="error-text" role="alert">{passwordError}</p> : null}
            {passwordSession ? (
              <PasswordField
                autoComplete="current-password"
                autoFocus
                label={resources.settingsPage.currentPassword}
                onChange={setCurrentPassword}
                showPassword={showPasswords}
                toggleLabel={showPasswords ? resources.settingsPage.hidePassword : resources.settingsPage.showPassword}
                onToggle={() => setShowPasswords((value) => !value)}
                value={currentPassword}
              />
            ) : null}
            <PasswordField
              autoComplete="new-password"
              autoFocus={!passwordSession}
              label={resources.settingsPage.newPassword}
              onChange={setNewPassword}
              showPassword={showPasswords}
              toggleLabel={showPasswords ? resources.settingsPage.hidePassword : resources.settingsPage.showPassword}
              onToggle={() => setShowPasswords((value) => !value)}
              value={newPassword}
            />
            <PasswordField
              autoComplete="new-password"
              label={resources.settingsPage.confirmNewPassword}
              onChange={setConfirmPassword}
              showPassword={showPasswords}
              toggleLabel={showPasswords ? resources.settingsPage.hidePassword : resources.settingsPage.showPassword}
              onToggle={() => setShowPasswords((value) => !value)}
              value={confirmPassword}
            />
            <small className="password-dialog-hint">{resources.settingsPage.passwordMinimumLength}</small>
            <footer>
              <button className="secondary-button" disabled={passwordSaving} onClick={closePasswordDialog} type="button">
                {resources.common.cancel}
              </button>
              <button className="primary-button" disabled={passwordSaving} type="submit">
                {passwordSaving ? resources.settingsPage.passwordUpdating : resources.settingsPage.passwordUpdate}
              </button>
            </footer>
          </form>
        </div>
      ) : null}
    </section>
  );
}

interface PasswordFieldProps {
  autoComplete: 'current-password' | 'new-password';
  autoFocus?: boolean;
  label: string;
  onChange: (value: string) => void;
  onToggle: () => void;
  showPassword: boolean;
  toggleLabel: string;
  value: string;
}

function PasswordField({
  autoComplete,
  autoFocus = false,
  label,
  onChange,
  onToggle,
  showPassword,
  toggleLabel,
  value,
}: PasswordFieldProps) {
  return (
    <label className="password-dialog-field">
      <span>{label}</span>
      <span className="password-dialog-input">
        <input
          autoComplete={autoComplete}
          autoFocus={autoFocus}
          onChange={(event) => onChange(event.target.value)}
          type={showPassword ? 'text' : 'password'}
          value={value}
        />
        <button aria-label={toggleLabel} className="icon-button" onClick={onToggle} title={toggleLabel} type="button">
          {showPassword ? <EyeOff aria-hidden="true" /> : <Eye aria-hidden="true" />}
        </button>
      </span>
    </label>
  );
}

interface ReviewNumberFieldProps {
  description: string;
  disabled: boolean;
  label: string;
  max?: string;
  min: string;
  onBlur: (value: number) => void;
  onChange: (value: number) => void;
  step?: string;
  tooltipId: string;
  value: number;
}

function ReviewNumberField({
  description,
  disabled,
  label,
  max,
  min,
  onBlur,
  onChange,
  step,
  tooltipId,
  value,
}: ReviewNumberFieldProps) {
  return (
    <label className="review-setting-field">
      <span className="review-setting-label">
        <span>{label}</span>
        <ReviewSettingHelp description={description} label={label} tooltipId={tooltipId} />
      </span>
      <input
        disabled={disabled}
        max={max}
        min={min}
        onBlur={(event) => onBlur(Number(event.target.value))}
        onChange={(event) => onChange(Number(event.target.value))}
        step={step}
        type="number"
        value={value}
      />
    </label>
  );
}
