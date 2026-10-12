import { Eye, FileText } from 'lucide-react';
import { useEffect, useState } from 'react';
import { allKnowledgePages, knowledgeApi } from '../services/knowledge';
import type { KnowledgeArticle } from '../types/knowledge';

/** 直属文章按需展开为子节点，正文由现有文章阅读页展示。 */
export default function KnowledgeOutlineArticleNodes({ nodeId, title, expanded, onView, onReady }: {
  nodeId: number;
  title: string;
  expanded: boolean;
  onView: (nodeId: number, articleId: number) => void;
  onReady: (nodeId: number) => void;
}) {
  const [articles, setArticles] = useState<KnowledgeArticle[]>();
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [reload, setReload] = useState(0);

  useEffect(() => {
    if (!expanded) return undefined;
    if (articles !== undefined) {
      onReady(nodeId);
      return undefined;
    }
    let active = true;
    setLoading(true);
    setError('');
    allKnowledgePages((page) => knowledgeApi.articles(nodeId, page))
      .then((value) => { if (active) setArticles(value); })
      .catch((e: Error) => { if (active) setError(e.message); })
      .finally(() => {
        if (active) { setLoading(false); onReady(nodeId); }
      });
    return () => { active = false; };
  // articles 是节点内缓存；仅展开或重试时读取。
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [expanded, nodeId, reload, onReady]);

  return <ul className="knowledge-tree-list knowledge-tree-children knowledge-tree-articles" id={`knowledge-articles-${nodeId}`} aria-label={`${title}的文章`} hidden={!expanded} aria-busy={loading}>
    {loading && <li className="knowledge-tree-item"><div className="knowledge-tree-content-message" role="status">正在加载文章…</div></li>}
    {error && <li className="knowledge-tree-item"><div className="knowledge-tree-content-message error-text" role="alert">{error} <button className="knowledge-tree-action" type="button" onClick={() => setReload((value) => value + 1)}>重试</button></div></li>}
    {!loading && !error && articles?.length === 0 && <li className="knowledge-tree-item"><div className="knowledge-tree-content-message">该节点暂无文章。</div></li>}
    {articles?.map((article) => <li className="knowledge-tree-item knowledge-tree-article-item" key={article.id}>
      <div className="knowledge-tree-row">
        <span className="knowledge-tree-spacer" aria-hidden="true"><span /></span>
        <span className="knowledge-tree-icon"><FileText aria-hidden="true" /></span>
        <div className="knowledge-tree-copy"><span className="knowledge-tree-title">{article.title}</span></div>
        <div className="knowledge-tree-actions" aria-label={`${article.title}的操作`}>
          <button className="knowledge-tree-action" type="button" onClick={() => onView(nodeId, article.id)}><Eye aria-hidden="true" /> 查看</button>
        </div>
      </div>
    </li>)}
  </ul>;
}
