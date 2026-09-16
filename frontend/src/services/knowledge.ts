import type { KnowledgeArticle, KnowledgeCard, KnowledgeNode, KnowledgePage, KnowledgeReviewSummary, KnowledgeTree } from '../types/knowledge';

const API = '/api/knowledge';
export async function knowledgeRequest<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`${API}${path}`, { credentials: 'include', ...init, headers: { Accept: 'application/json', ...(init?.body ? { 'Content-Type': 'application/json' } : {}), ...init?.headers } });
  const payload = await response.json().catch(() => undefined);
  if (!response.ok || !payload?.success) throw new Error(payload?.error?.message || `请求失败（${response.status}）`);
  return payload.data as T;
}
export const knowledgeApi = {
  topics: () => knowledgeRequest<{ items: KnowledgeNode[] }>('/topics'),
  tree: (id: number) => knowledgeRequest<KnowledgeTree>(`/outline-nodes/${id}/tree`),
  cards: (id: number, page = 1) => knowledgeRequest<KnowledgePage<KnowledgeCard>>(`/outline-nodes/${id}/cards?page=${page}&pageSize=100`),
  articles: (id: number, page = 1) => knowledgeRequest<KnowledgePage<KnowledgeArticle>>(`/outline-nodes/${id}/articles?page=${page}&pageSize=100`),
  card: (slug: string) => knowledgeRequest<KnowledgeCard>(`/cards/${encodeURIComponent(slug)}`),
  article: (id: number) => knowledgeRequest<KnowledgeArticle>(`/articles/${id}`),
  review: (slug: string, rating: string, clientAttemptId: string) => knowledgeRequest(`/cards/${encodeURIComponent(slug)}/review-attempts`, { method: 'POST', body: JSON.stringify({ clientAttemptId, rating, timezone: Intl.DateTimeFormat().resolvedOptions().timeZone }) }),
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
