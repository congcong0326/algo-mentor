import { ArrowLeft, BookOpen, ChevronDown, ChevronRight, FileText, Folder, FolderOpen, Layers3, ListTree, RotateCcw } from 'lucide-react';
import { useEffect, useState } from 'react';
import {
  reviewCenterPath,
  APP_ROUTES,
  knowledgeListSearch,
  knowledgeNodeArticlesPath,
  knowledgeNodeCardsPath,
  knowledgeOutlineExpandedIdsFromSearch,
  knowledgeOutlineScrollFromHistoryState,
  knowledgeOutlineSearch,
  knowledgeTopicPath,
  withKnowledgeOutlineHistoryState,
  type AppNavigationOptions,
} from '../app/navigation';
import { useI18n } from '../i18n/I18nProvider';
import { knowledgeApi } from '../services/knowledge';
import type { KnowledgeTree } from '../types/knowledge';

function OutlineTree({ tree, expandedNodeIds, onOpenArticles, onOpenCards, onToggle, depth = 0 }: {
  tree: KnowledgeTree;
  expandedNodeIds: ReadonlySet<number>;
  onOpenArticles: (nodeId: number) => void;
  onOpenCards: (nodeId: number) => void;
  onToggle: (nodeId: number) => void;
  depth?: number;
}) {
  const { node, children } = tree;
  const hasChildren = children.length > 0;
  const expanded = expandedNodeIds.has(node.id);
  const NodeIcon = hasChildren ? (expanded ? FolderOpen : Folder) : BookOpen;
  const childrenId = `knowledge-children-${node.id}`;

  return <li className="knowledge-tree-item">
    <div className={`knowledge-tree-row${depth === 0 ? ' knowledge-tree-row-root' : ''}`}>
      {hasChildren ? <button
        className="knowledge-tree-toggle"
        aria-label={`${expanded ? '收起' : '展开'}${node.title}`}
        aria-expanded={expanded}
        aria-controls={expanded ? childrenId : undefined}
        onClick={() => onToggle(node.id)}
        type="button"
      >{expanded ? <ChevronDown aria-hidden="true" /> : <ChevronRight aria-hidden="true" />}</button> : <span className="knowledge-tree-spacer" aria-hidden="true"><span /></span>}
      <span className={`knowledge-tree-icon${hasChildren ? ' is-folder' : ''}`}><NodeIcon aria-hidden="true" /></span>
      <div className="knowledge-tree-copy">
        {hasChildren ? <button className="knowledge-tree-title" type="button" aria-expanded={expanded} aria-controls={expanded ? childrenId : undefined} onClick={() => onToggle(node.id)}>{node.title}</button> : <span className="knowledge-tree-title">{node.title}</span>}
        {hasChildren ? <span className="knowledge-tree-meta">{children.length} 个子节点</span> : !node.directArticleCount && !node.directCardCount ? <span className="knowledge-tree-meta">暂无内容</span> : null}
      </div>
      {(node.directArticleCount > 0 || node.directCardCount > 0) && <div className="knowledge-tree-actions" aria-label={`${node.title}的内容`}>
        {node.directArticleCount > 0 && <button className="knowledge-tree-action knowledge-tree-article-action" aria-label={`查看${node.title}的文章（${node.directArticleCount}篇）`} onClick={() => onOpenArticles(node.id)} type="button"><FileText aria-hidden="true" /><span>文章</span><span className="knowledge-tree-count">{node.directArticleCount}</span></button>}
        {node.directCardCount > 0 && <button className="knowledge-tree-action" aria-label={`查看${node.title}的卡片（${node.directCardCount}张）`} onClick={() => onOpenCards(node.id)} type="button"><Layers3 aria-hidden="true" /><span>卡片</span><span className="knowledge-tree-count">{node.directCardCount}</span></button>}
      </div>}
    </div>
    {hasChildren && expanded && <ul className="knowledge-tree-list knowledge-tree-children" id={childrenId}>{children.map((child) => <OutlineTree key={child.node.id} tree={child} expandedNodeIds={expandedNodeIds} onOpenArticles={onOpenArticles} onOpenCards={onOpenCards} onToggle={onToggle} depth={depth + 1} />)}</ul>}
  </li>;
}

