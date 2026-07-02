import { Archive, BookOpenCheck, RefreshCw, Search } from 'lucide-react';
import { useEffect, useMemo, useState } from 'react';
import { APP_ROUTES } from '../app/navigation';
import {
  archiveMistake,
  listMistakeNotes,
  markMistake,
  requireApiData,
} from '../services/api';
import type { MasteryState, MistakeNote } from '../types/api';

interface MistakeNotebookPageProps {
  onNavigate: (path: string) => void;
}

const stateLabels: Record<MasteryState, string> = {
  NEW: '新入库',
  LEARNING: '复习中',
  MASTERED: '已掌握',
  LAPSED: '又忘了',
};

export default function MistakeNotebookPage({ onNavigate }: MistakeNotebookPageProps) {
  const [items, setItems] = useState<MistakeNote[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [keyword, setKeyword] = useState('');
  const [state, setState] = useState<MasteryState | ''>('');
  const [manualSlug, setManualSlug] = useState('');
  const [actionError, setActionError] = useState('');

  const stats = useMemo(() => {
    const active = items.filter((item) => !item.archived);
    const due = active.filter((item) => new Date(item.dueAt).getTime() <= Date.now());
    const mastered = active.filter((item) => item.masteryState === 'MASTERED');
    return { active: active.length, due: due.length, mastered: mastered.length };
  }, [items]);

  useEffect(() => {
    const controller = new AbortController();
    void load(controller.signal);
    return () => controller.abort();
  }, [keyword, state]);

  async function load(signal?: AbortSignal) {
    setLoading(true);
    setError('');
    try {
      const response = await listMistakeNotes({ keyword, state, limit: 80 }, signal);
      setItems(requireApiData(response, '错题列表加载失败'));
    } catch (loadError) {
      if (!(loadError instanceof DOMException && loadError.name === 'AbortError')) {
        setError(loadError instanceof Error ? loadError.message : '错题列表加载失败');
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
      const note = requireApiData(response, '加入错题本失败');
      setItems((current) => [note, ...current.filter((item) => item.id !== note.id)]);
      setManualSlug('');
    } catch (markError) {
      setActionError(markError instanceof Error ? markError.message : '加入错题本失败');
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
          <p className="eyebrow">Mistake Notebook</p>
          <h1 id="mistake-title">错题本</h1>
        </div>
        <button className="primary-button" onClick={() => onNavigate(APP_ROUTES.reviewSession)} type="button">
          <BookOpenCheck aria-hidden="true" />
          <span>开始复习</span>
        </button>
      </header>

      <section className="mistake-stat-grid" aria-label="错题概览">
        <div>
          <span>待复习</span>
          <strong>{stats.due}</strong>
        </div>
        <div>
          <span>有效错题</span>
          <strong>{stats.active}</strong>
        </div>
        <div>
          <span>已掌握</span>
          <strong>{stats.mastered}</strong>
        </div>
      </section>

      <section className="mistake-toolbar" aria-label="错题筛选">
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
        <button className="icon-button" onClick={() => void load()} title="刷新" type="button">
          <RefreshCw aria-hidden="true" />
        </button>
      </section>

      <section className="mistake-manual-mark" aria-label="手动加入错题本">
        <input
          onChange={(event) => setManualSlug(event.target.value)}
          placeholder="problem-slug"
          value={manualSlug}
        />
        <button className="secondary-button" onClick={() => void handleManualMark()} type="button">
          加入错题本
        </button>
      </section>

      {(error || actionError) && <p className="error-text" role="alert">{error || actionError}</p>}

      <div className="mistake-list" aria-busy={loading}>
        {loading ? (
          <div className="loading-panel">正在加载错题...</div>
        ) : items.length === 0 ? (
          <div className="loading-panel">暂无错题记录。</div>
        ) : items.map((note) => (
          <article className="mistake-note-card" key={note.id}>
            <div>
              <h2>{note.problemSlug}</h2>
              <p>{stateLabels[note.masteryState]} · 间隔 {note.intervalDays} 天 · lapses {note.lapses}</p>
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
