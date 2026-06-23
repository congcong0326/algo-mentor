import type { DifficultyDistributionLevel } from '../types/api';
import { difficultyDistributionOptions } from './options';

interface DifficultyDistributionControlProps {
  disabled: boolean;
  value: DifficultyDistributionLevel;
  onChange: (value: DifficultyDistributionLevel) => void;
}

function clampOptionIndex(value: number): number {
  const maxIndex = difficultyDistributionOptions.length - 1;

  if (!Number.isFinite(value)) {
    return 0;
  }

  return Math.min(Math.max(0, value), maxIndex);
}

export default function DifficultyDistributionControl({
  disabled,
  value,
  onChange,
}: DifficultyDistributionControlProps) {
  const selectedIndex = clampOptionIndex(difficultyDistributionOptions.findIndex((option) => option.value === value));
  const selected = difficultyDistributionOptions[selectedIndex] ?? difficultyDistributionOptions[0];
  const valueText = `${selected.label}：简单 ${selected.easyPercent}%，中等 ${selected.mediumPercent}%，困难 ${selected.hardPercent}%`;

  return (
    <div className="difficulty-control">
      <label className="topic-field">
        <span>难度分布</span>
        <input
          aria-label="难度分布"
          aria-valuetext={valueText}
          disabled={disabled}
          max={difficultyDistributionOptions.length - 1}
          min={0}
          onChange={(event) => {
            const next = difficultyDistributionOptions[clampOptionIndex(Number(event.target.value))];
            onChange(next.value);
          }}
          type="range"
          value={selectedIndex}
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
