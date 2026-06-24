import { useI18n } from '../i18n/I18nProvider';
import { clampDifficultyDistributionValue, getDifficultyDistribution } from './options';

interface DifficultyDistributionControlProps {
  disabled: boolean;
  value: number;
  onChange: (value: number) => void;
}

export default function DifficultyDistributionControl({
  disabled,
  value,
  onChange,
}: DifficultyDistributionControlProps) {
  const { resources } = useI18n();
  const selected = getDifficultyDistribution(value);
  const selectedLabel = resources.labels.difficultyDistribution[selected.labelKey];
  const valueText = resources.learningPlans.distributionValueText(
    selectedLabel,
    selected.easyPercent,
    selected.mediumPercent,
    selected.hardPercent,
  );

  return (
    <div className="difficulty-control">
      <label className="topic-field">
        <span>{resources.learningPlans.difficultyDistribution}</span>
        <input
          aria-label={resources.learningPlans.difficultyDistribution}
          aria-valuetext={valueText}
          disabled={disabled}
          max={100}
          min={0}
          onChange={(event) => {
            onChange(clampDifficultyDistributionValue(Number(event.target.value)));
          }}
          step={1}
          type="range"
          value={selected.value}
        />
      </label>
      <div className="difficulty-ratio-row">
        <span>{resources.learningPlans.easyPercent(selected.easyPercent)}</span>
        <span>{resources.learningPlans.mediumPercent(selected.mediumPercent)}</span>
        <span>{resources.learningPlans.hardPercent(selected.hardPercent)}</span>
      </div>
    </div>
  );
}
