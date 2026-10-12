import { Eye, Layers3, Minus, Plus } from 'lucide-react';
import { useEffect, useRef, useState } from 'react';
import { allKnowledgePages, knowledgeApi } from '../services/knowledge';
import type { KnowledgeCard } from '../types/knowledge';

/** 直属卡片沿用大纲的分支线，正文仍由独立详情页承载。 */
export default function KnowledgeOutlineCardNodes({ nodeId, title, expanded, onView, onReady }: {
  nodeId: number;
  title: string;
  expanded: boolean;
  onView: (slug: string) => void;
  onReady: (nodeId: number) => void;
}) {
  const [cards, setCards] = useState<KnowledgeCard[]>();
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [actionError, setActionError] = useState('');
  const [pendingSlug, setPendingSlug] = useState<string>();
  const [reload, setReload] = useState(0);
  const actionPending = useRef(false);
  const mounted = useRef(true);

  useEffect(() => {
    mounted.current = true;
    return () => { mounted.current = false; };
  }, []);

  useEffect(() => {
    if (!expanded) return undefined;
    if (cards !== undefined) {
      onReady(nodeId);
      return undefined;
    }
    let active = true;
    setLoading(true);
    setError('');
    allKnowledgePages((page) => knowledgeApi.cards(nodeId, page))
      .then((value) => { if (active) setCards(value); })
      .catch((e: Error) => { if (active) setError(e.message); })
      .finally(() => {
        if (active) { setLoading(false); onReady(nodeId); }
      });
    return () => { active = false; };
  // cards 是节点内缓存；仅展开或重试时发起读取。
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [expanded, nodeId, reload, onReady]);

  async function setEnrollment(card: KnowledgeCard, enrolled: boolean) {
    if (actionPending.current) return;
    actionPending.current = true;
    setPendingSlug(card.slug);
    setActionError('');
    try {
      const learningState = await knowledgeApi.setEnrollment(card.slug, enrolled);
      if (mounted.current) setCards((value) => value?.map((item) => item.slug === card.slug ? { ...item, learningState } : item));
    } catch (e) {
      if (mounted.current) setActionError(e instanceof Error ? e.message : '复习状态更新失败');
    } finally {
      actionPending.current = false;
      if (mounted.current) setPendingSlug(undefined);
    }
  }

  return <ul className="knowledge-tree-list knowledge-tree-children knowledge-tree-cards" id={`knowledge-cards-${nodeId}`} aria-label={`${title}的卡片`} hidden={!expanded} aria-busy={loading}>
    {loading && <li className="knowledge-tree-item"><div className="knowledge-tree-content-message" role="status">正在加载卡片…</div></li>}
    {error && <li className="knowledge-tree-item"><div className="knowledge-tree-content-message error-text" role="alert">{error} <button className="knowledge-tree-action" type="button" onClick={() => setReload((value) => value + 1)}>重试</button></div></li>}
    {actionError && <li className="knowledge-tree-item"><div className="knowledge-tree-content-message error-text" role="alert">{actionError}</div></li>}
    {!loading && !error && cards?.length === 0 && <li className="knowledge-tree-item"><div className="knowledge-tree-content-message">该节点暂无卡片。</div></li>}
    {cards?.map((card) => <li className="knowledge-tree-item knowledge-tree-card-item" key={card.slug}>
      <div className="knowledge-tree-row knowledge-tree-card-row">
        <span className="knowledge-tree-spacer" aria-hidden="true"><span /></span>
        <span className="knowledge-tree-icon is-card"><Layers3 aria-hidden="true" /></span>
        <div className="knowledge-tree-copy">
          <span className="knowledge-tree-title">{card.question}</span>
          <div className="knowledge-tree-card-meta"><span>{card.learningState.enrolled ? '复习中' : '未加入复习'}</span>{card.tags.map((tag) => <span key={tag}>{tag}</span>)}</div>
        </div>
        <div className="knowledge-tree-actions" aria-label={`${card.question}的操作`}>
          <button className="knowledge-tree-action" type="button" onClick={() => onView(card.slug)}><Eye aria-hidden="true" /> 查看</button>
          <button className="knowledge-tree-action" type="button" disabled={!!pendingSlug || card.learningState.enrolled} onClick={() => void setEnrollment(card, true)}><Plus aria-hidden="true" /> 加入复习</button>
          <button className="knowledge-tree-action" type="button" disabled={!!pendingSlug || !card.learningState.enrolled} onClick={() => void setEnrollment(card, false)}><Minus aria-hidden="true" /> 移除复习</button>
        </div>
      </div>
    </li>)}
  </ul>;
}
