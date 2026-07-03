import { ChevronLeft, ChevronRight, ExternalLink, Search } from 'lucide-react';
import { useEffect, useMemo, useState } from 'react';
import MarkdownView from './components/MarkdownView';
import { formatDifficulty } from './i18n/formatters';
import { useI18n } from './i18n/I18nProvider';
import { getProblemDetail, getProblemFilters, getProblems, requireApiData } from './services/api';
import type { ProblemDetail, ProblemDifficulty, ProblemFilters, ProblemListItem, ProblemListQuery, ProblemPage } from './types/api';

export default function ProblemLibrary() {
  const { locale, resources } = useI18n();
  const [keyword, setKeyword] = useState('');
  const [difficulty, setDifficulty] = useState<ProblemDifficulty | ''>('');
  const [company, setCompany] = useState('');
  const [role, setRole] = useState('');
  const [recencyBucket, setRecencyBucket] = useState('');
  const [sort, setSort] = useState<NonNullable<ProblemListQuery['sort']>>('frontend_id_asc');
  const [page, setPage] = useState(1);
  const [problemPage, setProblemPage] = useState<ProblemPage<ProblemListItem>>();
  const [filters, setFilters] = useState<ProblemFilters>();
  const [selectedSlug, setSelectedSlug] = useState<string>();
  const [detail, setDetail] = useState<ProblemDetail>();
  const [listError, setListError] = useState('');
  const [filtersError, setFiltersError] = useState('');
  const [detailError, setDetailError] = useState('');
  const [listLoading, setListLoading] = useState(false);
  const [detailLoading, setDetailLoading] = useState(false);

  const query: ProblemListQuery = useMemo(() => ({
    keyword: keyword.trim() || undefined,
    difficulty,
    company: company || undefined,
    role: role || undefined,
    recencyBucket: recencyBucket || undefined,
    sort: company ? sort : 'frontend_id_asc',
    locale,
    page,
    pageSize: 20,
  }), [company, difficulty, keyword, locale, page, recencyBucket, role, sort]);

  useEffect(() => {
    const controller = new AbortController();
    setFiltersError('');

    getProblemFilters(locale, controller.signal)
      .then((response) => {
        setFilters(requireApiData(response, resources.problems.filtersLoadFailed));
      })
      .catch((error) => {
        if (!controller.signal.aborted) {
          setFiltersError(error instanceof Error ? error.message : resources.problems.filtersLoadFailed);
        }
      });

    return () => controller.abort();
  }, [locale, resources.problems.filtersLoadFailed]);

  useEffect(() => {
    const controller = new AbortController();
    setListLoading(true);
    setListError('');

    getProblems(query, controller.signal)
      .then((response) => {
        const nextProblemPage = requireApiData(response, resources.problems.listLoadFailed);
        setProblemPage(nextProblemPage);
        setSelectedSlug((current) => current ?? nextProblemPage.items[0]?.slug);
      })
      .catch((error) => {
        if (!controller.signal.aborted) {
          setListError(error instanceof Error ? error.message : resources.problems.listLoadFailed);
        }
      })
      .finally(() => {
        if (!controller.signal.aborted) {
          setListLoading(false);
        }
      });

    return () => controller.abort();
  }, [query, resources.problems.listLoadFailed]);

  useEffect(() => {
    if (!selectedSlug) {
      setDetail(undefined);
      return;
    }

    const controller = new AbortController();
    setDetailLoading(true);
    setDetailError('');

    getProblemDetail(selectedSlug, locale, controller.signal)
      .then((response) => {
        setDetail(requireApiData(response, resources.problems.detailLoadFailed));
      })
      .catch((error) => {
        if (!controller.signal.aborted) {
          setDetailError(error instanceof Error ? error.message : resources.problems.detailLoadFailed);
        }
      })
      .finally(() => {
        if (!controller.signal.aborted) {
          setDetailLoading(false);
        }
      });

    return () => controller.abort();
  }, [locale, selectedSlug, resources.problems.detailLoadFailed]);

  const totalPages = Math.max(1, Math.ceil((problemPage?.total ?? 0) / (problemPage?.pageSize ?? 20)));
  const difficultyOptions: Array<{ label: string; value: ProblemDifficulty | '' }> = [
    { label: resources.problems.allDifficulty, value: '' },
    { label: formatDifficulty('EASY', resources), value: 'EASY' },
    { label: formatDifficulty('MEDIUM', resources), value: 'MEDIUM' },
    { label: formatDifficulty('HARD', resources), value: 'HARD' },
  ];

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

  function resetListSelection() {
    setPage(1);
    setSelectedSlug(undefined);
  }

  function handleCompanyChange(nextCompany: string) {
    setCompany(nextCompany);
    if (!nextCompany && sort === 'company_frequency_desc') {
      setSort('frontend_id_asc');
    }
    resetListSelection();
  }

  function handleRoleChange(nextRole: string) {
    setRole(nextRole);
    resetListSelection();
  }

  function handleRecencyBucketChange(nextBucket: string) {
    setRecencyBucket(nextBucket);
    resetListSelection();
  }

  function handleSortChange(nextSort: NonNullable<ProblemListQuery['sort']>) {
    if (nextSort === 'company_frequency_desc' && !company) {
      return;
    }
    setSort(nextSort);
    resetListSelection();
  }

  function problemDisplayId(problem: Pick<ProblemListItem, 'frontendDisplayId' | 'frontendId'>) {
    return problem.frontendDisplayId ?? problem.frontendId?.toString() ?? '-';
  }

  return (
    <section className="problem-shell" aria-label={resources.problems.ariaLabel}>
      <div className="problem-toolbar">
        <label className="search-field">
          <Search aria-hidden="true" />
          <input
            aria-label={resources.problems.searchLabel}
            onChange={(event) => handleKeywordChange(event.target.value)}
            placeholder={resources.problems.searchPlaceholder}
            value={keyword}
          />
        </label>
        <label className="filter-field">
          <span>{resources.problems.difficulty}</span>
          <select
            aria-label={resources.problems.difficultyFilter}
            onChange={(event) => handleDifficultyChange(event.target.value as ProblemDifficulty | '')}
            value={difficulty}
          >
            {difficultyOptions.map((option) => (
              <option key={option.value || 'all'} value={option.value}>{option.label}</option>
            ))}
          </select>
        </label>
        <label className="filter-field">
          <span>{resources.problems.company}</span>
          <select
            aria-label={resources.problems.companyFilter}
            onChange={(event) => handleCompanyChange(event.target.value)}
            value={company}
          >
            <option value="">{resources.problems.allCompanies}</option>
            {(filters?.companies ?? []).map((option) => (
              <option key={option.value} value={option.value}>{option.label}</option>
            ))}
          </select>
        </label>
        <label className="filter-field">
          <span>{resources.problems.role}</span>
          <select
            aria-label={resources.problems.roleFilter}
            onChange={(event) => handleRoleChange(event.target.value)}
            value={role}
          >
            <option value="">{resources.problems.allRoles}</option>
            {(filters?.roles ?? []).map((option) => (
              <option key={option.value} value={option.value}>{option.label}</option>
            ))}
          </select>
        </label>
        <label className="filter-field">
          <span>{resources.problems.recencyBucket}</span>
          <select
            aria-label={resources.problems.recencyBucketFilter}
            onChange={(event) => handleRecencyBucketChange(event.target.value)}
            value={recencyBucket}
          >
            <option value="">{resources.problems.allRecencyBuckets}</option>
            {(filters?.recencyBuckets ?? []).map((option) => (
              <option key={option.value} value={option.value}>{option.label}</option>
            ))}
          </select>
        </label>
        <label className="filter-field">
          <span>{resources.problems.sort}</span>
          <select
            aria-label={resources.problems.sortFilter}
            onChange={(event) => handleSortChange(event.target.value as NonNullable<ProblemListQuery['sort']>)}
            value={sort}
          >
            <option value="frontend_id_asc">{resources.problems.sortFrontendAsc}</option>
            <option disabled={!company} value="company_frequency_desc">{resources.problems.sortCompanyFrequencyDesc}</option>
          </select>
        </label>
      </div>

      <div className="problem-layout">
        <article className="problem-list-panel" aria-labelledby="problem-list-title">
          <div className="panel-title compact-title">
            <h2 id="problem-list-title">{resources.problems.listTitle}</h2>
            <span>{resources.problems.totalCount(problemPage?.total ?? 0)}</span>
          </div>
          {listError && <p className="error-text">{listError}</p>}
          {filtersError && filtersError !== listError && <p className="error-text">{filtersError}</p>}
          {listLoading && <p className="empty-log">{resources.problems.loadingList}</p>}
          {!listLoading && !listError && (
            <div className="problem-list">
              {(problemPage?.items ?? []).length === 0 ? (
                <p className="empty-log">{resources.problems.emptyList}</p>
              ) : (
                problemPage?.items.map((problem) => (
                  <button
                    className={`problem-row ${selectedSlug === problem.slug ? 'selected' : ''}`}
                    key={problem.slug}
                    onClick={() => setSelectedSlug(problem.slug)}
                    type="button"
                  >
                    <span className="problem-id">{problemDisplayId(problem)}</span>
                    <span className="problem-title">
                      <strong>{problem.title}</strong>
                      <small>{problem.slug}</small>
                    </span>
                    <span className="problem-badge-group">
                      {(problem.companySignalCount ?? 0) > 0 && (
                        <span className="company-hot-badge">{resources.problems.companySignalBadge}</span>
                      )}
                      <span className={`difficulty-badge ${problem.difficulty?.toLowerCase() ?? 'unknown'}`}>
                        {formatDifficulty(problem.difficulty, resources)}
                      </span>
                    </span>
                  </button>
                ))
              )}
            </div>
          )}
          <div className="pagination-row">
            <button
              aria-label={resources.problems.previousPage}
              className="icon-button"
              disabled={page <= 1}
              onClick={() => setPage((current) => Math.max(1, current - 1))}
              type="button"
            >
              <ChevronLeft aria-hidden="true" />
            </button>
            <span>{page} / {totalPages}</span>
            <button
              aria-label={resources.problems.nextPage}
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
          {detailLoading && <p className="empty-log">{resources.problems.loadingDetail}</p>}
          {detailError && <p className="error-text">{detailError}</p>}
          {!detailLoading && !detailError && detail && (
            <>
              <div className="detail-heading">
                <div>
                  <p className="eyebrow">#{problemDisplayId(detail)}</p>
                  <h2 id="problem-detail-title">{detail.title}</h2>
                  <p>{detail.slug}</p>
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
                  {formatDifficulty(detail.difficulty, resources)}
                </span>
                {detail.contentStatus === 'CN_ONLY' && <span className="content-status-badge">{resources.problems.cnOnlyBadge}</span>}
                {detail.tags.map((tag) => <span className="tag-pill" key={tag.value}>{tag.label}</span>)}
              </div>
              <MarkdownView content={detail.contentMarkdown} />
              {detail.sampleTestCase && (
                <section className="code-section">
                  <h3>{resources.problems.sampleInput}</h3>
                  <pre>{detail.sampleTestCase}</pre>
                </section>
              )}
              {detail.python3Template && (
                <section className="code-section">
                  <h3>{resources.problems.pythonTemplate}</h3>
                  <pre>{detail.python3Template}</pre>
                </section>
              )}
            </>
          )}
          {!detailLoading && !detailError && !detail && (
            <p className="empty-log">{resources.problems.selectProblem}</p>
          )}
        </article>
      </div>
    </section>
  );
}
