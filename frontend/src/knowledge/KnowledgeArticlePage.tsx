import { ArrowLeft, BookOpen, ChevronRight, FileText } from 'lucide-react';
import { useEffect, useRef, useState } from 'react';
import {
  APP_ROUTES,
  hasKnowledgeOutlineHistoryState,
  knowledgeOutlineExpandedIdsFromSearch,
  knowledgeOutlineCardIdsFromSearch,
  knowledgeOutlineArticleIdsFromSearch,
  knowledgeOutlineReturnPathFromHistoryState,
  knowledgeArticleIdFromSearch,
  knowledgeArticleSearch,
  knowledgeNodeArticlesPath,
  knowledgeOutlineSearch,
  knowledgeTopicPath,
  type AppNavigationOptions,
} from '../app/navigation';
import MarkdownView from '../components/MarkdownView';
import { allKnowledgePages, knowledgeApi } from '../services/knowledge';
import type { KnowledgeArticle, KnowledgeNodeDetail } from '../types/knowledge';

export default function KnowledgeArticlePage({ nodeId, search = '', onNavigate }: {
  nodeId: number;
  search?: string;
  onNavigate: (path: string, options?: AppNavigationOptions) => void;
}) {
  const [articles, setArticles] = useState<KnowledgeArticle[]>([]);
  const [article, setArticle] = useState<KnowledgeArticle>();
  const [selectedArticleId, setSelectedArticleId] = useState<number>();
  const [detail, setDetail] = useState<KnowledgeNodeDetail>();
  const [loading, setLoading] = useState(true);
  const [articleLoading, setArticleLoading] = useState(false);
  const [error, setError] = useState('');
  const [reload, setReload] = useState(0);
  const articleRequest = useRef(0);
  const expandedNodeIds = knowledgeOutlineExpandedIdsFromSearch(search);
  const cardNodeIds = knowledgeOutlineCardIdsFromSearch(search);
  const articleNodeIds = knowledgeOutlineArticleIdsFromSearch(search);
  const requestedArticleId = knowledgeArticleIdFromSearch(search);
  const outlineReturnPath = knowledgeOutlineReturnPathFromHistoryState(window.history.state);

  function navigateWithOutlineContext(path: string, options?: AppNavigationOptions) {
    const state = window.history.state;
    if (hasKnowledgeOutlineHistoryState(state)) onNavigate(path, { ...options, state });
    else if (options) onNavigate(path, options);
    else onNavigate(path);
  }

  useEffect(() => {
    let active = true;
    setLoading(true);
    setError('');
    setArticles([]);
    setArticle(undefined);
    setSelectedArticleId(undefined);
    setDetail(undefined);
    setArticleLoading(false);
    Promise.all([knowledgeApi.node(nodeId), allKnowledgePages((page) => knowledgeApi.articles(nodeId, page))])
      .then(([node, items]) => {
        if (!active) return;
        setDetail(node);
        setArticles(items);
      })
      .catch((e: Error) => { if (active) setError(e.message); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; articleRequest.current++; };
  }, [nodeId, reload]);

  useEffect(() => {
    if (loading) return;
    const selected = requestedArticleId === undefined ? articles[0] : articles.find((item) => item.id === requestedArticleId);
    if (selected) {
      if (selected.id !== selectedArticleId) void openArticle(selected.id);
    } else if (requestedArticleId !== undefined && !error) {
      articleRequest.current++;
      setArticle(undefined);
      setSelectedArticleId(undefined);
      setArticleLoading(false);
      setError('该文章不属于当前知识点或已不可用。');
    }
  // 目录点击先即时加载；URL 同步后已选中的文章无需重复读取。
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [articles, loading, requestedArticleId]);

  function selectArticle(id: number) {
    void openArticle(id);
    navigateWithOutlineContext(knowledgeNodeArticlesPath(nodeId) + knowledgeArticleSearch(search, id), { replace: true });
  }

  async function openArticle(id: number) {
    const request = ++articleRequest.current;
    setSelectedArticleId(id);
    setArticle(undefined);
    setArticleLoading(true);
    setError('');
    try {
      const value = await knowledgeApi.article(id);
      if (request === articleRequest.current) setArticle(value);
    } catch (e) {
      if (request === articleRequest.current) setError(e instanceof Error ? e.message : '文章加载失败');
    } finally {
      if (request === articleRequest.current) setArticleLoading(false);
    }
  }

  const topicId = detail?.breadcrumbs[0]?.id;
  const outlinePath = outlineReturnPath || (topicId
    ? knowledgeTopicPath(topicId) + knowledgeOutlineSearch(expandedNodeIds, cardNodeIds, articleNodeIds)
    : APP_ROUTES.knowledge);
  return <section className="knowledge-page knowledge-route-page knowledge-article-page" aria-labelledby="knowledge-article-page-title">
    <header className="knowledge-header">
      <div><p className="knowledge-eyebrow"><BookOpen aria-hidden="true" /> 知识库{detail?.breadcrumbs.map((item) => ` / ${item.title}`).join('')}</p><h1 id="knowledge-article-page-title">{detail?.node.title || '节点文章'}</h1><p className="knowledge-subtitle">阅读当前知识节点下的专题文章。</p></div>
      <button className="secondary-button compact" onClick={() => navigateWithOutlineContext(outlinePath)} type="button"><ArrowLeft aria-hidden="true" /> 返回大纲</button>
    </header>
    {error && <p className="error-text" role="alert">{error} <button className="secondary-button compact" type="button" onClick={() => selectedArticleId ? void openArticle(selectedArticleId) : setReload((value) => value + 1)}>重试</button></p>}
    {loading ? <div className="loading-panel" role="status">正在加载文章…</div> : articles.length === 0 ? !error && <div className="loading-panel">该节点暂无文章。</div> : <div className={`knowledge-article-workspace${articles.length === 1 ? ' single-article' : ''}`}>
      {articles.length > 1 && <aside className="knowledge-article-directory" aria-label="文章目录"><div className="knowledge-article-directory-heading"><FileText aria-hidden="true" /><span>文章目录</span><small>{articles.length}</small></div>{articles.map((item) => <button aria-current={selectedArticleId === item.id ? 'page' : undefined} key={item.id} onClick={() => selectArticle(item.id)} type="button"><span>{item.title}</span><ChevronRight aria-hidden="true" /></button>)}</aside>}
      <article className="learning-panel knowledge-article-detail" aria-busy={articleLoading}>{articleLoading && !article ? <p role="status">正在打开文章…</p> : article && <><div className="knowledge-article-heading"><span>专题文章</span><h2>{article.title}</h2></div><div className="review-problem-content"><MarkdownView content={article.bodyMarkdown || ''} /></div></>}</article>
    </div>}
  </section>;
}
