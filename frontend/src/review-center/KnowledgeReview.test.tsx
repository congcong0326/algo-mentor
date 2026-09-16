import { cleanup, fireEvent, render, screen, within } from '@testing-library/react';
import { useState } from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import KnowledgeReviewListPage from './KnowledgeReviewListPage';
import KnowledgeReviewSessionPage from './KnowledgeReviewSessionPage';
import { knowledgeApi } from '../services/knowledge';
import type { KnowledgeCard, KnowledgeReviewResult } from '../types/knowledge';

const card: KnowledgeCard = { slug: 'java-value', outlineNodeId: 11, question: 'Java 为什么只有值传递', tags: ['Java'], answerMarkdown: '核心答案', explanationMarkdown: '## 详细解释\n\n示例说明', learningState: { enrolled: true, isDue: true, phase: 'LEARNING', dueAt: '2026-09-01T00:00:00Z' } };
const result: KnowledgeReviewResult = { id: 1, cardSlug: card.slug, clientAttemptId: 'attempt', rating: 'GOOD', reviewedAt: '2026-09-16T00:00:00Z', dueAt: '2026-09-19T00:00:00Z', firstReview: true, duplicate: false };
const pageOf = (items: KnowledgeCard[]) => ({ items, total: items.length, page: 1, pageSize: 100 });

beforeEach(() => {
  vi.spyOn(knowledgeApi, 'reviewCards').mockResolvedValue(pageOf([card]));
  vi.spyOn(knowledgeApi, 'queue').mockResolvedValue(pageOf([card]));
  vi.spyOn(knowledgeApi, 'summary').mockResolvedValue({ enrolledCount: 1, dueCount: 1 });
  vi.spyOn(knowledgeApi, 'card').mockImplementation(async (slug) => ({ ...card, slug }));
  vi.spyOn(knowledgeApi, 'reviewPreview').mockResolvedValue({ cardSlug: card.slug, enrolled: true, timezone: 'UTC', asOf: '2026-09-16T00:00:00Z', options: [
    { rating: 'AGAIN', intervalDays: 0, dueAt: new Date(Date.now() + 60000).toISOString() },
    { rating: 'HARD', intervalDays: 1, dueAt: '2026-09-17T00:00:00Z' },
    { rating: 'GOOD', intervalDays: 3, dueAt: '2026-09-19T00:00:00Z' },
    { rating: 'EASY', intervalDays: 4, dueAt: '2026-09-20T00:00:00Z' },
  ] });
  vi.spyOn(knowledgeApi, 'review').mockResolvedValue(result);
  vi.spyOn(knowledgeApi, 'setEnrollment').mockResolvedValue({ enrolled: false, isDue: false });
});
afterEach(() => { cleanup(); vi.restoreAllMocks(); });

function ListHarness() {
  const [search, setSearch] = useState('?mode=knowledge');
  return <KnowledgeReviewListPage search={search} onNavigate={(path) => setSearch(new URL(path, 'http://localhost').search)} />;
}

