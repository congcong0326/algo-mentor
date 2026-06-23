import type { DifficultyDistributionLevel } from '../types/api';
import { difficultyDistributionOptions } from './options';

interface DifficultyDistributionControlProps {
  disabled: boolean;
  value: DifficultyDistributionLevel;
  onChange: (value: DifficultyDistributionLevel) => void;
}

export default function DifficultyDistributionControl({
  disabled,
  value,
  onChange,
}: DifficultyDistributionControlProps) {
  const selectedIndex = Math.max(0, difficultyDistributionOptions.findIndex((option) => option.value === value));
  const selected = difficultyDistributionOptions[selectedIndex] ?? difficultyDistributionOptions[0];

  return (
    <div className="difficulty-control">
      <label className="topic-field">
        <span>难度分布</span>
        <input
          aria-label="难度分布"
          disabled={disabled}
          max={difficultyDistributionOptions.length - 1}
          min={0}
          onChange={(event) => {
            const next = difficultyDistributionOptions[Number(event.target.value)];
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
