import { BookOpen, Layers3, RotateCcw } from 'lucide-react';
import { useEffect, useState } from 'react';
import { reviewCenterPath, APP_ROUTES, knowledgeTopicPath } from '../app/navigation';
import { useI18n } from '../i18n/I18nProvider';
import { knowledgeApi } from '../services/knowledge';
import type { KnowledgeNode } from '../types/knowledge';

export default function KnowledgeTopicPage({ onNavigate }: { onNavigate: (path: string) => void }) {
  const { resources } = useI18n();
  const [topics, setTopics] = useState<KnowledgeNode[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    let active = true;
    knowledgeApi.topics().then((data) => { if (active) setTopics(data.items); })
      .catch((e: Error) => { if (active) setError(e.message); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, []);

  return <section className="knowledge-page" aria-labelledby="knowledge-title">
    <header className="knowledge-header">
      <div><p className="knowledge-eyebrow"><BookOpen aria-hidden="true" /> {resources.nav.knowledge}</p><h1 id="knowledge-title">知识库</h1><p className="knowledge-subtitle">先选择一个技术主题，再沿大纲定位你要学习的知识。</p></div>
      <button className="secondary-button" onClick={() => onNavigate(reviewCenterPath({ mode: 'knowledge' }))} type="button"><RotateCcw aria-hidden="true" /> 查看复习中心</button>
    </header>
    <dl className="knowledge-stat-grid" aria-label="知识库概览"><div><dt>技术主题</dt><dd>{topics.length}</dd></div><div><dt>知识卡片</dt><dd>{topics.reduce((sum, item) => sum + item.subtreeCardCount, 0)}</dd></div><div><dt>已加入复习</dt><dd>{topics.reduce((sum, item) => sum + item.subtreeEnrolledCardCount, 0)}</dd></div></dl>
    {error && <p className="error-text" role="alert">{error}</p>}
    {loading ? <p role="status">正在加载知识库…</p> : <div className="knowledge-topic-grid">{topics.length === 0 ? <p>暂无知识主题。</p> : topics.map((topic) => <button className="knowledge-topic-card" key={topic.id} onClick={() => onNavigate(knowledgeTopicPath(topic.id))} type="button"><span className="knowledge-topic-icon"><Layers3 aria-hidden="true" /></span><span className="knowledge-topic-copy"><strong>{topic.title}</strong><span>{topic.subtreeCardCount} 张卡片 · {topic.subtreeEnrolledCardCount} 张复习中</span></span><span className="knowledge-topic-meta">进入大纲 →</span></button>)}</div>}
  </section>;
}