export default function KnowledgeOutlinePage({ topicId, search = '', onNavigate }: {
  topicId: number;
  search?: string;
  onNavigate: (path: string, options?: AppNavigationOptions) => void;
}) {
  const { resources } = useI18n();
  const [tree, setTree] = useState<KnowledgeTree>();
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [reload, setReload] = useState(0);

  useEffect(() => {
    let active = true;
    setLoading(true);
    setTree(undefined);
    setError('');
    knowledgeApi.tree(topicId)
      .then((value) => { if (active) setTree(value); })
      .catch((e: Error) => { if (active) setError(e.message); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [topicId, reload]);

  useEffect(() => {
    if (loading || !tree) return undefined;
    const scrollY = knowledgeOutlineScrollFromHistoryState(window.history.state, topicId);
    if (scrollY === undefined) return undefined;
    const frame = window.requestAnimationFrame(() => window.scrollTo({ top: scrollY, behavior: 'auto' }));
    return () => window.cancelAnimationFrame(frame);
  }, [loading, topicId, tree]);

  const requestedExpandedNodeIds = knowledgeOutlineExpandedIdsFromSearch(search);
  const [expandedNodeIds, setExpandedNodeIds] = useState<number[]>(() => requestedExpandedNodeIds ?? [topicId]);
  useEffect(() => {
    setExpandedNodeIds(requestedExpandedNodeIds ?? [topicId]);
  // search 是该 UI 状态的可分享来源；本地 state 仅用于导航提交前的一帧即时反馈。
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [search, topicId]);
  const expandedNodeIdSet = new Set(expandedNodeIds);
  function toggleNode(nodeId: number) {
    const next = expandedNodeIdSet.has(nodeId)
      ? expandedNodeIds.filter((id) => id !== nodeId)
      : [...expandedNodeIds, nodeId];
    setExpandedNodeIds(next);
    onNavigate(knowledgeTopicPath(topicId) + knowledgeOutlineSearch(next), { replace: true });
  }
  function openContent(path: string) {
    const state = withKnowledgeOutlineHistoryState(window.history.state, topicId, window.scrollY);
    window.history.replaceState(state, '', window.location.href);
    onNavigate(path, { state });
  }

  return <section className="knowledge-page knowledge-route-page knowledge-outline-page" aria-labelledby="knowledge-outline-title">
    <header className="knowledge-header">
      <div><p className="knowledge-eyebrow"><BookOpen aria-hidden="true" /> {resources.nav.knowledge}</p><h1 id="knowledge-outline-title">{tree?.node.title || '知识大纲'}</h1><p className="knowledge-subtitle">沿大纲探索知识，阅读文章或选择卡片巩固记忆。</p></div>
      <div className="button-row"><button className="secondary-button compact" onClick={() => onNavigate(APP_ROUTES.knowledge)} type="button"><ArrowLeft aria-hidden="true" /> 返回主题</button><button className="secondary-button compact" onClick={() => onNavigate(reviewCenterPath({ mode: 'knowledge' }))} type="button"><RotateCcw aria-hidden="true" /> 复习中心</button></div>
    </header>
    {error && <p className="error-text" role="alert">{error} <button className="secondary-button compact" type="button" onClick={() => setReload((value) => value + 1)}>重试</button></p>}
    {loading ? <p role="status">正在加载知识大纲…</p> : tree && <section className="knowledge-outline-panel" aria-label="知识大纲">
      <div className="knowledge-outline-panel-heading"><div className="knowledge-outline-heading-copy"><span className="knowledge-outline-heading-icon"><ListTree aria-hidden="true" /></span><div><h2>内容大纲</h2><p>展开目录，通过节点右侧的入口开始学习</p></div></div><span className="knowledge-outline-total"><Layers3 aria-hidden="true" /> {tree.node.subtreeCardCount} 张卡片</span></div>
      <ul className="knowledge-tree-list knowledge-tree-root"><OutlineTree key={topicId} tree={tree} expandedNodeIds={expandedNodeIdSet} onToggle={toggleNode} onOpenArticles={(nodeId) => openContent(knowledgeNodeArticlesPath(nodeId) + knowledgeOutlineSearch(expandedNodeIds))} onOpenCards={(nodeId) => openContent(knowledgeNodeCardsPath(nodeId) + knowledgeListSearch(1, '', expandedNodeIds))} /></ul>
    </section>}
  </section>;
}
