import { ChevronLeft, ChevronRight, ExternalLink, Search } from 'lucide-react';
import { useEffect, useMemo, useState } from 'react';
import { getProblemDetail, getProblems } from './services/api';
import type { ProblemDetail, ProblemDifficulty, ProblemListItem, ProblemListQuery, ProblemPage } from './types/api';

const difficultyOptions: Array<{ label: string; value: ProblemDifficulty | '' }> = [
  { label: '全部难度', value: '' },
  { label: 'Easy', value: 'EASY' },
  { label: 'Medium', value: 'MEDIUM' },
  { label: 'Hard', value: 'HARD' },
];

function difficultyLabel(difficulty?: ProblemDifficulty): string {
  return difficulty ? difficulty[0] + difficulty.slice(1).toLowerCase() : '-';
}

export default function ProblemLibrary() {
  const [keyword, setKeyword] = useState('');
  const [difficulty, setDifficulty] = useState<ProblemDifficulty | ''>('');
  const [page, setPage] = useState(1);
  const [problemPage, setProblemPage] = useState<ProblemPage<ProblemListItem>>();
  const [selectedSlug, setSelectedSlug] = useState<string>();
  const [detail, setDetail] = useState<ProblemDetail>();
  const [listError, setListError] = useState('');
  const [detailError, setDetailError] = useState('');
  const [listLoading, setListLoading] = useState(false);
  const [detailLoading, setDetailLoading] = useState(false);

  const query: ProblemListQuery = useMemo(() => ({
    keyword: keyword.trim() || undefined,
    difficulty,
    sort: 'frontend_id_asc',
    page,
    pageSize: 20,
  }), [difficulty, keyword, page]);

  useEffect(() => {
    const controller = new AbortController();
    setListLoading(true);
    setListError('');

    getProblems(query, controller.signal)
      .then((response) => {
        if (!response.success || !response.data) {
          throw new Error(response.error?.message ?? '题库列表加载失败');
        }
        const nextProblemPage = response.data;
        setProblemPage(nextProblemPage);
        setSelectedSlug((current) => current ?? nextProblemPage.items[0]?.slug);
      })
      .catch((error) => {
        if (!controller.signal.aborted) {
          setListError(error instanceof Error ? error.message : '题库列表加载失败');
        }
      })
      .finally(() => {
        if (!controller.signal.aborted) {
          setListLoading(false);
        }
      });

    return () => controller.abort();
  }, [query]);

  useEffect(() => {
    if (!selectedSlug) {
      setDetail(undefined);
      return;
    }

    const controller = new AbortController();
    setDetailLoading(true);
    setDetailError('');

    getProblemDetail(selectedSlug, controller.signal)
      .then((response) => {
        if (!response.success || !response.data) {
          throw new Error(response.error?.message ?? '题目详情加载失败');
        }
        setDetail(response.data);
      })
      .catch((error) => {
        if (!controller.signal.aborted) {
          setDetailError(error instanceof Error ? error.message : '题目详情加载失败');
        }
      })
      .finally(() => {
        if (!controller.signal.aborted) {
          setDetailLoading(false);
        }
      });

    return () => controller.abort();
  }, [selectedSlug]);

  const totalPages = Math.max(1, Math.ceil((problemPage?.total ?? 0) / (problemPage?.pageSize ?? 20)));

  function handleKeywordChange(nextKeyword: string) {
    setKeyword(nextKeyword);
    setPage(1);
    setSelectedSlug(undefined);
  }

  function handleDifficultyChange(nextDifficulty: ProblemDifficulty | '') {
    setDifficulty(nextDifficulty);
    setPage(1);
    setSelectedSlug(undefined);
  }

  return (
    <section className="problem-shell" aria-label="题库">
      <div className="problem-toolbar">
        <label className="search-field">
          <Search aria-hidden="true" />
          <input
            aria-label="搜索题目"
            onChange={(event) => handleKeywordChange(event.target.value)}
            placeholder="搜索标题、slug 或编号"
            value={keyword}
          />
        </label>
        <label className="filter-field">
          <span>难度</span>
          <select
            aria-label="难度筛选"
            onChange={(event) => handleDifficultyChange(event.target.value as ProblemDifficulty | '')}
            value={difficulty}
          >
            {difficultyOptions.map((option) => (
              <option key={option.value || 'all'} value={option.value}>{option.label}</option>
            ))}
          </select>
        </label>
      </div>

      <div className="problem-layout">
        <article className="problem-list-panel" aria-labelledby="problem-list-title">
          <div className="panel-title compact-title">
            <h2 id="problem-list-title">题目列表</h2>
            <span>{problemPage?.total ?? 0} 题</span>
          </div>
          {listError && <p className="error-text">{listError}</p>}
          {listLoading && <p className="empty-log">加载题库...</p>}
          {!listLoading && !listError && (
            <div className="problem-list">
              {(problemPage?.items ?? []).length === 0 ? (
                <p className="empty-log">没有匹配的题目</p>
              ) : (
                problemPage?.items.map((problem) => (
                  <button
                    className={`problem-row ${selectedSlug === problem.slug ? 'selected' : ''}`}
                    key={problem.slug}
                    onClick={() => setSelectedSlug(problem.slug)}
                    type="button"
                  >
                    <span className="problem-id">{problem.frontendId ?? '-'}</span>
                    <span className="problem-title">
                      <strong>{problem.titleCn || problem.title}</strong>
                      <small>{problem.slug}</small>
                    </span>
                    <span className={`difficulty-badge ${problem.difficulty?.toLowerCase() ?? 'unknown'}`}>
                      {difficultyLabel(problem.difficulty)}
                    </span>
                  </button>
                ))
              )}
            </div>
          )}
          <div className="pagination-row">
            <button
              aria-label="上一页"
              className="icon-button"
              disabled={page <= 1}
              onClick={() => setPage((current) => Math.max(1, current - 1))}
              type="button"
            >
              <ChevronLeft aria-hidden="true" />
            </button>
            <span>{page} / {totalPages}</span>
            <button
              aria-label="下一页"
              className="icon-button"
              disabled={page >= totalPages}
              onClick={() => setPage((current) => current + 1)}
              type="button"
            >
              <ChevronRight aria-hidden="true" />
            </button>
          </div>
        </article>

        <article className="problem-detail-panel" aria-labelledby="problem-detail-title">
          {detailLoading && <p className="empty-log">加载详情...</p>}
          {detailError && <p className="error-text">{detailError}</p>}
          {!detailLoading && !detailError && detail && (
            <>
              <div className="detail-heading">
                <div>
                  <p className="eyebrow">#{detail.frontendId ?? '-'}</p>
                  <h2 id="problem-detail-title">{detail.titleCn || detail.title}</h2>
                  <p>{detail.title}</p>
                </div>
                {detail.leetcodeUrl && (
                  <a className="external-link" href={detail.leetcodeUrl} rel="noreferrer" target="_blank">
                    <ExternalLink aria-hidden="true" />
                    <span>LeetCode</span>
                  </a>
                )}
              </div>
              <div className="tag-row">
                <span className={`difficulty-badge ${detail.difficulty?.toLowerCase() ?? 'unknown'}`}>
                  {difficultyLabel(detail.difficulty)}
                </span>
                {detail.tags.map((tag) => <span className="tag-pill" key={tag}>{tag}</span>)}
              </div>
              <pre className="markdown-view">{detail.contentMarkdown}</pre>
              {detail.sampleTestCase && (
                <section className="code-section">
                  <h3>样例输入</h3>
                  <pre>{detail.sampleTestCase}</pre>
                </section>
              )}
              {detail.python3Template && (
                <section className="code-section">
                  <h3>Python3 模板</h3>
                  <pre>{detail.python3Template}</pre>
                </section>
              )}
            </>
          )}
          {!detailLoading && !detailError && !detail && (
            <p className="empty-log">选择一道题查看详情</p>
          )}
        </article>
      </div>
    </section>
  );
}
