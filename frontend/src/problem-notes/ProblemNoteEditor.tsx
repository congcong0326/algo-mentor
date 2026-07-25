import { ChevronDown, Loader2, RefreshCw, Save } from 'lucide-react';
import { useEffect, useMemo, useState } from 'react';
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
  const [open, setOpen] = useState(false);
  const [freeNoteOpen, setFreeNoteOpen] = useState(false);
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
    return note.noteMarkdown !== draft.noteMarkdown
      || JSON.stringify(note.outline) !== JSON.stringify(draft.outline);
  }, [draft, note]);

  useEffect(() => {
    onDirtyChange?.(dirty);
  }, [dirty, onDirtyChange]);

  useEffect(() => {
    const controller = new AbortController();
    setOpen(false);
    setFreeNoteOpen(false);
    onDirtyChange?.(false);
    void load(controller.signal);
    return () => controller.abort();
  }, [onDirtyChange, problemSlug]);

  async function load(signal?: AbortSignal) {
    setLoading(true);
    setError('');
    setConflict(false);
    try {
      const response = await getProblemNote(problemSlug, signal);
      const loaded = requireApiData(response, '题目笔记加载失败');
      setNote(loaded);
      setDraft({ noteMarkdown: loaded.noteMarkdown, outline: loaded.outline });
    } catch (loadError) {
      if (!(loadError instanceof DOMException && loadError.name === 'AbortError')) {
        setError(loadError instanceof Error ? loadError.message : '题目笔记加载失败');
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
      const saved = requireApiData(response, '题目笔记保存失败');
      setNote(saved);
      setDraft({ noteMarkdown: saved.noteMarkdown, outline: saved.outline });
    } catch (saveError) {
      if (saveError instanceof ApiRequestError && saveError.code === 'PROBLEM_NOTE_REVISION_CONFLICT') {
        setConflict(true);
      }
      setError(saveError instanceof Error ? saveError.message : '题目笔记保存失败');
    } finally {
      setSaving(false);
    }
  }

  const stateLabel = loading
    ? '加载中'
    : dirty
      ? '有未保存修改'
      : note?.hasContent
        ? '已有笔记'
        : '暂无笔记';

  return (
    <section className={`problem-note-editor ${open ? 'is-open' : ''} ${className}`.trim()}>
      <button
        aria-expanded={open}
        className="problem-note-disclosure"
        onClick={() => setOpen((current) => !current)}
        type="button"
      >
        <span>
          <strong>我的题目笔记</strong>
          <small>
            {stateLabel}
            {note?.updatedAt ? ` · 更新于 ${formatUpdatedAt(note.updatedAt)}` : ''}
          </small>
        </span>
        <ChevronDown aria-hidden="true" />
      </button>

      {open && (
        <div className="problem-note-editor-body">
          {loading ? (
            <p className="problem-note-state" role="status"><Loader2 aria-hidden="true" />正在加载题目笔记...</p>
          ) : error && !note ? (
            <div className="problem-note-state error" role="alert">
              <span>{error}</span>
              <button className="secondary-button compact" onClick={() => void load()} type="button">
                <RefreshCw aria-hidden="true" />
                <span>重试</span>
              </button>
            </div>
          ) : note ? (
            <>
              <ProblemSolutionOutlineForm
                disabled={saving}
                onChange={(outline) => setDraft((current) => ({ ...current, outline }))}
                value={draft.outline}
              />
              <div className={`problem-note-free-note ${freeNoteOpen ? 'is-open' : ''}`}>
                <button
                  aria-expanded={freeNoteOpen}
                  className="problem-note-secondary-disclosure"
                  onClick={() => setFreeNoteOpen((current) => !current)}
                  type="button"
                >
                  <span>
                    <strong>自由笔记</strong>
                    <small>{draft.noteMarkdown.trim() ? '已有内容' : '未填写'}</small>
                  </span>
                  <ChevronDown aria-hidden="true" />
                </button>
                {freeNoteOpen && (
                  <label className="problem-note-field problem-note-field-wide">
                    <span>自由笔记内容</span>
                    <textarea
                      aria-label="自由笔记内容"
                      disabled={saving}
                      maxLength={10000}
                      onChange={(event) => setDraft((current) => ({
                        ...current,
                        noteMarkdown: event.target.value,
                      }))}
                      rows={8}
                      value={draft.noteMarkdown}
                    />
                    <small>{draft.noteMarkdown.length} / 10000</small>
                  </label>
                )}
              </div>
              {(error || conflict) && (
                <p className="problem-note-save-error" role="alert">
                  {conflict ? '笔记已在其他页面更新，请重新加载后再编辑。' : error}
                </p>
              )}
              <div className="problem-note-actions">
                {conflict && (
                  <button className="secondary-button compact" onClick={() => void load()} type="button">
                    <RefreshCw aria-hidden="true" />
                    <span>重新加载</span>
                  </button>
                )}
                <button className="primary-button compact" disabled={!dirty || saving} onClick={() => void save()} type="button">
                  {saving ? <Loader2 aria-hidden="true" /> : <Save aria-hidden="true" />}
                  <span>{saving ? '保存中' : '保存笔记'}</span>
                </button>
              </div>
            </>
          ) : null}
        </div>
      )}
    </section>
  );
}

function formatUpdatedAt(value: string) {
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString('zh-CN', { hour12: false });
}
