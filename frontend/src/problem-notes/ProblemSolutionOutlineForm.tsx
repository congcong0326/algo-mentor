import type {
  ProblemAlgorithmKey,
  ProblemComplexityKey,
  ProblemComplexityValue,
  ProblemDataStructureKey,
  ProblemSolutionOutlineV1,
} from '../types/api';
import { useI18n } from '../i18n/I18nProvider';
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
  const { locale, resources } = useI18n();

  function update(patch: Partial<ProblemSolutionOutlineV1>) {
    onChange({ ...value, ...patch });
  }

  return (
    <div className="problem-note-outline-form">
      <label className="problem-note-field problem-note-field-wide">
        <span>{resources.problemNotes.coreIdea}</span>
        <textarea
          disabled={disabled}
          onChange={(event) => update({ coreIdea: event.target.value })}
          rows={4}
          value={value.coreIdea}
        />
      </label>

      <OptionChecklist<ProblemDataStructureKey>
        disabled={disabled}
        label={resources.problemNotes.dataStructures}
        labels={resources.problemNotes.dataStructureLabels}
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
          delimiter={locale.startsWith('zh') ? '，' : ', '}
          label={resources.problemNotes.customDataStructures}
          onChange={(customDataStructures) => update({ customDataStructures })}
          value={value.customDataStructures}
        />
      )}
      {value.dataStructures.length > 0 && (
        <label className="problem-note-field problem-note-field-wide">
          <span>{resources.problemNotes.dataStructureNotes}</span>
          <textarea
            disabled={disabled}
            onChange={(event) => update({ dataStructureNotes: event.target.value })}
            placeholder={resources.problemNotes.dataStructureNotesPlaceholder}
            rows={3}
            value={value.dataStructureNotes}
          />
        </label>
      )}

      <OptionChecklist<ProblemAlgorithmKey>
        disabled={disabled}
        label={resources.problemNotes.algorithms}
        labels={resources.problemNotes.algorithmLabels}
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
          delimiter={locale.startsWith('zh') ? '，' : ', '}
          label={resources.problemNotes.customAlgorithms}
          onChange={(customAlgorithms) => update({ customAlgorithms })}
          value={value.customAlgorithms}
        />
      )}
      {value.algorithms.length > 0 && (
        <label className="problem-note-field problem-note-field-wide">
          <span>{resources.problemNotes.algorithmNotes}</span>
          <textarea
            disabled={disabled}
            onChange={(event) => update({ algorithmNotes: event.target.value })}
            placeholder={resources.problemNotes.algorithmNotesPlaceholder}
            rows={3}
            value={value.algorithmNotes}
          />
        </label>
      )}

      <ComplexityField
        disabled={disabled}
        emptyLabel={resources.problemNotes.complexityEmpty}
        label={resources.problemNotes.timeComplexity}
        labels={resources.problemNotes.complexityLabels}
        onChange={(timeComplexity) => update({ timeComplexity })}
        value={value.timeComplexity}
      />
      <ComplexityField
        disabled={disabled}
        emptyLabel={resources.problemNotes.complexityEmpty}
        label={resources.problemNotes.spaceComplexity}
        labels={resources.problemNotes.complexityLabels}
        onChange={(spaceComplexity) => update({ spaceComplexity })}
        value={value.spaceComplexity}
      />
    </div>
  );
}

function OptionChecklist<T extends string>({
  disabled,
  label,
  labels,
  onChange,
  options,
  value,
}: {
  disabled: boolean;
  label: string;
  labels: Record<T, string>;
  onChange: (value: T[]) => void;
  options: T[];
  value: T[];
}) {
  return (
    <fieldset className="problem-note-options">
      <legend>{label}</legend>
      <div className="problem-note-option-grid">
        {options.map((option) => (
          <label key={option}>
            <input
              checked={value.includes(option)}
              disabled={disabled}
              onChange={(event) => onChange(event.target.checked
                ? [...value, option]
                : value.filter((item) => item !== option))}
              type="checkbox"
            />
            <span>{labels[option]}</span>
          </label>
        ))}
      </div>
    </fieldset>
  );
}

function CommaListField({
  delimiter,
  disabled,
  label,
  onChange,
  value,
}: {
  delimiter: string;
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
        value={value.join(delimiter)}
      />
    </label>
  );
}

function ComplexityField({
  disabled,
  emptyLabel,
  label,
  labels,
  onChange,
  value,
}: {
  disabled: boolean;
  emptyLabel: string;
  label: string;
  labels: Record<ProblemComplexityKey, string>;
  onChange: (value: ProblemComplexityValue) => void;
  value: ProblemComplexityValue;
}) {
  const { resources } = useI18n();

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
        <option value="">{emptyLabel}</option>
        {problemComplexityOptions.map((option) => (
          <option key={option} value={option}>{labels[option]}</option>
        ))}
      </select>
      {value.key === 'OTHER' && (
        <input
          aria-label={resources.problemNotes.customValueAriaLabel(label)}
          disabled={disabled}
          onChange={(event) => onChange({ key: 'OTHER', customText: event.target.value })}
          placeholder={resources.problemNotes.customValuePlaceholder}
          value={value.customText ?? ''}
        />
      )}
    </label>
  );
}
