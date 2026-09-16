import { ArrowLeft, BookOpen, Layers3, RotateCcw, Search } from 'lucide-react';
import { useEffect, useMemo, useRef, useState } from 'react';
import { reviewCenterPath, APP_ROUTES } from './app/navigation';
import MarkdownView from './components/MarkdownView';
import KnowledgeCardContent from './components/knowledge/KnowledgeCardContent';
import { useI18n } from './i18n/I18nProvider';
import { allKnowledgePages, knowledgeApi } from './services/knowledge';
import { KNOWLEDGE_RELATION_LABELS } from './types/knowledge';
import type { KnowledgeArticle, KnowledgeCard, KnowledgeNode, KnowledgeTree } from './types/knowledge';

function Outline({ tree, selected, onSelect }: { tree: KnowledgeTree; selected?: number; onSelect: (node: KnowledgeNode) => void }) {
  return <li><button className={`knowledge-card-row ${selected === tree.node.id ? 'selected' : ''}`} onClick={() => onSelect(tree.node)} type="button" aria-current={selected === tree.node.id ? 'true' : undefined}>{tree.node.title}<small>{tree.node.subtreeCardCount} 张卡片</small></button>
    {tree.children.length > 0 && <ul className="knowledge-outline-list">{tree.children.map((child) => <Outline tree={child} selected={selected} onSelect={onSelect} key={child.node.id} />)}</ul>}
  </li>;
}

