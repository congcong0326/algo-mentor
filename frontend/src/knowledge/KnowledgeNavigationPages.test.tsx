import { act, cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import KnowledgeCardPage from './KnowledgeCardPage';
import KnowledgeArticlePage from './KnowledgeArticlePage';
import KnowledgeNodeCardsPage from './KnowledgeNodeCardsPage';
import KnowledgeOutlinePage from './KnowledgeOutlinePage';
import KnowledgeTopicPage from './KnowledgeTopicPage';
import { knowledgeApi } from '../services/knowledge';
import type { KnowledgeArticle, KnowledgeCard, KnowledgeNode } from '../types/knowledge';
import { withKnowledgeOutlineHistoryState } from '../app/navigation';

vi.mock('../i18n/I18nProvider', () => ({ useI18n: () => ({ resources: { nav: { knowledge: '知识库' }, reviewCenter: { ratingLabels: { AGAIN: '不会', HARD: '困难', GOOD: '记得', EASY: '轻松' } } } }) }));

const topic: KnowledgeNode = { id: 10, title: 'Java', subtreeCardCount: 1, subtreeEnrolledCardCount: 0, directCardCount: 0, directArticleCount: 0 };
const leaf: KnowledgeNode = { id: 11, title: '基础语法', subtreeCardCount: 1, subtreeEnrolledCardCount: 0, directCardCount: 1, directArticleCount: 0 };
const card: KnowledgeCard = { slug: 'pass-by-value', outlineNodeId: 11, question: 'Java 是值传递吗？', tags: ['Java'], answerMarkdown: '是值传递。', learningState: { enrolled: false, isDue: false } };
const article: KnowledgeArticle = { id: 21, outlineNodeId: 11, title: '理解值传递', bodyMarkdown: '## 参数传递\n\n文章正文。' };
const page = <T,>(items: T[]) => ({ items, total: items.length, page: 1, pageSize: 100 });

beforeEach(() => {
  window.history.replaceState({}, '', '/knowledge');
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

  it('大纲页按需展开直属卡片，收起再展开复用列表，目录仍可独立切换', async () => {
    const navigate = vi.fn();
    render(<KnowledgeOutlinePage topicId={10} onNavigate={navigate} />);
    expect(await screen.findByRole('button', { name: /基础语法/ })).toBeInTheDocument();
    expect(screen.queryByText('Java 是值传递吗？')).not.toBeInTheDocument();
    expect(knowledgeApi.cards).not.toHaveBeenCalled();
    fireEvent.click(screen.getByRole('button', { name: /基础语法/ }));
    expect(await screen.findByText('Java 是值传递吗？')).toBeInTheDocument();
    expect(navigate).toHaveBeenCalledWith('/knowledge/topics/10?open=10&cards=11', { replace: true });
    expect(screen.queryByText('是值传递。')).not.toBeInTheDocument();
    const cardButton = screen.getByRole('button', { name: /基础语法/ });
    expect(cardButton).toHaveAttribute('aria-expanded', 'true');
    fireEvent.click(cardButton);
    expect(screen.queryByRole('button', { name: '查看' })).not.toBeInTheDocument();
    fireEvent.click(cardButton);
    expect(screen.getByRole('button', { name: '查看' })).toBeInTheDocument();
    expect(knowledgeApi.cards).toHaveBeenCalledTimes(1);
    fireEvent.click(screen.getByRole('button', { name: '收起Java' }));
    expect(screen.getByRole('button', { name: '展开Java' })).toHaveAttribute('aria-expanded', 'false');
    expect(screen.queryByText('基础语法')).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Java' }));
    expect(screen.getByText('基础语法')).toBeInTheDocument();
  });

  it('从 URL 恢复多层大纲的展开状态', async () => {
    const branch = { ...leaf, directCardCount: 0, id: 11, title: '语言基础' };
    const nested = { ...leaf, id: 12, title: '值传递' };
    vi.mocked(knowledgeApi.tree).mockResolvedValue({ node: topic, children: [{ node: branch, children: [{ node: nested, children: [] }] }] });
    render(<KnowledgeOutlinePage topicId={10} search="?open=10%2C11" onNavigate={vi.fn()} />);
    expect(await screen.findByRole('button', { name: '查看值传递的卡片（1张）' })).toBeInTheDocument();
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
    expect(navigate).toHaveBeenLastCalledWith('/knowledge/topics/10?open=10&articles=10', { replace: true });
    fireEvent.click(within(root).getByRole('button', { name: /卡片/ }));
    expect(navigate).toHaveBeenLastCalledWith('/knowledge/topics/10?open=10&cards=10&articles=10', { replace: true });
    expect(within(screen.getByLabelText('基础语法的内容')).getAllByRole('button')).toHaveLength(1);
    const reading = screen.getByLabelText('阅读专题的内容');
    expect(within(reading).queryByRole('button', { name: /卡片/ })).not.toBeInTheDocument();
    fireEvent.click(within(reading).getByRole('button', { name: /文章/ }));
    expect(navigate).toHaveBeenLastCalledWith('/knowledge/topics/10?open=10&cards=10&articles=10%2C12', { replace: true });
    expect(screen.queryByLabelText('空目录的内容')).not.toBeInTheDocument();
    expect(screen.queryByLabelText('仅含下级卡片的内容')).not.toBeInTheDocument();
    expect(screen.getByText('暂无内容')).toBeInTheDocument();
  });

  it('卡片子节点直接查看详情并保存来源大纲与滚动位置', async () => {
    const navigate = vi.fn();
    render(<KnowledgeOutlinePage topicId={10} search="?open=10&cards=11" onNavigate={navigate} />);
    fireEvent.click(await screen.findByRole('button', { name: '查看' }));
    expect(navigate).toHaveBeenCalledWith('/knowledge/cards/pass-by-value', {
      state: { knowledgeOutline: { topicId: 10, scrollY: window.scrollY, returnPath: '/knowledge/topics/10?open=10&cards=11' } },
    });
  });

  it('文章按需展开为子节点，收起复用缓存，查看保留两类内容的展开状态', async () => {
    const second = { ...article, id: 22, title: '对象引用' };
    vi.mocked(knowledgeApi.tree).mockResolvedValue({ node: topic, children: [{ node: { ...leaf, directArticleCount: 2 }, children: [] }] });
    vi.mocked(knowledgeApi.articles).mockResolvedValue(page([article, second]));
    const navigate = vi.fn();
    render(<KnowledgeOutlinePage topicId={10} search="?open=10&cards=11" onNavigate={navigate} />);
    const toggle = await screen.findByRole('button', { name: '查看基础语法的文章（2篇）' });
    expect(knowledgeApi.articles).not.toHaveBeenCalled();
    fireEvent.click(toggle);
    expect(await screen.findByText('对象引用')).toBeInTheDocument();
    expect(knowledgeApi.article).not.toHaveBeenCalled();
    expect(screen.queryByText('文章正文。')).not.toBeInTheDocument();
    expect(navigate).toHaveBeenLastCalledWith('/knowledge/topics/10?open=10&cards=11&articles=11', { replace: true });
    fireEvent.click(toggle);
    expect(screen.getByRole('button', { name: '查看' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '查看基础语法的卡片（1张）' })).toHaveAttribute('aria-expanded', 'true');
    fireEvent.click(toggle);
    expect(knowledgeApi.articles).toHaveBeenCalledTimes(1);
    fireEvent.click(within(screen.getByLabelText('对象引用的操作')).getByRole('button', { name: '查看' }));
    expect(navigate).toHaveBeenLastCalledWith('/knowledge/nodes/11/articles?open=10&cards=11&articles=11&article=22', {
      state: { knowledgeOutline: { topicId: 10, scrollY: window.scrollY, returnPath: '/knowledge/topics/10?open=10&cards=11&articles=11' } },
    });
  });

  it('文章完整读取多页并支持加载失败原位重试', async () => {
    const second = { ...article, id: 22, title: '第二页文章' };
    vi.mocked(knowledgeApi.tree).mockResolvedValue({ node: { ...topic, directArticleCount: 2 }, children: [] });
    vi.mocked(knowledgeApi.articles).mockRejectedValueOnce(new Error('文章列表失败'))
      .mockResolvedValueOnce({ items: [article], total: 2, page: 1, pageSize: 1 })
      .mockResolvedValueOnce({ items: [second], total: 2, page: 2, pageSize: 1 });
    render(<KnowledgeOutlinePage topicId={10} search="?open=10&articles=10" onNavigate={vi.fn()} />);
    expect(await screen.findByRole('alert')).toHaveTextContent('文章列表失败');
    fireEvent.click(screen.getByRole('button', { name: '重试' }));
    expect(await screen.findByText('第二页文章')).toBeInTheDocument();
    expect(knowledgeApi.articles).toHaveBeenLastCalledWith(10, 2);
    expect(knowledgeApi.cards).not.toHaveBeenCalled();
    expect(knowledgeApi.article).not.toHaveBeenCalled();
  });

  it('文章列表收起再展开时忽略旧请求的迟到结果', async () => {
    let resolveFirst!: (value: ReturnType<typeof page<KnowledgeArticle>>) => void;
    vi.mocked(knowledgeApi.tree).mockResolvedValue({ node: { ...topic, directArticleCount: 1 }, children: [] });
    vi.mocked(knowledgeApi.articles).mockReturnValueOnce(new Promise((resolve) => { resolveFirst = resolve; }))
      .mockResolvedValueOnce(page([{ ...article, id: 22, title: '新文章' }]));
    render(<KnowledgeOutlinePage topicId={10} search="?open=10&articles=10" onNavigate={vi.fn()} />);
    await screen.findByText('正在加载文章…');
    const toggle = screen.getByRole('button', { name: '查看Java的文章（1篇）' });
    fireEvent.click(toggle);
    fireEvent.click(toggle);
    expect(await screen.findByText('新文章')).toBeInTheDocument();
    await act(async () => { resolveFirst(page([article])); });
    expect(screen.queryByText('理解值传递')).not.toBeInTheDocument();
  });

  it('恢复大纲滚动时等待文章和卡片都加载完成', async () => {
    let resolveCards!: (value: ReturnType<typeof page<KnowledgeCard>>) => void;
    let resolveArticles!: (value: ReturnType<typeof page<KnowledgeArticle>>) => void;
    vi.mocked(knowledgeApi.tree).mockResolvedValue({ node: topic, children: [{ node: { ...leaf, directArticleCount: 1 }, children: [] }] });
    vi.mocked(knowledgeApi.cards).mockReturnValue(new Promise((resolve) => { resolveCards = resolve; }));
    vi.mocked(knowledgeApi.articles).mockReturnValue(new Promise((resolve) => { resolveArticles = resolve; }));
    const scrollTo = vi.spyOn(window, 'scrollTo').mockImplementation(() => {});
    vi.spyOn(window, 'requestAnimationFrame').mockImplementation((callback) => { callback(0); return 1; });
    window.history.replaceState(withKnowledgeOutlineHistoryState({}, 10, 640), '', '/knowledge/topics/10?open=10&cards=11&articles=11');
    render(<KnowledgeOutlinePage topicId={10} search="?open=10&cards=11&articles=11" onNavigate={vi.fn()} />);
    await screen.findByText('正在加载文章…');
    await act(async () => { resolveCards(page([card])); });
    expect(scrollTo).not.toHaveBeenCalled();
    await act(async () => { resolveArticles(page([article])); });
    expect(scrollTo).toHaveBeenCalledWith({ top: 640, behavior: 'instant' });
  });

  it('阅读页打开指定文章并返回含文章和卡片展开项的大纲', async () => {
    const second = { ...article, id: 22, title: '对象引用', bodyMarkdown: '选中的第二篇正文' };
    vi.mocked(knowledgeApi.articles).mockResolvedValue(page([article, second]));
    vi.mocked(knowledgeApi.article).mockImplementation(async (id) => id === 22 ? second : article);
    const navigate = vi.fn();
    const { rerender } = render(<KnowledgeArticlePage nodeId={11} search="?open=10&cards=11&articles=11&article=22" onNavigate={navigate} />);
    expect(await screen.findByText('选中的第二篇正文')).toBeInTheDocument();
    expect(knowledgeApi.article).toHaveBeenCalledTimes(1);
    expect(knowledgeApi.article).toHaveBeenCalledWith(22);
    fireEvent.click(screen.getByRole('button', { name: '理解值传递' }));
    expect(await screen.findByText('文章正文。')).toBeInTheDocument();
    expect(navigate).toHaveBeenLastCalledWith('/knowledge/nodes/11/articles?open=10&cards=11&articles=11&article=21', { replace: true });
    rerender(<KnowledgeArticlePage nodeId={11} search="?open=10&cards=11&articles=11&article=21" onNavigate={navigate} />);
    expect(knowledgeApi.article).toHaveBeenCalledTimes(2);
    expect(knowledgeApi.articles).toHaveBeenCalledTimes(1);
    fireEvent.click(screen.getByRole('button', { name: '返回大纲' }));
    expect(navigate).toHaveBeenCalledWith('/knowledge/topics/10?open=10&cards=11&articles=11');
  });

  it('指定文章不属于当前知识点时提示错误，列表失败也可返回来源大纲', async () => {
    vi.mocked(knowledgeApi.articles).mockResolvedValueOnce(page([article])).mockRejectedValueOnce(new Error('列表失败'));
    const returnPath = '/knowledge/topics/10?open=10&cards=11&articles=11';
    window.history.replaceState(withKnowledgeOutlineHistoryState({}, 10, 640, returnPath), '', '/knowledge/nodes/11/articles?article=99');
    const navigate = vi.fn();
    const { rerender } = render(<KnowledgeArticlePage nodeId={11} search="?article=99" onNavigate={navigate} />);
    expect(await screen.findByRole('alert')).toHaveTextContent('该文章不属于当前知识点');
    expect(knowledgeApi.article).not.toHaveBeenCalled();
    rerender(<KnowledgeArticlePage nodeId={12} search="?article=22" onNavigate={navigate} />);
    expect(await screen.findByRole('alert')).toHaveTextContent('列表失败');
    fireEvent.click(screen.getByRole('button', { name: '返回大纲' }));
    expect(navigate).toHaveBeenLastCalledWith(returnPath, expect.any(Object));
  });

  it('直属卡片超过一页时完整展示，父节点与子节点的卡片互不混淆', async () => {
    const second = { ...card, slug: 'second-card', question: '第二页问题' };
    vi.mocked(knowledgeApi.tree).mockResolvedValue({ node: { ...topic, directCardCount: 2 }, children: [{ node: leaf, children: [] }] });
    vi.mocked(knowledgeApi.cards).mockImplementation(async (id, requestedPage) => ({ items: id === 10 && requestedPage === 2 ? [second] : [card], total: id === 10 ? 2 : 1, page: requestedPage || 1, pageSize: 1 }));
    render(<KnowledgeOutlinePage topicId={10} search="?open=10&cards=10" onNavigate={vi.fn()} />);
    expect(await screen.findByText('第二页问题')).toBeInTheDocument();
    expect(knowledgeApi.cards).toHaveBeenNthCalledWith(1, 10, 1);
    expect(knowledgeApi.cards).toHaveBeenNthCalledWith(2, 10, 2);
    expect(screen.getByRole('button', { name: /基础语法/ })).toHaveAttribute('aria-expanded', 'false');
    expect(screen.getAllByRole('button', { name: '查看' })).toHaveLength(2);
  });

  it('大纲内加入、移除复习；失败保留状态并可重新操作', async () => {
    vi.mocked(knowledgeApi.setEnrollment).mockRejectedValueOnce(new Error('更新失败'));
    render(<KnowledgeOutlinePage topicId={10} search="?open=10&cards=11" onNavigate={vi.fn()} />);
    const add = await screen.findByRole('button', { name: '加入复习' });
    const remove = screen.getByRole('button', { name: '移除复习' });
    expect(remove).toBeDisabled();
    fireEvent.click(add);
    expect(await screen.findByRole('alert')).toHaveTextContent('更新失败');
    expect(add).toBeEnabled();
    fireEvent.click(add);
    await waitFor(() => expect(remove).toBeEnabled());
    expect(add).toBeDisabled();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
    fireEvent.click(remove);
    await waitFor(() => expect(add).toBeEnabled());
    expect(remove).toBeDisabled();
    expect(knowledgeApi.review).not.toHaveBeenCalled();
  });

  it('大纲内卡片加载失败可以原位重试', async () => {
    vi.mocked(knowledgeApi.cards).mockRejectedValueOnce(new Error('卡片加载失败'));
    render(<KnowledgeOutlinePage topicId={10} search="?open=10&cards=11" onNavigate={vi.fn()} />);
    expect(await screen.findByRole('alert')).toHaveTextContent('卡片加载失败');
    fireEvent.click(screen.getByRole('button', { name: '重试' }));
    expect(await screen.findByRole('button', { name: '查看' })).toBeInTheDocument();
  });

  it('加载中收起再展开时忽略旧请求的迟到结果', async () => {
    let resolveFirst!: (value: ReturnType<typeof page<KnowledgeCard>>) => void;
    const newer = { ...card, slug: 'new-card', question: '最新卡片' };
    vi.mocked(knowledgeApi.cards).mockReturnValueOnce(new Promise((resolve) => { resolveFirst = resolve; })).mockResolvedValueOnce(page([newer]));
    render(<KnowledgeOutlinePage topicId={10} search="?open=10&cards=11" onNavigate={vi.fn()} />);
    await screen.findByText('正在加载卡片…');
    const toggle = screen.getByRole('button', { name: /基础语法/ });
    fireEvent.click(toggle);
    fireEvent.click(toggle);
    expect(await screen.findByText('最新卡片')).toBeInTheDocument();
    await act(async () => { resolveFirst(page([card])); });
    expect(screen.queryByText('Java 是值传递吗？')).not.toBeInTheDocument();
    expect(screen.getByText('最新卡片')).toBeInTheDocument();
  });

  it('恢复大纲时等待卡片请求完成才还原滚动位置', async () => {
    let resolveCards!: (value: ReturnType<typeof page<KnowledgeCard>>) => void;
    vi.mocked(knowledgeApi.cards).mockReturnValue(new Promise((resolve) => { resolveCards = resolve; }));
    const scrollTo = vi.spyOn(window, 'scrollTo').mockImplementation(() => {});
    vi.spyOn(window, 'requestAnimationFrame').mockImplementation((callback) => { callback(0); return 1; });
    window.history.replaceState(withKnowledgeOutlineHistoryState({}, 10, 640), '', '/knowledge/topics/10?open=10&cards=11');
    render(<KnowledgeOutlinePage topicId={10} search="?open=10&cards=11" onNavigate={vi.fn()} />);
    await screen.findByText('正在加载卡片…');
    expect(scrollTo).not.toHaveBeenCalled();
    await act(async () => { resolveCards(page([card])); });
    expect(scrollTo).toHaveBeenCalledWith({ top: 640, behavior: 'instant' });
  });

  it('从大纲打开的详情及关联卡片均可返回原大纲，加载失败也保留返回入口', async () => {
    const navigate = vi.fn();
    const returnPath = '/knowledge/topics/10?open=10&cards=11';
    window.history.replaceState(withKnowledgeOutlineHistoryState({}, 10, 640, returnPath), '', '/knowledge/cards/pass-by-value');
    vi.mocked(knowledgeApi.card).mockResolvedValueOnce({ ...card, relations: [{ type: 'related', slug: 'other-card', question: '关联知识' }] }).mockRejectedValueOnce(new Error('详情失败'));
    const { rerender } = render(<KnowledgeCardPage slug={card.slug} onNavigate={navigate} />);
    fireEvent.click(await screen.findByRole('button', { name: '关联知识' }));
    expect(navigate).toHaveBeenLastCalledWith('/knowledge/cards/other-card', expect.any(Object));
    rerender(<KnowledgeCardPage slug="other-card" onNavigate={navigate} />);
    await screen.findByRole('alert');
    fireEvent.click(screen.getByRole('button', { name: '返回大纲' }));
    expect(navigate).toHaveBeenLastCalledWith(returnPath, expect.any(Object));
  });

  it('卡片列表返回时保留大纲的展开上下文', async () => {
    const navigate = vi.fn();
    render(<KnowledgeNodeCardsPage nodeId={11} search="?open=10%2C11" onNavigate={navigate} />);
    await screen.findByRole('heading', { name: 'Java 是值传递吗？' });
    fireEvent.click(screen.getByRole('button', { name: '返回大纲' }));
    expect(navigate).toHaveBeenCalledWith('/knowledge/topics/10?open=10%2C11');
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
    expect(navigate).toHaveBeenLastCalledWith('/knowledge/cards/other-card?page=2&q=Java');
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