describe('统一知识卡复习列表', () => {
  it('先展示列表，可看详情、关闭恢复焦点，再开始工作台和切换刷题', async () => {
    const onNavigate = vi.fn();
    render(<KnowledgeReviewListPage onNavigate={onNavigate} />);
    await screen.findByRole('heading', { name: card.question });
    expect(screen.getByRole('button', { name: '八股文' })).toHaveAttribute('aria-pressed', 'true');
    expect(screen.queryByRole('button', { name: '显示答案' })).not.toBeInTheDocument();
    expect(knowledgeApi.queue).not.toHaveBeenCalled();
    const trigger = screen.getByRole('button', { name: `查看复习卡详情 ${card.question}` });
    fireEvent.click(trigger);
    const dialog = screen.getByRole('dialog');
    expect(await within(dialog).findByText('核心答案')).toBeInTheDocument();
    expect(within(dialog).getByText('示例说明')).toBeInTheDocument();
    fireEvent.keyDown(dialog, { key: 'Escape' });
    expect(trigger).toHaveFocus();
    fireEvent.click(screen.getByRole('button', { name: '开始今日复习 1 题' }));
    expect(onNavigate).toHaveBeenLastCalledWith('/mistakes/review?mode=knowledge');
    fireEvent.click(screen.getByRole('button', { name: '刷题' }));
    expect(onNavigate).toHaveBeenLastCalledWith('/mistakes');
  });

  it('读取完整分页，搜索标签，筛选到期并分页显示', async () => {
    const cards = Array.from({ length: 12 }, (_, i) => ({ ...card, slug: `card-${i}`, question: `知识卡 ${i}`, tags: i === 11 ? ['集合'] : ['Java'], learningState: { ...card.learningState, isDue: i === 11 } }));
    vi.mocked(knowledgeApi.reviewCards).mockImplementation(async (page) => ({ items: page === 1 ? cards.slice(0, 10) : cards.slice(10), total: 12, page: page || 1, pageSize: 10 }));
    render(<ListHarness />);
    await screen.findByRole('heading', { name: '知识卡 0' });
    expect(knowledgeApi.reviewCards).toHaveBeenCalledWith(2);
    expect(screen.queryByRole('heading', { name: '知识卡 11' })).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '下一页' }));
    expect(screen.getByRole('heading', { name: '知识卡 11' })).toBeInTheDocument();
    fireEvent.change(screen.getByRole('textbox'), { target: { value: '集合' } });
    expect(screen.queryByRole('heading', { name: '知识卡 10' })).not.toBeInTheDocument();
    fireEvent.change(screen.getByRole('textbox'), { target: { value: '' } });
    fireEvent.click(screen.getByRole('checkbox', { name: '仅看待复习' }));
    expect(screen.getByRole('heading', { name: '知识卡 11' })).toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: '知识卡 0' })).not.toBeInTheDocument();
  });

  it('移出后刷新卡片和统计，失败时保留卡片', async () => {
    vi.mocked(knowledgeApi.setEnrollment).mockRejectedValueOnce(new Error('移出失败'));
    render(<KnowledgeReviewListPage onNavigate={vi.fn()} />);
    fireEvent.click(await screen.findByRole('button', { name: '移出复习' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('移出失败');
    expect(screen.getByRole('heading', { name: card.question })).toBeInTheDocument();
    vi.mocked(knowledgeApi.reviewCards).mockResolvedValue(pageOf([]));
    vi.mocked(knowledgeApi.summary).mockResolvedValue({ enrolledCount: 0, dueCount: 0 });
    fireEvent.click(screen.getByRole('button', { name: '移出复习' }));
    await screen.findByText(/暂无复习卡。在知识库/);
    expect(knowledgeApi.setEnrollment).toHaveBeenLastCalledWith(card.slug, false);
    expect(screen.getByRole('button', { name: '今日已完成' })).toBeDisabled();
  });

  it('列表错误不展示成功空态，可刷新恢复', async () => {
    vi.mocked(knowledgeApi.reviewCards).mockRejectedValueOnce(new Error('列表失败'));
    render(<KnowledgeReviewListPage onNavigate={vi.fn()} />);
    expect(await screen.findByRole('alert')).toHaveTextContent('列表失败');
    expect(screen.queryByText(/暂无复习卡。在知识库/)).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '刷新复习卡' }));
    expect(await screen.findByRole('heading', { name: card.question })).toBeInTheDocument();
  });
});

