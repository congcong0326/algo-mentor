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
  const selected = getDifficultyDistribution(value);
  const valueText = `${selected.label}：简单 ${selected.easyPercent}%，中等 ${selected.mediumPercent}%，困难 ${selected.hardPercent}%`;

  return (
    <div className="difficulty-control">
      <label className="topic-field">
        <span>难度分布</span>
        <input
          aria-label="难度分布"
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
        <span>简单 {selected.easyPercent}%</span>
        <span>中等 {selected.mediumPercent}%</span>
        <span>困难 {selected.hardPercent}%</span>
      </div>
    </div>
  );
}
