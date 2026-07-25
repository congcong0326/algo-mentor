import type {
  ProblemAlgorithmKey,
  ProblemComplexityKey,
  ProblemComplexityValue,
  ProblemDataStructureKey,
  ProblemSolutionOutlineV1,
} from '../types/api';
import {
  problemAlgorithmOptions,
  problemComplexityOptions,
  problemDataStructureOptions,
} from './problemNoteOptions';

interface ProblemSolutionOutlineFormProps {
  disabled?: boolean;
  onChange: (outline: ProblemSolutionOutlineV1) => void;
  value: ProblemSolutionOutlineV1;
}

export default function ProblemSolutionOutlineForm({
  disabled = false,
  onChange,
  value,
}: ProblemSolutionOutlineFormProps) {
  function update(patch: Partial<ProblemSolutionOutlineV1>) {
    onChange({ ...value, ...patch });
  }

  return (
    <div className="problem-note-outline-form">
      <label className="problem-note-field problem-note-field-wide">
        <span>核心思路</span>
        <textarea
          disabled={disabled}
          onChange={(event) => update({ coreIdea: event.target.value })}
          rows={4}
          value={value.coreIdea}
        />
      </label>

      <OptionChecklist<ProblemDataStructureKey>
        disabled={disabled}
        label="数据结构"
        onChange={(dataStructures) => update({
          dataStructures,
          dataStructureNotes: dataStructures.length > 0 ? value.dataStructureNotes : '',
        })}
        options={problemDataStructureOptions}
        value={value.dataStructures}
      />
      {value.dataStructures.includes('OTHER') && (
        <CommaListField
          disabled={disabled}
          label="自定义数据结构"
          onChange={(customDataStructures) => update({ customDataStructures })}
          value={value.customDataStructures}
        />
      )}
      {value.dataStructures.length > 0 && (
        <label className="problem-note-field problem-note-field-wide">
          <span>数据结构说明</span>
          <textarea
            disabled={disabled}
            onChange={(event) => update({ dataStructureNotes: event.target.value })}
            placeholder="记录这些数据结构在本题中的作用"
            rows={3}
            value={value.dataStructureNotes}
          />
        </label>
      )}

      <OptionChecklist<ProblemAlgorithmKey>
        disabled={disabled}
        label="算法"
        onChange={(algorithms) => update({
          algorithms,
          algorithmNotes: algorithms.length > 0 ? value.algorithmNotes : '',
        })}
        options={problemAlgorithmOptions}
        value={value.algorithms}
      />
      {value.algorithms.includes('OTHER') && (
        <CommaListField
          disabled={disabled}
          label="自定义算法"
          onChange={(customAlgorithms) => update({ customAlgorithms })}
          value={value.customAlgorithms}
        />
      )}
      {value.algorithms.length > 0 && (
        <label className="problem-note-field problem-note-field-wide">
          <span>算法说明</span>
          <textarea
            disabled={disabled}
            onChange={(event) => update({ algorithmNotes: event.target.value })}
            placeholder="记录算法在本题中的使用方式或关键步骤"
            rows={3}
            value={value.algorithmNotes}
          />
        </label>
      )}

      <ComplexityField
        disabled={disabled}
        label="时间复杂度"
        onChange={(timeComplexity) => update({ timeComplexity })}
        value={value.timeComplexity}
      />
      <ComplexityField
        disabled={disabled}
        label="空间复杂度"
        onChange={(spaceComplexity) => update({ spaceComplexity })}
        value={value.spaceComplexity}
      />

      <label className="problem-note-field problem-note-field-wide">
        <span>边界与易错点</span>
        <textarea
          disabled={disabled}
          onChange={(event) => update({ edgeCases: event.target.value })}
          rows={4}
          value={value.edgeCases}
        />
      </label>
    </div>
  );
}

function OptionChecklist<T extends string>({
  disabled,
  label,
  onChange,
  options,
  value,
}: {
  disabled: boolean;
  label: string;
  onChange: (value: T[]) => void;
  options: Array<{ key: T; label: string }>;
  value: T[];
}) {
  return (
    <fieldset className="problem-note-options">
      <legend>{label}</legend>
      <div className="problem-note-option-grid">
        {options.map((option) => (
          <label key={option.key}>
            <input
              checked={value.includes(option.key)}
              disabled={disabled}
              onChange={(event) => onChange(event.target.checked
                ? [...value, option.key]
                : value.filter((item) => item !== option.key))}
              type="checkbox"
            />
            <span>{option.label}</span>
          </label>
        ))}
      </div>
    </fieldset>
  );
}

function CommaListField({
  disabled,
  label,
  onChange,
  value,
}: {
  disabled: boolean;
  label: string;
  onChange: (value: string[]) => void;
  value: string[];
}) {
  return (
    <label className="problem-note-field">
      <span>{label}</span>
      <input
        disabled={disabled}
        onChange={(event) => onChange(event.target.value
          .split(/[,，]/)
          .map((item) => item.trim())
          .filter(Boolean))}
        value={value.join('，')}
      />
    </label>
  );
}

function ComplexityField({
  disabled,
  label,
  onChange,
  value,
}: {
  disabled: boolean;
  label: string;
  onChange: (value: ProblemComplexityValue) => void;
  value: ProblemComplexityValue;
}) {
  return (
    <label className="problem-note-field">
      <span>{label}</span>
      <select
        disabled={disabled}
        onChange={(event) => {
          const key = event.target.value as ProblemComplexityKey | '';
          onChange({ key: key || null, customText: key === 'OTHER' ? value.customText ?? '' : null });
        }}
        value={value.key ?? ''}
      >
        <option value="">未填写</option>
        {problemComplexityOptions.map((option) => (
          <option key={option.key} value={option.key}>{option.label}</option>
        ))}
      </select>
      {value.key === 'OTHER' && (
        <input
          aria-label={`${label}自定义值`}
          disabled={disabled}
          onChange={(event) => onChange({ key: 'OTHER', customText: event.target.value })}
          placeholder="例如 O(m+n)"
          value={value.customText ?? ''}
        />
      )}
    </label>
  );
}