describe('统一知识卡复习工作台', () => {
  it('答案展开前禁用评级，评级后保留内容，最后一张也需下一题确认', async () => {
    const onNavigate = vi.fn();
    render(<KnowledgeReviewSessionPage onNavigate={onNavigate} search="?mode=knowledge&q=Java&page=2" />);
    await screen.findByRole('button', { name: '显示答案' });
    const good = screen.getByRole('button', { name: /良好，.*3 天后复习/ });
    expect(good).toBeDisabled();
    fireEvent.keyDown(window, { key: '3' });
    expect(knowledgeApi.review).not.toHaveBeenCalled();
    fireEvent.click(screen.getByRole('button', { name: '显示答案' }));
    expect(screen.getByText('核心答案')).toBeInTheDocument();
    fireEvent.keyDown(window, { key: '3' });
    expect(await screen.findByRole('region', { name: '复习确认结果' })).toHaveTextContent('下次复习');
    expect(screen.getByText('核心答案')).toBeInTheDocument();
    expect(good).toHaveClass('is-selected');
    expect(good).toBeDisabled();
    expect(screen.queryByRole('heading', { name: '今日待复习已完成' })).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '下一题' }));
    expect(screen.getByRole('heading', { name: '今日待复习已完成' })).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '返回复习中心' }));
    expect(onNavigate).toHaveBeenCalledWith('/mistakes?q=Java&page=2&mode=knowledge');
  });

  it('提交期间防重，失败后只重试同一评级和 UUID', async () => {
    let reject!: (error: Error) => void;
    vi.mocked(knowledgeApi.review).mockReturnValueOnce(new Promise((_, rejectFn) => { reject = rejectFn; }));
    render(<KnowledgeReviewSessionPage onNavigate={vi.fn()} />);
    fireEvent.click(await screen.findByRole('button', { name: '显示答案' }));
    fireEvent.click(screen.getByRole('button', { name: /^良好，/ }));
    fireEvent.click(screen.getByRole('button', { name: /^简单，/ }));
    fireEvent.keyDown(window, { key: '3' });
    expect(knowledgeApi.review).toHaveBeenCalledTimes(1);
    reject(new Error('网络中断'));
    expect(await screen.findByRole('alert')).toHaveTextContent('网络中断');
    expect(screen.getByRole('button', { name: /^简单，/ })).toBeDisabled();
    fireEvent.keyDown(window, { key: '4' });
    expect(knowledgeApi.review).toHaveBeenCalledTimes(1);
    fireEvent.click(screen.getByRole('button', { name: /^良好，/ }));
    await screen.findByRole('region', { name: '复习确认结果' });
    const calls = vi.mocked(knowledgeApi.review).mock.calls;
    expect(calls[1]).toEqual(calls[0]);
    expect(calls[0][2]).toMatch(/^[a-f0-9-]{36}$/);
  });

  it('下一张重新隐藏答案，清空评级，加载新的详情和预览', async () => {
    vi.mocked(knowledgeApi.queue).mockResolvedValue(pageOf([card, { ...card, slug: 'second-card' }]));
    render(<KnowledgeReviewSessionPage onNavigate={vi.fn()} />);
    fireEvent.click(await screen.findByRole('button', { name: '显示答案' }));
    fireEvent.click(screen.getByRole('button', { name: /^良好，/ }));
    await screen.findByRole('region', { name: '复习确认结果' });
    fireEvent.click(screen.getByRole('button', { name: '下一题' }));
    await screen.findByRole('button', { name: '显示答案' });
    expect(screen.getByText('2 / 2')).toBeInTheDocument();
    expect(screen.queryByText('核心答案')).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: '下一题' })).toBeDisabled();
    expect(knowledgeApi.card).toHaveBeenLastCalledWith('second-card');
    expect(knowledgeApi.reviewPreview).toHaveBeenLastCalledWith('second-card');
  });

  it('详情或队列失败时支持重试，不误报完成', async () => {
    vi.mocked(knowledgeApi.queue).mockRejectedValueOnce(new Error('队列失败'));
    vi.mocked(knowledgeApi.reviewPreview).mockRejectedValueOnce(new Error('预览失败'));
    render(<KnowledgeReviewSessionPage onNavigate={vi.fn()} />);
    expect(await screen.findByRole('alert')).toHaveTextContent('队列失败');
    expect(screen.queryByRole('heading', { name: '今日待复习已完成' })).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '重新加载' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('预览失败');
    fireEvent.click(screen.getByRole('button', { name: '重新加载' }));
    expect(await screen.findByRole('button', { name: '显示答案' })).toBeEnabled();
  });

  it('快捷键忽略编辑输入和组合键', async () => {
    render(<><input aria-label="测试输入" /><KnowledgeReviewSessionPage onNavigate={vi.fn()} /></>);
    fireEvent.click(await screen.findByRole('button', { name: '显示答案' }));
    fireEvent.keyDown(screen.getByRole('textbox'), { key: '1' });
    fireEvent.keyDown(window, { key: '1', ctrlKey: true });
    fireEvent.keyDown(window, { key: '1', repeat: true });
    expect(knowledgeApi.review).not.toHaveBeenCalled();
  });
});
