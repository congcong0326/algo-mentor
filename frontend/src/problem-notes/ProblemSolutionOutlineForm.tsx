import { useId } from 'react';
import type {
  ProblemAlgorithmKey,
  ProblemComplexityKey,
  ProblemComplexityValue,
  ProblemDataStructureKey,
  ProblemSolutionOutlineV1,
  UserInputLimits,
} from '../types/api';
import { unicodeCodePointLength } from '../config/userInputLimits';
import { useI18n } from '../i18n/I18nProvider';
import {
  problemAlgorithmOptions,
  problemComplexityOptions,
  problemDataStructureOptions,
} from './problemNoteOptions';

interface ProblemSolutionOutlineFormProps {
  disabled?: boolean;
  limits: UserInputLimits['reviewNote'];
  onChange: (outline: ProblemSolutionOutlineV1) => void;
  value: ProblemSolutionOutlineV1;
}

export default function ProblemSolutionOutlineForm({
  disabled = false,
  limits,
  onChange,
  value,
}: ProblemSolutionOutlineFormProps) {
  const { locale, resources } = useI18n();
  const coreIdeaCounterId = useId();
  const dataStructureNotesCounterId = useId();
  const algorithmNotesCounterId = useId();

  function update(patch: Partial<ProblemSolutionOutlineV1>) {
    onChange({ ...value, ...patch });
  }

  return (
    <div className="problem-note-outline-form">
      <label className="problem-note-field problem-note-field-wide">
        <span>{resources.problemNotes.coreIdea}</span>
        <textarea
          aria-describedby={coreIdeaCounterId}
          aria-invalid={unicodeCodePointLength(value.coreIdea) > limits.coreIdeaMaxChars}
          aria-label={resources.problemNotes.coreIdea}
          disabled={disabled}
          onChange={(event) => update({ coreIdea: event.target.value })}
          rows={4}
          value={value.coreIdea}
        />
        <InputCounter
          current={unicodeCodePointLength(value.coreIdea)}
          id={coreIdeaCounterId}
          max={limits.coreIdeaMaxChars}
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
          maxCount={limits.customItemMaxCount}
          maxItemChars={limits.customItemMaxChars}
          onChange={(customDataStructures) => update({ customDataStructures })}
          value={value.customDataStructures}
        />
      )}
      {value.dataStructures.length > 0 && (
        <label className="problem-note-field problem-note-field-wide">
          <span>{resources.problemNotes.dataStructureNotes}</span>
          <textarea
            aria-describedby={dataStructureNotesCounterId}
            aria-invalid={unicodeCodePointLength(value.dataStructureNotes) > limits.dataStructureNotesMaxChars}
            aria-label={resources.problemNotes.dataStructureNotes}
            disabled={disabled}
            onChange={(event) => update({ dataStructureNotes: event.target.value })}
            placeholder={resources.problemNotes.dataStructureNotesPlaceholder}
            rows={3}
            value={value.dataStructureNotes}
          />
          <InputCounter
            current={unicodeCodePointLength(value.dataStructureNotes)}
            id={dataStructureNotesCounterId}
            max={limits.dataStructureNotesMaxChars}
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
          maxCount={limits.customItemMaxCount}
          maxItemChars={limits.customItemMaxChars}
          onChange={(customAlgorithms) => update({ customAlgorithms })}
          value={value.customAlgorithms}
        />
      )}
      {value.algorithms.length > 0 && (
        <label className="problem-note-field problem-note-field-wide">
          <span>{resources.problemNotes.algorithmNotes}</span>
          <textarea
            aria-describedby={algorithmNotesCounterId}
            aria-invalid={unicodeCodePointLength(value.algorithmNotes) > limits.algorithmNotesMaxChars}
            aria-label={resources.problemNotes.algorithmNotes}
            disabled={disabled}
            onChange={(event) => update({ algorithmNotes: event.target.value })}
            placeholder={resources.problemNotes.algorithmNotesPlaceholder}
            rows={3}
            value={value.algorithmNotes}
          />
          <InputCounter
            current={unicodeCodePointLength(value.algorithmNotes)}
            id={algorithmNotesCounterId}
            max={limits.algorithmNotesMaxChars}
          />
        </label>
      )}

      <ComplexityField
        disabled={disabled}
        emptyLabel={resources.problemNotes.complexityEmpty}
        label={resources.problemNotes.timeComplexity}
        labels={resources.problemNotes.complexityLabels}
        maxChars={limits.customComplexityMaxChars}
        onChange={(timeComplexity) => update({ timeComplexity })}
        value={value.timeComplexity}
      />
      <ComplexityField
        disabled={disabled}
        emptyLabel={resources.problemNotes.complexityEmpty}
        label={resources.problemNotes.spaceComplexity}
        labels={resources.problemNotes.complexityLabels}
        maxChars={limits.customComplexityMaxChars}
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
  maxCount,
  maxItemChars,
  onChange,
  value,
}: {
  delimiter: string;
  disabled: boolean;
  label: string;
  maxCount: number;
  maxItemChars: number;
  onChange: (value: string[]) => void;
  value: string[];
}) {
  const { resources } = useI18n();
  const counterId = useId();
  const overLimit = value.length > maxCount
    || value.some((item) => unicodeCodePointLength(item) > maxItemChars);

  return (
    <label className="problem-note-field">
      <span>{label}</span>
      <input
        aria-describedby={counterId}
        aria-invalid={overLimit}
        aria-label={label}
        disabled={disabled}
        onChange={(event) => onChange(event.target.value
          .split(/[,，]/)
          .map((item) => item.trim())
          .filter(Boolean))}
        value={value.join(delimiter)}
      />
      <small
        className={overLimit ? 'input-limit-counter is-over-limit' : 'input-limit-counter'}
        id={counterId}
      >
        {resources.common.itemInputLimit(value.length, maxCount, maxItemChars)}
      </small>
    </label>
  );
}

function ComplexityField({
  disabled,
  emptyLabel,
  label,
  labels,
  maxChars,
  onChange,
  value,
}: {
  disabled: boolean;
  emptyLabel: string;
  label: string;
  labels: Record<ProblemComplexityKey, string>;
  maxChars: number;
  onChange: (value: ProblemComplexityValue) => void;
  value: ProblemComplexityValue;
}) {
  const { resources } = useI18n();
  const counterId = useId();

  return (
    <label className="problem-note-field">
      <span>{label}</span>
      <select
        aria-label={label}
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
        <>
          <input
            aria-describedby={counterId}
            aria-invalid={unicodeCodePointLength(value.customText ?? '') > maxChars}
            aria-label={resources.problemNotes.customValueAriaLabel(label)}
            disabled={disabled}
            onChange={(event) => onChange({ key: 'OTHER', customText: event.target.value })}
            placeholder={resources.problemNotes.customValuePlaceholder}
            value={value.customText ?? ''}
          />
          <InputCounter
            current={unicodeCodePointLength(value.customText ?? '')}
            id={counterId}
            max={maxChars}
          />
        </>
      )}
    </label>
  );
}

function InputCounter({ current, id, max }: { current: number; id: string; max: number }) {
  const { resources } = useI18n();
  return (
    <small
      className={current > max ? 'input-limit-counter is-over-limit' : 'input-limit-counter'}
      id={id}
    >
      {resources.common.characterCount(current, max)}
    </small>
  );
}
