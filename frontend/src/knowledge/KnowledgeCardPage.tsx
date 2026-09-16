import { ArrowLeft, BookOpen } from 'lucide-react';
import { useEffect, useState } from 'react';
import { APP_ROUTES, knowledgeCardPath, knowledgeListOptions, knowledgeListSearch, knowledgeNodeCardsPath } from '../app/navigation';
import KnowledgeCardContent from '../components/knowledge/KnowledgeCardContent';
import { knowledgeApi } from '../services/knowledge';
import { KNOWLEDGE_RELATION_LABELS, type KnowledgeCard, type KnowledgeRelationType } from '../types/knowledge';

export default function KnowledgeCardPage({ slug, search = '', onNavigate }: {
  slug: string; search?: string; onNavigate: (path: string) => void;
}) {
  const [card, setCard] = useState<KnowledgeCard>();
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);
  const [reload, setReload] = useState(0);
  const { page, keyword } = knowledgeListOptions(search);

  useEffect(() => {
    let active = true;
    setCard(undefined);
    setError('');
    setLoading(true);
    knowledgeApi.card(slug)
      .then((value) => { if (active) setCard(value); })
      .catch((e: Error) => { if (active) setError(e.message); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [slug, reload]);

  return <section className="knowledge-review-page knowledge-card-page" aria-labelledby="knowledge-card-title">
    <header className="knowledge-header">
      <div><p className="knowledge-eyebrow"><BookOpen aria-hidden="true" /> 知识卡片</p><h1 id="knowledge-card-title">{card?.question || '卡片详情'}</h1></div>
      <button className="secondary-button compact" type="button" onClick={() => onNavigate(card ? knowledgeNodeCardsPath(card.outlineNodeId) + knowledgeListSearch(page, keyword) : APP_ROUTES.knowledge)}><ArrowLeft aria-hidden="true" /> {card ? '返回卡片列表' : '返回知识库'}</button>
    </header>
    {loading ? <div className="loading-panel" role="status">正在加载卡片详情…</div> : error ? <p className="error-text" role="alert">{error} <button className="secondary-button compact" type="button" onClick={() => setReload((value) => value + 1)}>重试</button></p> : card && <>
      <article className="learning-panel knowledge-card-detail">
        <div className="knowledge-tags"><span className="status-pill">{card.learningState.enrolled ? '复习中' : '未加入复习'}</span>{card.tags.map((tag) => <span className="status-pill" key={tag}>{tag}</span>)}</div>
        <KnowledgeCardContent card={card} />
      </article>
      {(Object.entries(KNOWLEDGE_RELATION_LABELS) as [KnowledgeRelationType, string][]).map(([type, label]) => {
        const relations = card.relations?.filter((relation) => relation.type === type) || [];
        return relations.length > 0 && <section className="knowledge-node-articles" aria-label={label} key={type}><h2>{label}</h2>{relations.map((relation) => <button className="knowledge-card-row" key={relation.slug} type="button" onClick={() => onNavigate(knowledgeCardPath(relation.slug))}>{relation.question}</button>)}</section>;
      })}
    </>}
  </section>;
}
