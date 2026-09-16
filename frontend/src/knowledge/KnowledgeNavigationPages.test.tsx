import { act, cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import KnowledgeCardPage from './KnowledgeCardPage';
import KnowledgeArticlePage from './KnowledgeArticlePage';
import KnowledgeNodeCardsPage from './KnowledgeNodeCardsPage';
import KnowledgeOutlinePage from './KnowledgeOutlinePage';
import KnowledgeTopicPage from './KnowledgeTopicPage';
import { knowledgeApi } from '../services/knowledge';
import type { KnowledgeArticle, KnowledgeCard, KnowledgeNode } from '../types/knowledge';

vi.mock('../i18n/I18nProvider', () => ({ useI18n: () => ({ resources: { nav: { knowledge: '知识库' }, reviewCenter: { ratingLabels: { AGAIN: '不会', HARD: '困难', GOOD: '记得', EASY: '轻松' } } } }) }));

const topic: KnowledgeNode = { id: 10, title: 'Java', subtreeCardCount: 1, subtreeEnrolledCardCount: 0, directCardCount: 0, directArticleCount: 0 };
const leaf: KnowledgeNode = { id: 11, title: '基础语法', subtreeCardCount: 1, subtreeEnrolledCardCount: 0, directCardCount: 1, directArticleCount: 0 };
const card: KnowledgeCard = { slug: 'pass-by-value', outlineNodeId: 11, question: 'Java 是值传递吗？', tags: ['Java'], answerMarkdown: '是值传递。', learningState: { enrolled: false, isDue: false } };
const article: KnowledgeArticle = { id: 21, outlineNodeId: 11, title: '理解值传递', bodyMarkdown: '## 参数传递\n\n文章正文。' };
const page = <T,>(items: T[]) => ({ items, total: items.length, page: 1, pageSize: 100 });

beforeEach(() => {
  vi.spyOn(knowledgeApi, 'topics').mockResolvedValue({ items: [topic] });
  vi.spyOn(knowledgeApi, 'tree').mockResolvedValue({ node: topic, children: [{ node: leaf, children: [] }] });
  vi.spyOn(knowledgeApi, 'node').mockResolvedValue({ node: leaf, breadcrumbs: [{ id: 10, title: 'Java', slug: 'java' }, { id: 11, title: '基础语法', slug: 'basics' }], children: [] });
  vi.spyOn(knowledgeApi, 'cards').mockResolvedValue(page([card]));
  vi.spyOn(knowledgeApi, 'articles').mockResolvedValue(page([]));
  vi.spyOn(knowledgeApi, 'article').mockResolvedValue(article);
  vi.spyOn(knowledgeApi, 'card').mockResolvedValue(card);
  vi.spyOn(knowledgeApi, 'review').mockResolvedValue({ id: 1, cardSlug: card.slug, clientAttemptId: 'attempt', rating: 'GOOD', reviewedAt: '2026-09-16T00:00:00Z', dueAt: '2026-09-17T00:00:00Z', firstReview: true, duplicate: false });
  vi.spyOn(knowledgeApi, 'setEnrollment').mockImplementation(async (_, enrolled) => ({ enrolled, isDue: enrolled }));
});

afterEach(() => { cleanup(); vi.restoreAllMocks(); });

describe('知识库分层导航', () => {
  it('从主题卡片进入独立大纲页', async () => {
    const navigate = vi.fn();
    render(<KnowledgeTopicPage onNavigate={navigate} />);
    fireEvent.click(await screen.findByRole('button', { name: /Java/ }));
    expect(navigate).toHaveBeenCalledWith('/knowledge/topics/10');
  });

  it('大纲页通过独立卡片按钮进入列表，目录可收起与展开', async () => {
    const navigate = vi.fn();
    render(<KnowledgeOutlinePage topicId={10} onNavigate={navigate} />);
    expect(await screen.findByRole('button', { name: /基础语法/ })).toBeInTheDocument();
    expect(screen.queryByText('Java 是值传递吗？')).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: /基础语法/ }));
    expect(navigate).toHaveBeenCalledWith('/knowledge/nodes/11/cards');
    fireEvent.click(screen.getByRole('button', { name: '收起Java' }));
    expect(screen.getByRole('button', { name: '展开Java' })).toHaveAttribute('aria-expanded', 'false');
    expect(screen.queryByText('基础语法')).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Java' }));
    expect(screen.getByText('基础语法')).toBeInTheDocument();
  });

  it('按直属内容区分文章、卡片和空节点，父节点也可提供两个入口', async () => {
    vi.mocked(knowledgeApi.tree).mockResolvedValue({
      node: { ...topic, directArticleCount: 1, directCardCount: 2 },
      children: [
        { node: leaf, children: [] },
        { node: { ...leaf, id: 12, title: '阅读专题', directCardCount: 0, directArticleCount: 1 }, children: [] },
        { node: { ...topic, id: 13, title: '空目录', subtreeCardCount: 0 }, children: [] },
        { node: { ...topic, id: 14, title: '仅含下级卡片' }, children: [{ node: { ...leaf, id: 15 }, children: [] }] },
      ],
    });
    const navigate = vi.fn();
    render(<KnowledgeOutlinePage topicId={10} onNavigate={navigate} />);
    const root = await screen.findByLabelText('Java的内容');
    expect(within(root).getAllByRole('button')).toHaveLength(2);
    fireEvent.click(within(root).getByRole('button', { name: /文章/ }));
    expect(navigate).toHaveBeenLastCalledWith('/knowledge/nodes/10/articles');
    fireEvent.click(within(root).getByRole('button', { name: /卡片/ }));
    expect(navigate).toHaveBeenLastCalledWith('/knowledge/nodes/10/cards');
    expect(within(screen.getByLabelText('基础语法的内容')).getAllByRole('button')).toHaveLength(1);
    const reading = screen.getByLabelText('阅读专题的内容');
    expect(within(reading).queryByRole('button', { name: /卡片/ })).not.toBeInTheDocument();
    fireEvent.click(within(reading).getByRole('button', { name: /文章/ }));
    expect(navigate).toHaveBeenLastCalledWith('/knowledge/nodes/12/articles');
    expect(screen.queryByLabelText('空目录的内容')).not.toBeInTheDocument();
    expect(screen.queryByLabelText('仅含下级卡片的内容')).not.toBeInTheDocument();
    expect(screen.getByText('暂无内容')).toBeInTheDocument();
  });

  it('文章页直接阅读正文并返回大纲，不请求卡片', async () => {
    vi.mocked(knowledgeApi.articles).mockResolvedValue(page([article]));
    const navigate = vi.fn();
    render(<KnowledgeArticlePage nodeId={11} onNavigate={navigate} />);
    expect(await screen.findByRole('heading', { name: '参数传递' })).toBeInTheDocument();
    expect(screen.queryByLabelText('文章目录')).not.toBeInTheDocument();
    expect(knowledgeApi.cards).not.toHaveBeenCalled();
    fireEvent.click(screen.getByRole('button', { name: '返回大纲' }));
    expect(navigate).toHaveBeenCalledWith('/knowledge/topics/10');
  });

  it('多篇文章切换失败可重试当前文章', async () => {
    const second = { ...article, id: 22, title: '对象引用', bodyMarkdown: '第二篇正文' };
    vi.mocked(knowledgeApi.articles).mockResolvedValue(page([article, second]));
    vi.mocked(knowledgeApi.article).mockResolvedValueOnce(article).mockRejectedValueOnce(new Error('文章加载失败')).mockResolvedValueOnce(second);
    render(<KnowledgeArticlePage nodeId={11} onNavigate={vi.fn()} />);
    await screen.findByText('文章正文。');
    fireEvent.click(screen.getByRole('button', { name: '对象引用' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('文章加载失败');
    expect(screen.queryByText('文章正文。')).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '重试' }));
    expect(await screen.findByText('第二篇正文')).toBeInTheDocument();
    expect(knowledgeApi.article).toHaveBeenLastCalledWith(22);
    expect(screen.getByRole('button', { name: '对象引用' })).toHaveAttribute('aria-current', 'page');
  });

  it('快速切换文章时忽略较早请求的迟到响应', async () => {
    const second = { ...article, id: 22, title: '对象引用', bodyMarkdown: '第二篇正文' };
    let resolveFirst!: (value: KnowledgeArticle) => void;
    vi.mocked(knowledgeApi.articles).mockResolvedValue(page([article, second]));
    vi.mocked(knowledgeApi.article).mockReturnValueOnce(new Promise((resolve) => { resolveFirst = resolve; })).mockResolvedValueOnce(second);
    render(<KnowledgeArticlePage nodeId={11} onNavigate={vi.fn()} />);
    fireEvent.click(await screen.findByRole('button', { name: '对象引用' }));
    expect(await screen.findByText('第二篇正文')).toBeInTheDocument();
    await act(async () => { resolveFirst(article); });
    expect(screen.getByText('第二篇正文')).toBeInTheDocument();
    expect(screen.queryByText('文章正文。')).not.toBeInTheDocument();
  });

  it('无文章时显示空状态，不请求详情', async () => {
    render(<KnowledgeArticlePage nodeId={11} onNavigate={vi.fn()} />);
    expect(await screen.findByText('该节点暂无文章。')).toBeInTheDocument();
    expect(knowledgeApi.article).not.toHaveBeenCalled();
  });

  it('叶子页列出卡片，并可回到所属主题大纲', async () => {
    const navigate = vi.fn();
    render(<KnowledgeNodeCardsPage nodeId={11} onNavigate={navigate} />);
    await screen.findByRole('heading', { name: 'Java 是值传递吗？' });
    fireEvent.click(screen.getByRole('button', { name: '查看' }));
    expect(navigate).toHaveBeenCalledWith('/knowledge/cards/pass-by-value');
    expect(screen.queryByText('是值传递。')).not.toBeInTheDocument();
    expect(knowledgeApi.articles).not.toHaveBeenCalled();
    fireEvent.click(screen.getByRole('button', { name: '返回大纲' }));
    expect(navigate).toHaveBeenCalledWith('/knowledge/topics/10');
  });

  it('按页加载并在查看详情时保留当前搜索和页码', async () => {
    vi.mocked(knowledgeApi.cards).mockResolvedValue({ items: [card], total: 41, page: 2, pageSize: 20 });
    const navigate = vi.fn();
    render(<KnowledgeNodeCardsPage nodeId={11} search="?page=2&q=Java" onNavigate={navigate} />);
    await screen.findByText('第 2 / 3 页');
    expect(knowledgeApi.cards).toHaveBeenCalledWith(11, 2, 20, 'Java');
    fireEvent.click(screen.getByRole('button', { name: '下一页' }));
    expect(navigate).toHaveBeenLastCalledWith('/knowledge/nodes/11/cards?page=3&q=Java');
    fireEvent.click(screen.getByRole('button', { name: '查看' }));
    expect(navigate).toHaveBeenLastCalledWith('/knowledge/cards/pass-by-value?page=2&q=Java');
    fireEvent.change(screen.getByRole('textbox', { name: '搜索知识卡或标签' }), { target: { value: '集合' } });
    fireEvent.click(screen.getByRole('button', { name: '搜索' }));
    expect(navigate).toHaveBeenLastCalledWith('/knowledge/nodes/11/cards?q=%E9%9B%86%E5%90%88');
  });

  it('加入和移除复习更新按钮状态，且不会代替用户评级', async () => {
    render(<KnowledgeNodeCardsPage nodeId={11} onNavigate={vi.fn()} />);
    const add = await screen.findByRole('button', { name: '加入复习' });
    const remove = screen.getByRole('button', { name: '移除复习' });
    expect(remove).toBeDisabled();
    fireEvent.click(add);
    await waitFor(() => expect(remove).toBeEnabled());
    expect(add).toBeDisabled();
    expect(screen.getByText('复习中')).toBeInTheDocument();
    fireEvent.click(remove);
    await waitFor(() => expect(add).toBeEnabled());
    expect(remove).toBeDisabled();
    expect(knowledgeApi.setEnrollment).toHaveBeenNthCalledWith(1, card.slug, true);
    expect(knowledgeApi.setEnrollment).toHaveBeenNthCalledWith(2, card.slug, false);
    expect(knowledgeApi.review).not.toHaveBeenCalled();
  });

  it('加入失败保留原状态，支持重新操作', async () => {
    vi.mocked(knowledgeApi.setEnrollment).mockRejectedValueOnce(new Error('更新失败'));
    render(<KnowledgeNodeCardsPage nodeId={11} onNavigate={vi.fn()} />);
    fireEvent.click(await screen.findByRole('button', { name: '加入复习' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('更新失败');
    expect(screen.getByRole('button', { name: '加入复习' })).toBeEnabled();
    expect(screen.getByRole('button', { name: '移除复习' })).toBeDisabled();
    fireEvent.click(screen.getByRole('button', { name: '加入复习' }));
    await waitFor(() => expect(screen.getByRole('button', { name: '移除复习' })).toBeEnabled());
  });

  it('卡片页直接展示全部 Markdown 与关联，并返回原分页', async () => {
    vi.mocked(knowledgeApi.card).mockResolvedValue({ ...card, explanationMarkdown: '## 详细解释\n\n- 列表内容\n\n```java\nint value = 1;\n```', relations: [{ type: 'related', slug: 'other-card', question: '关联知识' }] });
    const navigate = vi.fn();
    const { container } = render(<KnowledgeCardPage slug={card.slug} search="?page=2&q=Java" onNavigate={navigate} />);
    expect(await screen.findByText('是值传递。')).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: '详细解释' })).toBeInTheDocument();
    expect(screen.getByText('列表内容')).toBeInTheDocument();
    expect(container.querySelector('.review-problem-content .markdown-view pre code')).toHaveTextContent('int value = 1;');
    expect(screen.queryByRole('button', { name: '显示答案' })).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '返回卡片列表' }));
    expect(navigate).toHaveBeenLastCalledWith('/knowledge/nodes/11/cards?page=2&q=Java');
    fireEvent.click(screen.getByRole('button', { name: '关联知识' }));
    expect(navigate).toHaveBeenLastCalledWith('/knowledge/cards/other-card');
  });

  it('分页加载失败不展示旧页内容，重试后恢复', async () => {
    vi.mocked(knowledgeApi.cards).mockRejectedValueOnce(new Error('列表加载失败'));
    render(<KnowledgeNodeCardsPage nodeId={11} onNavigate={vi.fn()} />);
    expect(await screen.findByRole('alert')).toHaveTextContent('列表加载失败');
    expect(screen.queryByRole('button', { name: '查看' })).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '重试' }));
    expect(await screen.findByRole('button', { name: '查看' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '上一页' })).toBeDisabled();
    expect(screen.getByRole('button', { name: '下一页' })).toBeDisabled();
  });
});
