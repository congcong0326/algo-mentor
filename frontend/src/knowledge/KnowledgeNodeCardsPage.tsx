import { ArrowLeft, BookOpen, ChevronLeft, ChevronRight, Eye, Plus, Search, Minus } from 'lucide-react';
import { useEffect, useRef, useState } from 'react';
import {
  APP_ROUTES,
  KNOWLEDGE_CARD_PAGE_SIZE,
  hasKnowledgeOutlineHistoryState,
  knowledgeCardPath,
  knowledgeListOptions,
  knowledgeListSearch,
  knowledgeNodeCardsPath,
  knowledgeOutlineSearch,
  knowledgeTopicPath,
  type AppNavigationOptions,
} from '../app/navigation';
import { knowledgeApi } from '../services/knowledge';
import type { KnowledgeCard, KnowledgeNodeDetail, KnowledgePage } from '../types/knowledge';

export default function KnowledgeNodeCardsPage({ nodeId, search = '', onNavigate }: {
  nodeId: number; search?: string; onNavigate: (path: string, options?: AppNavigationOptions) => void;
}) {
  const { page, keyword, expandedNodeIds } = knowledgeListOptions(search);
  const [result, setResult] = useState<KnowledgePage<KnowledgeCard>>();
  const [detail, setDetail] = useState<KnowledgeNodeDetail>();
  const [query, setQuery] = useState(keyword);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [actionError, setActionError] = useState('');
  const [pendingSlug, setPendingSlug] = useState<string>();
  const [reload, setReload] = useState(0);
  const actionPending = useRef(false);
  const listRequest = useRef(0);

  useEffect(() => { setQuery(keyword); }, [keyword]);
  useEffect(() => {
    let active = true;
    knowledgeApi.node(nodeId)
      .then((node) => { if (active) setDetail(node); })
      .catch((e: Error) => { if (active) setActionError(e.message); });
    return () => { active = false; };
  }, [nodeId, reload]);

  useEffect(() => {
    const request = ++listRequest.current;
    setLoading(true);
    setError('');
    knowledgeApi.cards(nodeId, page, KNOWLEDGE_CARD_PAGE_SIZE, keyword)
      .then((value) => {
        if (request !== listRequest.current) return;
        const lastPage = Math.max(1, Math.ceil(value.total / KNOWLEDGE_CARD_PAGE_SIZE));
        if (page > lastPage) {
          navigateWithOutlineContext(knowledgeNodeCardsPath(nodeId) + knowledgeListSearch(lastPage, keyword, expandedNodeIds), { replace: true });
        } else setResult(value);
      })
      .catch((e: Error) => { if (request === listRequest.current) setError(e.message); })
      .finally(() => { if (request === listRequest.current) setLoading(false); });
    return () => { listRequest.current++; };
  }, [nodeId, page, keyword, reload, onNavigate]);

  async function setEnrollment(card: KnowledgeCard, enrolled: boolean) {
    if (actionPending.current) return;
    actionPending.current = true;
    setPendingSlug(card.slug);
    setActionError('');
    const request = listRequest.current;
    try {
      const learningState = await knowledgeApi.setEnrollment(card.slug, enrolled);
      if (request === listRequest.current) {
        setResult((value) => value && { ...value, items: value.items.map((item) => item.slug === card.slug ? { ...item, learningState } : item) });
      }
    } catch (e) { setActionError(e instanceof Error ? e.message : '复习状态更新失败'); }
    finally { actionPending.current = false; setPendingSlug(undefined); }
  }

  const topicId = detail?.breadcrumbs[0]?.id;
  const totalPages = Math.max(1, Math.ceil((result?.total || 0) / KNOWLEDGE_CARD_PAGE_SIZE));
  const changePage = (nextPage: number) => navigateWithOutlineContext(knowledgeNodeCardsPath(nodeId) + knowledgeListSearch(nextPage, keyword, expandedNodeIds));
  const outlinePath = topicId
    ? knowledgeTopicPath(topicId) + (expandedNodeIds === undefined ? '' : knowledgeOutlineSearch(expandedNodeIds))
    : APP_ROUTES.knowledge;

  function navigateWithOutlineContext(path: string, options: AppNavigationOptions = {}) {
    const state = window.history.state;
    if (hasKnowledgeOutlineHistoryState(state)) {
      onNavigate(path, { ...options, state });
    } else if (options.replace) {
      onNavigate(path, options);
    } else {
      onNavigate(path);
    }
  }

  return <section className="knowledge-page knowledge-route-page knowledge-node-cards-page" aria-labelledby="knowledge-node-title">
    <header className="knowledge-header">
      <div>
        <p className="knowledge-eyebrow"><BookOpen aria-hidden="true" /> 知识库{detail?.breadcrumbs.map((item) => ` / ${item.title}`).join('')}</p>
        <h1 id="knowledge-node-title">{detail?.node.title || '节点卡片'}</h1>
        <p className="knowledge-subtitle">浏览知识卡片，选择需要持续复习的内容。</p>
      </div>
      <button className="secondary-button compact" onClick={() => navigateWithOutlineContext(outlinePath)} type="button"><ArrowLeft aria-hidden="true" /> 返回大纲</button>
    </header>
    <form className="knowledge-list-toolbar" onSubmit={(event) => { event.preventDefault(); navigateWithOutlineContext(knowledgeNodeCardsPath(nodeId) + knowledgeListSearch(1, query.trim(), expandedNodeIds)); }}>
      <label className="search-field"><Search aria-hidden="true" /><input aria-label="搜索知识卡或标签" placeholder="搜索知识卡或标签" maxLength={200} value={query} onChange={(event) => setQuery(event.target.value)} /></label>
      <button className="secondary-button compact" type="submit">搜索</button>
      <span>{result ? `共 ${result.total} 张卡片` : '知识卡片'}</span>
    </form>
    {(error || actionError) && <p className="error-text" role="alert">{error || actionError} <button className="secondary-button compact" type="button" onClick={() => { setActionError(''); setReload((value) => value + 1); }}>重试</button></p>}
    <div className="mistake-list" aria-label="知识卡片列表" aria-busy={loading}>
      {loading ? <div className="knowledge-list-skeleton" role="status"><span className="visually-hidden">正在加载知识卡片…</span>{[0, 1, 2].map((item) => <span className="knowledge-list-skeleton-row" key={item} aria-hidden="true" />)}</div> : error ? null : !result?.items.length ? <div className="loading-panel">该节点暂无匹配卡片。</div> : result.items.map((card) => <article className="mistake-note-card knowledge-list-card" key={card.slug}>
        <div className="mistake-note-main">
          <h2>{card.question}</h2>
          <div className="mistake-note-meta"><span>{card.learningState.enrolled ? '复习中' : '未加入复习'}</span>{card.tags.map((tag) => <span key={tag}>{tag}</span>)}</div>
        </div>
        <div className="knowledge-list-actions" aria-label={`${card.question}的操作`}>
          <button className="secondary-button compact" type="button" onClick={() => navigateWithOutlineContext(knowledgeCardPath(card.slug) + knowledgeListSearch(page, keyword, expandedNodeIds))}><Eye aria-hidden="true" /> 查看</button>
          <button className="secondary-button compact" type="button" disabled={!!pendingSlug || card.learningState.enrolled} onClick={() => void setEnrollment(card, true)}><Plus aria-hidden="true" /> 加入复习</button>
          <button className="secondary-button compact" type="button" disabled={!!pendingSlug || !card.learningState.enrolled} onClick={() => void setEnrollment(card, false)}><Minus aria-hidden="true" /> 移除复习</button>
        </div>
      </article>)}
    </div>
    {!loading && !error && !!result?.total && <nav aria-label="知识卡片分页" className="pagination-row mistake-pagination">
      <button aria-label="上一页" className="icon-button" disabled={page <= 1} onClick={() => changePage(page - 1)} type="button"><ChevronLeft aria-hidden="true" /></button>
      <span>第 {page} / {totalPages} 页</span>
      <button aria-label="下一页" className="icon-button" disabled={page >= totalPages} onClick={() => changePage(page + 1)} type="button"><ChevronRight aria-hidden="true" /></button>
    </nav>}
  </section>;
}
