import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import KnowledgePage from './KnowledgePage';
import KnowledgeReviewCenterPage from './KnowledgeReviewCenterPage';
import { knowledgeApi } from './services/knowledge';
import type { KnowledgeCard, KnowledgeNode } from './types/knowledge';

vi.mock('./i18n/I18nProvider', () => ({ useI18n: () => ({ resources: { nav: { knowledge: '知识库' }, reviewCenter: { ratingLabels: { AGAIN: '不会', HARD: '困难', GOOD: '记得', EASY: '轻松' }, ratingDescriptions: {}, ratingAriaLabel: '复习评级' } } }) }));
const topic: KnowledgeNode = { id: 10, title: 'Java', subtreeCardCount: 2, subtreeEnrolledCardCount: 0, directCardCount: 0, directArticleCount: 0 };
const child: KnowledgeNode = { ...topic, id: 11, title: '基础', directCardCount: 1, directArticleCount: 1 };
const card: KnowledgeCard = { slug: 'java-pass-by-value', outlineNodeId: 11, question: 'Java为什么只有值传递', tags: ['Java基础'], answerMarkdown: '核心测试答案', explanationMarkdown: '## 任意详情标题\n\n详细测试解释', relations: [{ type: 'related', slug: 'hashmap-collision', question: 'HashMap如何处理哈希冲突' }], learningState: { enrolled: false, isDue: false } };
const page = <T,>(items: T[]) => ({ items, total: items.length, page: 1, pageSize: 100 });

beforeEach(() => {
  vi.spyOn(knowledgeApi, 'topics').mockResolvedValue({ items: [topic] });
  vi.spyOn(knowledgeApi, 'tree').mockResolvedValue({ node: topic, children: [{ node: child, children: [] }] });
  vi.spyOn(knowledgeApi, 'cards').mockImplementation(async (id) => page(id === 11 ? [card] : []));
  vi.spyOn(knowledgeApi, 'articles').mockImplementation(async (id) => page(id === 11 ? [{ id: 20, outlineNodeId: 11, title: '对象与引用' }] : []));
  vi.spyOn(knowledgeApi, 'card').mockImplementation(async (slug) => slug === card.slug ? card : { ...card, slug, question: 'HashMap如何处理哈希冲突', relations: [] });
  vi.spyOn(knowledgeApi, 'article').mockResolvedValue({ id: 20, outlineNodeId: 11, title: '对象与引用', bodyMarkdown: '## 文章章节\n\n独立文章正文' });
  vi.spyOn(knowledgeApi, 'review').mockResolvedValue({ id: 1, cardSlug: card.slug, clientAttemptId: 'attempt', rating: 'GOOD', reviewedAt: '2026-09-16T00:00:00Z', dueAt: '2026-09-17T00:00:00Z', firstReview: true, duplicate: false });
  vi.spyOn(knowledgeApi, 'summary').mockResolvedValue({ enrolledCount: 1, dueCount: 1 });
  vi.spyOn(knowledgeApi, 'queue').mockResolvedValue(page([card]));
});
afterEach(() => { cleanup(); vi.restoreAllMocks(); });
async function openBasics() {
  render(<KnowledgePage />);
  fireEvent.click(await screen.findByRole('button', { name: /^Java/ }));
  fireEvent.click(await screen.findByRole('button', { name: /^基础/ }));
}

describe('知识库目录内容', () => {
  it('通过大纲读卡，分别渲染核心回答和自由详情，并按 slug 跳转关联', async () => {
    await openBasics();
    fireEvent.click(await screen.findByRole('button', { name: /^Java为什么只有值传递/ }));
    expect(await screen.findByText('Java基础')).toBeInTheDocument();
    expect(screen.queryByText('核心测试答案')).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '显示答案' }));
    expect(screen.getByText('核心测试答案')).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: '任意详情标题' })).toBeInTheDocument();
    expect(screen.getByText('详细测试解释')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'HashMap如何处理哈希冲突' }));
    await screen.findByRole('heading', { name: 'HashMap如何处理哈希冲突' });
    expect(knowledgeApi.card).toHaveBeenLastCalledWith('hashmap-collision');
    expect(screen.queryByText('核心测试答案')).not.toBeInTheDocument();
  });
  it('展示节点文章且文章不出现评价操作', async () => {
    await openBasics();
    fireEvent.click(await screen.findByRole('button', { name: '对象与引用' }));
    expect(await screen.findByText('独立文章正文')).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: '文章章节' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '记得' })).not.toBeInTheDocument();
  });
  it('旧知识库复习入口跳转到统一复习列表', () => {
    const onNavigate = vi.fn();
    render(<KnowledgeReviewCenterPage onNavigate={onNavigate} />);
    expect(onNavigate).toHaveBeenCalledWith('/mistakes?mode=knowledge', { replace: true });
    expect(knowledgeApi.queue).not.toHaveBeenCalled();
  });
});
