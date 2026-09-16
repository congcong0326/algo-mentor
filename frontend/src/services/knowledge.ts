import type { KnowledgeArticle, KnowledgeCard, KnowledgeLearningState, KnowledgeNode, KnowledgeNodeDetail, KnowledgePage, KnowledgeReviewSummary, KnowledgeReviewPreview, KnowledgeReviewResult, KnowledgeTree } from '../types/knowledge';
import { apiFetch } from './api';

const API = '/api/knowledge';
export async function knowledgeRequest<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await apiFetch(`${API}${path}`, { credentials: 'include', ...init, headers: { Accept: 'application/json', ...(init?.body ? { 'Content-Type': 'application/json' } : {}), ...init?.headers } });
  const payload = await response.json().catch(() => undefined);
  if (!response.ok || !payload?.success) throw new Error(payload?.error?.message || `请求失败（${response.status}）`);
  return payload.data as T;
}
export const knowledgeApi = {
  topics: () => knowledgeRequest<{ items: KnowledgeNode[] }>('/topics'),
  tree: (id: number) => knowledgeRequest<KnowledgeTree>(`/outline-nodes/${id}/tree`),
  node: (id: number) => knowledgeRequest<KnowledgeNodeDetail>(`/outline-nodes/${id}`),
  cards: (id: number, page = 1, pageSize = 100, keyword = '') => knowledgeRequest<KnowledgePage<KnowledgeCard>>(`/outline-nodes/${id}/cards?${new URLSearchParams({ page: String(page), pageSize: String(pageSize), keyword })}`),
  articles: (id: number, page = 1) => knowledgeRequest<KnowledgePage<KnowledgeArticle>>(`/outline-nodes/${id}/articles?page=${page}&pageSize=100`),
  card: (slug: string) => knowledgeRequest<KnowledgeCard>(`/cards/${encodeURIComponent(slug)}`),
  setEnrollment: (slug: string, enrolled: boolean) => knowledgeRequest<KnowledgeLearningState>(`/cards/${encodeURIComponent(slug)}/review-enrollment`, { method: enrolled ? 'PUT' : 'DELETE' }),
  article: (id: number) => knowledgeRequest<KnowledgeArticle>(`/articles/${id}`),
  reviewPreview: (slug: string) => knowledgeRequest<KnowledgeReviewPreview>(`/cards/${encodeURIComponent(slug)}/review-preview?${new URLSearchParams({ timezone: Intl.DateTimeFormat().resolvedOptions().timeZone })}`),
  reviewCards: (page = 1) => knowledgeRequest<KnowledgePage<KnowledgeCard>>(`/review/cards?filter=ALL&page=${page}&pageSize=100`),
  review: (slug: string, rating: string, clientAttemptId: string) => knowledgeRequest<KnowledgeReviewResult>(`/cards/${encodeURIComponent(slug)}/review-attempts`, { method: 'POST', body: JSON.stringify({ clientAttemptId, rating, timezone: Intl.DateTimeFormat().resolvedOptions().timeZone }) }),
  summary: () => knowledgeRequest<KnowledgeReviewSummary>('/review/summary'),
  queue: (page = 1) => knowledgeRequest<KnowledgePage<KnowledgeCard>>(`/review/cards?filter=DUE&page=${page}&pageSize=100`),
};

/** 所有分页读完再展示，避免目录内容超过单页后静默丢失。 */
export async function allKnowledgePages<T>(load: (page: number) => Promise<KnowledgePage<T>>): Promise<T[]> {
  const result: T[] = [];
  for (let page = 1; ; page++) {
    const response = await load(page);
    result.push(...response.items);
    if (result.length >= response.total || response.items.length === 0) return result;
  }
}
