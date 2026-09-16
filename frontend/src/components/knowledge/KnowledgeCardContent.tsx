import MarkdownView from '../MarkdownView';
import type { KnowledgeCard } from '../../types/knowledge';

export default function KnowledgeCardContent({ card }: { card: KnowledgeCard }) {
  return <div className="knowledge-answer">
    <MarkdownView content={card.answerMarkdown || '暂无答案。'} />
    {card.explanationMarkdown && <><hr /><MarkdownView content={card.explanationMarkdown} /></>}
  </div>;
}
