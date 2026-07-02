import { Archive, BookOpenCheck, RefreshCw, Search } from 'lucide-react';
import { useEffect, useMemo, useState } from 'react';
import { APP_ROUTES } from '../app/navigation';
import {
  archiveMistake,
  listMistakeNotes,
  markMistake,
  requireApiData,
} from '../services/api';
import type { MasteryState, MistakeNote, MistakeSource } from '../types/api';

interface MistakeNotebookPageProps {
  onNavigate: (path: string) => void;
}

const stateLabels: Record<MasteryState, string> = {
  NEW: '新入库',
  LEARNING: '复习中',
  MASTERED: '已掌握',
  LAPSED: '又忘了',
};

const sourceLabels: Record<MistakeSource, string> = {
  REVIEW_FAILED: '错题',
  REVIEW_PASSED: '复习',
  USER_MARKED: '手动标记',
  AI_WEAK: '薄弱点',
};

export default function MistakeNotebookPage({ onNavigate }: MistakeNotebookPageProps) {
  const [items, setItems] = useState<MistakeNote[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [keyword, setKeyword] = useState('');
  const [state, setState] = useState<MasteryState | ''>('');
  const [mistakeOnly, setMistakeOnly] = useState(false);
  const [manualSlug, setManualSlug] = useState('');
  const [actionError, setActionError] = useState('');

  const stats = useMemo(() => {
    const active = items.filter((item) => !item.archived);
    const due = active.filter((item) => new Date(item.dueAt).getTime() <= Date.now());
    const mistakes = active.filter((item) => item.source === 'REVIEW_FAILED' || item.lapses > 0);
    return { active: active.length, due: due.length, mistakes: mistakes.length };
  }, [items]);

  useEffect(() => {
    const controller = new AbortController();
    void load(controller.signal);
    return () => controller.abort();
  }, [keyword, state, mistakeOnly]);

  async function load(signal?: AbortSignal) {
    setLoading(true);
    setError('');
    try {
      const response = await listMistakeNotes({ keyword, state, mistakeOnly, limit: 80 }, signal);
      setItems(requireApiData(response, '复习队列加载失败'));
    } catch (loadError) {
      if (!(loadError instanceof DOMException && loadError.name === 'AbortError')) {
        setError(loadError instanceof Error ? loadError.message : '复习队列加载失败');
      }
    } finally {
      setLoading(false);
    }
  }

  async function handleManualMark() {
    if (!manualSlug.trim()) {
      return;
    }
    setActionError('');
    try {
      const response = await markMistake(manualSlug.trim());
      const note = requireApiData(response, '加入复习队列失败');
      setItems((current) => [note, ...current.filter((item) => item.id !== note.id)]);
      setManualSlug('');
    } catch (markError) {
      setActionError(markError instanceof Error ? markError.message : '加入复习队列失败');
    }
  }

  async function handleArchive(note: MistakeNote) {
    setActionError('');
    try {
      const response = await archiveMistake(note.id, !note.archived);
      const updated = requireApiData(response, '更新归档状态失败');
      setItems((current) => current.map((item) => (item.id === updated.id ? updated : item)));
    } catch (archiveError) {
      setActionError(archiveError instanceof Error ? archiveError.message : '更新归档状态失败');
    }
  }

  return (
    <section className="mistake-page" aria-labelledby="mistake-title">
      <header className="mistake-header">
        <div>
          <p className="eyebrow">Review Center</p>
          <h1 id="mistake-title">复习中心</h1>
        </div>
        <button className="primary-button" onClick={() => onNavigate(APP_ROUTES.reviewSession)} type="button">
          <BookOpenCheck aria-hidden="true" />
          <span>开始复习</span>
        </button>
      </header>

      <section className="mistake-stat-grid" aria-label="复习概览">
        <div>
          <span>待复习</span>
          <strong>{stats.due}</strong>
        </div>
        <div>
          <span>复习题</span>
          <strong>{stats.active}</strong>
        </div>
        <div>
          <span>错题</span>
          <strong>{stats.mistakes}</strong>
        </div>
      </section>

      <section className="mistake-toolbar" aria-label="复习筛选">
        <label className="search-field">
          <Search aria-hidden="true" />
          <input
            onChange={(event) => setKeyword(event.target.value)}
            placeholder="搜索题目 slug 或备注"
            value={keyword}
          />
        </label>
        <select onChange={(event) => setState(event.target.value as MasteryState | '')} value={state}>
          <option value="">全部状态</option>
          {Object.entries(stateLabels).map(([value, label]) => (
            <option key={value} value={value}>{label}</option>
          ))}
        </select>
        <label className="checkbox-control">
          <input
            checked={mistakeOnly}
            onChange={(event) => setMistakeOnly(event.target.checked)}
            type="checkbox"
          />
          <span>仅看错题</span>
        </label>
        <button className="icon-button" onClick={() => void load()} title="刷新" type="button">
          <RefreshCw aria-hidden="true" />
        </button>
      </section>

      <section className="mistake-manual-mark" aria-label="手动加入复习队列">
        <input
          onChange={(event) => setManualSlug(event.target.value)}
          placeholder="problem-slug"
          value={manualSlug}
        />
        <button className="secondary-button" onClick={() => void handleManualMark()} type="button">
          加入复习队列
        </button>
      </section>

      {(error || actionError) && <p className="error-text" role="alert">{error || actionError}</p>}

      <div className="mistake-list" aria-busy={loading}>
        {loading ? (
          <div className="loading-panel">正在加载复习队列...</div>
        ) : items.length === 0 ? (
          <div className="loading-panel">暂无复习记录。</div>
        ) : items.map((note) => (
          <article className="mistake-note-card" key={note.id}>
            <div>
              <h2>{note.problemSlug}</h2>
              <p>{sourceLabels[note.source]} · {stateLabels[note.masteryState]} · 间隔 {note.intervalDays} 天 · lapses {note.lapses}</p>
              {note.userNotePersistent && <p className="mistake-note-text">{note.userNotePersistent}</p>}
            </div>
            <div className="mistake-note-actions">
              <span className={`mistake-state ${note.masteryState.toLowerCase()}`}>{stateLabels[note.masteryState]}</span>
              <button className="icon-button" onClick={() => void handleArchive(note)} title="归档" type="button">
                <Archive aria-hidden="true" />
              </button>
            </div>
          </article>
        ))}
      </div>
    </section>
  );
}
