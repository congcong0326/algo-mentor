import { ChevronDown, Loader2, RefreshCw, Save, Sparkles } from 'lucide-react';
import { useEffect, useMemo, useState } from 'react';
import HeaderActionTooltip from '../app/HeaderActionTooltip';
import MarkdownView from '../components/MarkdownView';
import { useI18n } from '../i18n/I18nProvider';
import { ApiRequestError, getProblemNote, requireApiData, upsertProblemNote } from '../services/api';
import type { ProblemSolutionOutlineV1, UserProblemNote } from '../types/api';
import { emptyProblemSolutionOutline } from './problemNoteOptions';
import ProblemSolutionOutlineForm from './ProblemSolutionOutlineForm';

interface ProblemNoteEditorProps {
  className?: string;
  onDirtyChange?: (dirty: boolean) => void;
  problemSlug: string;
}

interface NoteDraft {
  noteMarkdown: string;
  outline: ProblemSolutionOutlineV1;
}

export default function ProblemNoteEditor({
  className = '',
  onDirtyChange,
  problemSlug,
}: ProblemNoteEditorProps) {
  const { locale, resources } = useI18n();
  const [open, setOpen] = useState(false);
  const [coachSummaryOpen, setCoachSummaryOpen] = useState(false);
  const [note, setNote] = useState<UserProblemNote>();
  const [draft, setDraft] = useState<NoteDraft>({
    noteMarkdown: '',
    outline: emptyProblemSolutionOutline(),
  });
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const [conflict, setConflict] = useState(false);

  const dirty = useMemo(() => {
    if (!note) {
      return false;
    }
    return JSON.stringify(note.outline) !== JSON.stringify(draft.outline);
  }, [draft, note]);

  useEffect(() => {
    onDirtyChange?.(dirty);
  }, [dirty, onDirtyChange]);

  useEffect(() => {
    const controller = new AbortController();
    setOpen(false);
    setCoachSummaryOpen(false);
    onDirtyChange?.(false);
    void load(controller.signal);
    return () => controller.abort();
  }, [locale, onDirtyChange, problemSlug]);

  async function load(signal?: AbortSignal) {
    setLoading(true);
    setError('');
    setConflict(false);
    try {
      const response = await getProblemNote(problemSlug, signal);
      const loaded = requireApiData(response, resources.problemNotes.loadFailed);
      setNote(loaded);
      setDraft({ noteMarkdown: loaded.noteMarkdown, outline: loaded.outline });
    } catch (loadError) {
      if (!(loadError instanceof DOMException && loadError.name === 'AbortError')) {
        setError(loadError instanceof Error ? loadError.message : resources.problemNotes.loadFailed);
      }
    } finally {
      if (!signal?.aborted) {
        setLoading(false);
      }
    }
  }

  async function save() {
    if (!note || saving || !dirty) {
      return;
    }
    setSaving(true);
    setError('');
    setConflict(false);
    try {
      const response = await upsertProblemNote(problemSlug, {
        expectedRevision: note.revision,
        noteMarkdown: draft.noteMarkdown,
        outline: draft.outline,
      });
      const saved = requireApiData(response, resources.problemNotes.saveFailed);
      setNote(saved);
      setDraft({ noteMarkdown: saved.noteMarkdown, outline: saved.outline });
    } catch (saveError) {
      if (saveError instanceof ApiRequestError && saveError.code === 'PROBLEM_NOTE_REVISION_CONFLICT') {
        setConflict(true);
      }
      setError(saveError instanceof Error ? saveError.message : resources.problemNotes.saveFailed);
    } finally {
      setSaving(false);
    }
  }

  const outlineHasContent = note ? hasVisibleOutlineContent(note.outline) : false;
  const noteStateLabel = loading
    ? resources.problemNotes.loading
    : error && !note
      ? resources.problemNotes.loadFailed
      : dirty
        ? resources.problemNotes.unsaved
        : outlineHasContent
          ? resources.problemNotes.existing
          : resources.problemNotes.empty;
  const coachSummaryStateLabel = loading
    ? resources.problemNotes.loading
    : error && !note
      ? resources.problemNotes.loadFailed
      : draft.noteMarkdown.trim()
        ? resources.problemNotes.coachSummaryPresent
        : resources.problemNotes.coachSummaryNotGenerated;
  const coachSummaryTooltipId = `problem-note-coach-summary-tooltip-${problemSlug}`;

  return (
    <div className={`problem-note-sections ${className}`.trim()}>
      <section className={`problem-note-editor ${open ? 'is-open' : ''}`}>
        <button
          aria-expanded={open}
          className="problem-note-disclosure"
          onClick={() => setOpen((current) => !current)}
          type="button"
        >
          <span>
            <strong>{resources.problemNotes.title}</strong>
            <small>
              {noteStateLabel}
              {outlineHasContent && note?.updatedAt
                ? resources.problemNotes.updatedAt(formatUpdatedAt(note.updatedAt, locale))
                : ''}
            </small>
          </span>
          <ChevronDown aria-hidden="true" />
        </button>

        {open && (
          <div className="problem-note-editor-body">
            {loading ? (
              <p className="problem-note-state" role="status">
                <Loader2 aria-hidden="true" />{resources.problemNotes.loadingDetail}
              </p>
            ) : error && !note ? (
              <div className="problem-note-state error" role="alert">
                <span>{error}</span>
                <button className="secondary-button compact" onClick={() => void load()} type="button">
                  <RefreshCw aria-hidden="true" />
                  <span>{resources.problemNotes.retry}</span>
                </button>
              </div>
            ) : note ? (
              <>
                <ProblemSolutionOutlineForm
                  disabled={saving}
                  onChange={(outline) => setDraft((current) => ({ ...current, outline }))}
                  value={draft.outline}
                />
                {(error || conflict) && (
                  <p className="problem-note-save-error" role="alert">
                    {conflict ? resources.problemNotes.conflict : error}
                  </p>
                )}
                <div className="problem-note-actions">
                  {conflict && (
                    <button className="secondary-button compact" onClick={() => void load()} type="button">
                      <RefreshCw aria-hidden="true" />
                      <span>{resources.problemNotes.reload}</span>
                    </button>
                  )}
                  <button className="primary-button compact" disabled={!dirty || saving} onClick={() => void save()} type="button">
                    {saving ? <Loader2 aria-hidden="true" /> : <Save aria-hidden="true" />}
                    <span>{saving ? resources.problemNotes.saving : resources.problemNotes.save}</span>
                  </button>
                </div>
              </>
            ) : null}
          </div>
        )}
      </section>

      <section className={`problem-note-coach-summary ${coachSummaryOpen ? 'is-open' : ''}`}>
        <div className="problem-note-coach-summary-header">
          <button
            aria-expanded={coachSummaryOpen}
            className="problem-note-disclosure"
            onClick={() => setCoachSummaryOpen((current) => !current)}
            type="button"
          >
            <span>
              <strong>{resources.problemNotes.coachSummary}</strong>
              <small>{coachSummaryStateLabel}</small>
            </span>
            <ChevronDown aria-hidden="true" />
          </button>
          <HeaderActionTooltip
            id={coachSummaryTooltipId}
            label={resources.problemNotes.coachSummaryHint}
          >
            <span
              aria-describedby={coachSummaryTooltipId}
              aria-label={resources.problemNotes.coachSummaryHint}
              className="problem-note-coach-summary-info"
              role="img"
              tabIndex={0}
            >
              <Sparkles aria-hidden="true" />
            </span>
          </HeaderActionTooltip>
        </div>
        {coachSummaryOpen && (
          <div className="problem-note-coach-summary-body">
            {loading ? (
              <p className="problem-note-state" role="status">
                <Loader2 aria-hidden="true" />{resources.problemNotes.loadingDetail}
              </p>
            ) : error && !note ? (
              <div className="problem-note-state error" role="alert">
                <span>{error}</span>
                <button className="secondary-button compact" onClick={() => void load()} type="button">
                  <RefreshCw aria-hidden="true" />
                  <span>{resources.problemNotes.retry}</span>
                </button>
              </div>
            ) : draft.noteMarkdown.trim() ? (
              <div className="problem-note-coach-summary-content review-problem-content">
                <MarkdownView content={draft.noteMarkdown} />
              </div>
            ) : (
              <p className="problem-note-coach-summary-empty">
                {resources.problemNotes.coachSummaryEmpty}
              </p>
            )}
          </div>
        )}
      </section>
    </div>
  );
}

function hasVisibleOutlineContent(outline: ProblemSolutionOutlineV1): boolean {
  return Boolean(
    outline.coreIdea.trim()
    || outline.dataStructures.length
    || outline.customDataStructures.length
    || outline.dataStructureNotes.trim()
    || outline.algorithms.length
    || outline.customAlgorithms.length
    || outline.algorithmNotes.trim()
    || outline.timeComplexity.key
    || outline.timeComplexity.customText?.trim()
    || outline.spaceComplexity.key
    || outline.spaceComplexity.customText?.trim(),
  );
}

function formatUpdatedAt(value: string, locale: string) {
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString(locale, { hour12: false });
}