export default function KnowledgePage({ onNavigate }: { onNavigate?: (path: string) => void } = {}) {
  const { resources } = useI18n();
  const [topics, setTopics] = useState<KnowledgeNode[]>([]);
  const [tree, setTree] = useState<KnowledgeTree>();
  const [node, setNode] = useState<KnowledgeNode>();
  const [cards, setCards] = useState<KnowledgeCard[]>([]);
  const [articles, setArticles] = useState<KnowledgeArticle[]>([]);
  const [selected, setSelected] = useState<KnowledgeCard>();
  const [article, setArticle] = useState<KnowledgeArticle>();
  const [answer, setAnswer] = useState(false);
  const [loading, setLoading] = useState(true);
  const [contentLoading, setContentLoading] = useState(false);
  const [detailLoading, setDetailLoading] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');
  const [query, setQuery] = useState('');
  const requestId = useRef(0);
  const detailId = useRef(0);
  const attempt = useRef<{ slug: string; rating: string; id: string } | undefined>(undefined);

  useEffect(() => { let active = true; knowledgeApi.topics().then((data) => { if (active) setTopics(data.items); }).catch((e: Error) => { if (active) setError(e.message); }).finally(() => { if (active) setLoading(false); }); return () => { active = false; requestId.current++; detailId.current++; }; }, []);

  async function openNode(next: KnowledgeNode) {
    const id = ++requestId.current; detailId.current++;
    setNode(next); setSelected(undefined); setArticle(undefined); setCards([]); setArticles([]); setQuery(''); setError(''); setContentLoading(true); setDetailLoading(false);
    try {
      const [nextCards, nextArticles] = await Promise.all([allKnowledgePages((page) => knowledgeApi.cards(next.id, page)), allKnowledgePages((page) => knowledgeApi.articles(next.id, page))]);
      if (id === requestId.current) { setCards(nextCards); setArticles(nextArticles); }
    } catch (e) { if (id === requestId.current) setError((e as Error).message); }
    finally { if (id === requestId.current) setContentLoading(false); }
  }
  async function openTopic(topic: KnowledgeNode) {
    const id = ++requestId.current; setError(''); setContentLoading(true);
    try { const next = await knowledgeApi.tree(topic.id); if (id === requestId.current) { setTree(next); await openNode(next.node); } }
    catch (e) { if (id === requestId.current) { setError((e as Error).message); setContentLoading(false); } }
  }
  async function selectCard(slug: string) {
    const id = ++detailId.current; setSelected(undefined); setArticle(undefined); setAnswer(false); setError(''); setDetailLoading(true);
    try { const next = await knowledgeApi.card(slug); if (id === detailId.current) setSelected(next); }
    catch (e) { if (id === detailId.current) setError((e as Error).message); }
    finally { if (id === detailId.current) setDetailLoading(false); }
  }
  async function selectArticle(id: number) {
    const request = ++detailId.current; setSelected(undefined); setArticle(undefined); setError(''); setDetailLoading(true);
    try { const next = await knowledgeApi.article(id); if (request === detailId.current) setArticle(next); }
    catch (e) { if (request === detailId.current) setError((e as Error).message); }
    finally { if (request === detailId.current) setDetailLoading(false); }
  }
  async function review(rating: string) {
    if (!selected || submitting) return;
    const card = selected; setSubmitting(true); setError('');
    if (attempt.current?.slug !== card.slug || attempt.current.rating !== rating) attempt.current = { slug: card.slug, rating, id: crypto.randomUUID() };
    try {
      await knowledgeApi.review(card.slug, rating, attempt.current.id); attempt.current = undefined;
      const learningState = { ...card.learningState, enrolled: true, isDue: false };
      setSelected((current) => current?.slug === card.slug ? { ...current, learningState } : current);
      setCards((current) => current.map((item) => item.slug === card.slug ? { ...item, learningState } : item));
      setTopics((await knowledgeApi.topics()).items);
    } catch (e) { setError((e as Error).message); }
    finally { setSubmitting(false); }
  }
  const filtered = useMemo(() => cards.filter((card) => `${card.question} ${card.tags.join(' ')}`.toLowerCase().includes(query.trim().toLowerCase())), [cards, query]);
  return <section className="knowledge-page" aria-labelledby="knowledge-title">
    <header className="knowledge-header"><div><p className="knowledge-eyebrow"><BookOpen aria-hidden="true" /> {resources.nav.knowledge}</p><h1 id="knowledge-title">知识库</h1><p className="knowledge-subtitle">沿大纲阅读文章、学习知识卡，再通过复习巩固记忆。</p></div><button className="secondary-button" onClick={() => onNavigate?.(reviewCenterPath({ mode: 'knowledge' }))} type="button"><RotateCcw aria-hidden="true" /> 查看复习中心</button></header>
    <dl className="knowledge-stat-grid" aria-label="知识库概览"><div><dt>技术主题</dt><dd>{topics.length}</dd></div><div><dt>知识卡片</dt><dd>{topics.reduce((sum, item) => sum + item.subtreeCardCount, 0)}</dd></div><div><dt>已加入复习</dt><dd>{topics.reduce((sum, item) => sum + item.subtreeEnrolledCardCount, 0)}</dd></div></dl>
    {error && <p className="error-text" role="alert">{error}</p>}
    {loading ? <p role="status">正在加载知识库…</p> : !tree ? <div className="knowledge-topic-grid">{topics.length === 0 ? <p>暂无知识主题。</p> : topics.map((topic) => <button className="knowledge-topic-card" key={topic.id} disabled={contentLoading} onClick={() => void openTopic(topic)} type="button"><Layers3 aria-hidden="true" /><span className="knowledge-topic-copy"><strong>{topic.title}</strong><span>{topic.subtreeCardCount} 张卡片</span></span></button>)}</div> : <div className="knowledge-workspace">
      <section className="knowledge-browser" aria-label="知识大纲">
        <button className="secondary-button compact" type="button" onClick={() => { requestId.current++; detailId.current++; setTree(undefined); setSelected(undefined); setArticle(undefined); setContentLoading(false); }}><ArrowLeft aria-hidden="true" /> 返回主题</button>
        <ul className="knowledge-outline-list"><Outline tree={tree} selected={node?.id} onSelect={(next) => void openNode(next)} /></ul>
        <h2>{node?.title}的内容</h2>
        {contentLoading ? <p role="status">正在加载内容…</p> : <>
          {articles.length > 0 && <section aria-label="文章"><h3>文章</h3>{articles.map((item) => <button className="knowledge-card-row" key={item.id} type="button" onClick={() => void selectArticle(item.id)}><BookOpen aria-hidden="true" />{item.title}</button>)}</section>}
          <label className="search-field knowledge-search"><Search aria-hidden="true" /><input aria-label="搜索知识卡或标签" placeholder="搜索知识卡或标签" value={query} onChange={(event) => setQuery(event.target.value)} /></label>
          <div className="knowledge-card-list">{filtered.length === 0 ? <p>当前节点暂无匹配卡片，可选择下级大纲。</p> : filtered.map((card) => <button className={`knowledge-card-row ${selected?.slug === card.slug ? 'selected' : ''}`} key={card.slug} type="button" onClick={() => void selectCard(card.slug)}><span>{card.question}</span><small>{card.learningState.enrolled ? '复习中' : '未加入复习'}</small></button>)}</div>
        </>}
      </section>
      <article className="knowledge-detail learning-panel" aria-live="polite">
        {detailLoading ? <p role="status">正在加载详情…</p> : article ? <><span className="knowledge-detail-label">文章</span><h2>{article.title}</h2><MarkdownView content={article.bodyMarkdown || ''} /></> : selected ? <>
          <span className="knowledge-detail-label">知识卡</span><h2>{selected.question}</h2>
          <div className="knowledge-tags">{selected.tags.map((tag) => <span className="status-pill" key={tag}>{tag}</span>)}</div>
          {!answer ? <div className="knowledge-answer-gate"><p>先独立回忆，再查看答案与详细解释。</p><button className="primary-button" type="button" onClick={() => setAnswer(true)}>显示答案</button></div> : <>
            <KnowledgeCardContent card={selected} />
            <div className="knowledge-review-actions"><span>完成回忆后选择熟练度</span><div className="button-row">{(['AGAIN', 'HARD', 'GOOD', 'EASY'] as const).map((rating) => <button className="secondary-button" key={rating} disabled={submitting} type="button" onClick={() => void review(rating)}>{resources.reviewCenter.ratingLabels[rating]}</button>)}</div></div>
          </>}
          {Object.entries(KNOWLEDGE_RELATION_LABELS).map(([type, label]) => {
            const relations = selected.relations?.filter((relation) => relation.type === type) ?? [];
            return relations.length > 0 && <section className="knowledge-relations" aria-label={label} key={type}><h3>{label}</h3>{relations.map((relation) => <button className="knowledge-card-row" key={relation.slug} type="button" onClick={() => void selectCard(relation.slug)}>{relation.question}</button>)}</section>;
          })}
        </> : <div className="knowledge-empty-detail"><h2>选择一张卡片或一篇文章</h2><p>通过左侧大纲进入具体主题。</p></div>}
      </article>
    </div>}
  </section>;
}
