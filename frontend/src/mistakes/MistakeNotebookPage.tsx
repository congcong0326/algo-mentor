import { Archive, BookOpenCheck, Eye, RefreshCw, Search, X } from 'lucide-react';
import { useEffect, useMemo, useRef, useState } from 'react';
import type { KeyboardEvent } from 'react';
import { APP_ROUTES } from '../app/navigation';
import MarkdownView from '../components/MarkdownView';
import {
  archiveMistake,
  getReviewCard,
  getReviewProblemStatement,
  listMistakeNotes,
  requireApiData,
} from '../services/api';
import type { MasteryState, MistakeNote, MistakeSource, ReviewCard } from '../types/api';

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
  const [actionError, setActionError] = useState('');
  const [detailNote, setDetailNote] = useState<MistakeNote>();
  const [selectedCard, setSelectedCard] = useState<ReviewCard>();
  const [cardLoading, setCardLoading] = useState(false);
  const [cardError, setCardError] = useState('');
  const [statementCache, setStatementCache] = useState<Map<number, string>>(new Map());
  const [statementLoading, setStatementLoading] = useState(false);
  const [statementError, setStatementError] = useState('');
  const detailRequestId = useRef(0);
  const detailTriggerButtonRef = useRef<HTMLButtonElement | null>(null);
  const detailCloseButtonRef = useRef<HTMLButtonElement>(null);

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
      const notes = requireApiData(response, '复习队列加载失败');
      setItems(notes);
      if (detailNote && !notes.some((item) => item.id === detailNote.id)) {
        setDetailNote(undefined);
        setSelectedCard(undefined);
        setCardError('');
      }
    } catch (loadError) {
      if (!(loadError instanceof DOMException && loadError.name === 'AbortError')) {
        setError(loadError instanceof Error ? loadError.message : '复习队列加载失败');
      }
    } finally {
      setLoading(false);
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

  useEffect(() => {
    if (detailNote) {
      detailCloseButtonRef.current?.focus();
    }
  }, [detailNote]);

  async function handleOpenDetail(note: MistakeNote, triggerButton: HTMLButtonElement) {
    const requestId = detailRequestId.current + 1;
    detailRequestId.current = requestId;
    detailTriggerButtonRef.current = triggerButton;
    setDetailNote(note);
    setSelectedCard(undefined);
    setCardError('');
    setStatementError('');
    setStatementLoading(false);
    setCardLoading(true);
    try {
      const response = await getReviewCard(note.id);
      if (detailRequestId.current === requestId) {
        setSelectedCard(requireApiData(response, '复习卡加载失败'));
      }
    } catch (loadError) {
      if (detailRequestId.current === requestId) {
        setCardError(loadError instanceof Error ? loadError.message : '复习卡加载失败');
      }
    } finally {
      if (detailRequestId.current === requestId) {
        setCardLoading(false);
      }
    }
  }

  function closeDetail() {
    detailRequestId.current += 1;
    setDetailNote(undefined);
    setSelectedCard(undefined);
    setCardError('');
    setStatementError('');
    setStatementLoading(false);
    setCardLoading(false);
    if (detailTriggerButtonRef.current?.isConnected) {
      detailTriggerButtonRef.current.focus();
    }
    detailTriggerButtonRef.current = null;
  }

  function handleDetailKeyDown(event: KeyboardEvent<HTMLElement>) {
    if (event.key !== 'Escape') {
      return;
    }
    event.preventDefault();
    closeDetail();
  }

  function noteTitle(note: MistakeNote) {
    return note.problemTitle || note.problemSlug;
  }

  async function loadProblemStatement(noteId: number) {
    if (statementCache.has(noteId) || statementLoading) {
      return;
    }
    setStatementLoading(true);
    setStatementError('');
    try {
      const response = await getReviewProblemStatement(noteId);
      const data = requireApiData(response, '题目原文加载失败');
      setStatementCache((currentCache) => {
        const nextCache = new Map(currentCache);
        nextCache.set(noteId, data.contentMarkdown);
        return nextCache;
      });
    } catch (loadError) {
      setStatementError(loadError instanceof Error ? loadError.message : '未找到题目原文');
    } finally {
      setStatementLoading(false);
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

      {(error || actionError) && <p className="error-text" role="alert">{error || actionError}</p>}

      <div className="mistake-content-grid">
        <div className="mistake-list" aria-busy={loading}>
          {loading ? (
            <div className="loading-panel">正在加载复习队列...</div>
          ) : items.length === 0 ? (
            <div className="loading-panel">暂无复习记录。</div>
          ) : items.map((note) => (
            <article className="mistake-note-card" key={note.id}>
              <div
                className="mistake-note-main"
              >
                <h2>{noteTitle(note)}</h2>
                <p>
                  {note.problemTitle && note.problemTitle !== note.problemSlug ? `${note.problemSlug} · ` : ''}
                  {sourceLabels[note.source]} · {stateLabels[note.masteryState]} · 间隔 {note.intervalDays} 天 · lapses {note.lapses}
                  {note.problemLocale ? ` · ${note.problemLocale}` : ''}
                </p>
                {note.userNotePersistent && <p className="mistake-note-text">{note.userNotePersistent}</p>}
              </div>
              <div className="mistake-note-actions">
                <span className={`mistake-state ${note.masteryState.toLowerCase()}`}>{stateLabels[note.masteryState]}</span>
                <button
                  aria-label={`查看复习卡详情 ${noteTitle(note)}`}
                  className="icon-button"
                  onClick={(event) => void handleOpenDetail(note, event.currentTarget)}
                  title="查看详情"
                  type="button"
                >
                  <Eye aria-hidden="true" />
                </button>
                <button
                  className="secondary-button compact"
                  onClick={() => void handleArchive(note)}
                  title={note.archived ? '恢复复习' : '移出复习'}
                  type="button"
                >
                  <Archive aria-hidden="true" />
                  <span>{note.archived ? '恢复复习' : '移出复习'}</span>
                </button>
              </div>
            </article>
          ))}
        </div>
      </div>

      {detailNote && (
        <div className="modal-backdrop">
          <section
            aria-labelledby="mistake-card-detail-title"
            aria-modal="true"
            className="mistake-detail-modal"
            onKeyDown={handleDetailKeyDown}
            role="dialog"
            tabIndex={-1}
          >
            <div className="modal-heading">
              <div>
                <p className="eyebrow">Review Card</p>
                <h2 id="mistake-card-detail-title">{noteTitle(detailNote)}</h2>
                <p className="mistake-detail-meta">
                  {detailNote.problemSlug}
                  {detailNote.problemDifficulty ? ` · ${detailNote.problemDifficulty}` : ''}
                  {detailNote.problemLocale ? ` · ${detailNote.problemLocale}` : ''}
                  {' · '}
                  {sourceLabels[detailNote.source]} · {stateLabels[detailNote.masteryState]}
                </p>
              </div>
              <button
                aria-label="关闭复习卡详情"
                className="icon-button"
                onClick={closeDetail}
                ref={detailCloseButtonRef}
                type="button"
              >
                <X aria-hidden="true" />
              </button>
            </div>

            {cardLoading ? (
              <div className="loading-panel">正在加载复习卡...</div>
            ) : cardError ? (
              <p className="error-text" role="alert">{cardError}</p>
            ) : selectedCard ? (
              <div className="mistake-detail-content">

                <section className="mistake-detail-section">
                  <h3>题面</h3>
                  <details
                    className="review-problem-full"
                    onToggle={(event) => {
                      if (event.currentTarget.open) {
                        void loadProblemStatement(detailNote.id);
                      }
                    }}
                  >
                    <summary>
                      <Eye aria-hidden="true" />
                      <span>查看题面</span>
                    </summary>
                    {statementLoading && !statementCache.has(detailNote.id) ? (
                      <p>正在加载题目原文...</p>
                    ) : statementError ? (
                      <p>{statementError}</p>
                    ) : statementCache.has(detailNote.id) ? (
                      <MarkdownView content={statementCache.get(detailNote.id) ?? ''} />
                    ) : null}
                  </details>
                </section>

                <section className="mistake-detail-section">
                  <h3>复习上下文</h3>
                  <p>{selectedCard.contextSummary || '暂无上下文。'}</p>
                </section>

                <section className="mistake-detail-section">
                  <h3>复述提示</h3>
                  {selectedCard.prompts.length > 0 ? (
                    <ol className="review-prompts">
                      {selectedCard.prompts.map((prompt) => (
                        <li key={prompt.key}>
                          <strong>{prompt.label}</strong>
                          {prompt.hint && <span>{prompt.hint}</span>}
                        </li>
                      ))}
                    </ol>
                  ) : (
                    <p>暂无提示。</p>
                  )}
                </section>

                {selectedCard.scaffold && (
                  <section className="mistake-detail-section">
                    <h3>答题脚手架</h3>
                    <pre className="review-scaffold">{selectedCard.scaffold.templateMarkdown}</pre>
                  </section>
                )}

                <section className="mistake-detail-section">
                  <h3>长期备注</h3>
                  <p>{selectedCard.userNotePersistent || '暂无长期备注。'}</p>
                </section>

                <section className="mistake-detail-section">
                  <h3>最近 5 条历史回答</h3>
                  {selectedCard.recentRecallHistory.length > 0 ? (
                    <ol className="review-history-list">
                      {selectedCard.recentRecallHistory.map((history) => (
                        <li key={history.id}>
                          <div className="review-history-meta">
                            <strong>{gradeLabel(history.grade)}</strong>
                            <span>{history.reviewedAt} · 间隔 {history.intervalAfter} 天</span>
                          </div>
                          <p>{history.userRecallText || '本次未记录复述内容。'}</p>
                          {history.userNoteTransient && <small>本次备注：{history.userNoteTransient}</small>}
                        </li>
                      ))}
                    </ol>
                  ) : (
                    <p>暂无历史回答。</p>
                  )}
                </section>
              </div>
            ) : null}
          </section>
        </div>
      )}
    </section>
  );
}

function gradeLabel(grade: ReviewCard['recentRecallHistory'][number]['grade']) {
  const labels: Record<string, string> = {
    FORGOT: '忘了',
    BARELY: '勉强想起',
    MASTERED: '掌握',
    FLUENT: '熟练',
  };
  return labels[grade] ?? grade;
}
