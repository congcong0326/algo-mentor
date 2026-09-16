import { afterEach, describe, expect, it, vi } from 'vitest';
import { knowledgeApi } from './knowledge';

const jsonResponse = (data: unknown) => new Response(JSON.stringify({ success: true, data }), { headers: { 'Content-Type': 'application/json' } });

afterEach(() => {
  vi.unstubAllGlobals();
  document.cookie = 'XSRF-TOKEN=; Max-Age=0; path=/';
});

describe('知识卡 API', () => {
  it('加入与移除复习携带 CSRF，并返回后端学习状态', async () => {
    document.cookie = 'XSRF-TOKEN=knowledge-csrf; path=/';
    const fetchMock = vi.fn().mockResolvedValueOnce(jsonResponse({ enrolled: true, isDue: true })).mockResolvedValueOnce(jsonResponse({ enrolled: false, isDue: false }));
    vi.stubGlobal('fetch', fetchMock);
    expect(await knowledgeApi.setEnrollment('stable-card', true)).toEqual({ enrolled: true, isDue: true });
    expect(await knowledgeApi.setEnrollment('stable-card', false)).toEqual({ enrolled: false, isDue: false });
    for (const [index, method] of ['PUT', 'DELETE'].entries()) {
      expect(fetchMock.mock.calls[index][0]).toBe('/api/knowledge/cards/stable-card/review-enrollment');
      const init = fetchMock.mock.calls[index][1] as RequestInit;
      expect(init.method).toBe(method);
      expect(new Headers(init.headers).get('X-XSRF-TOKEN')).toBe('knowledge-csrf');
    }
  });

  it('分页搜索正确编码特殊字符，不吞掉失败响应', async () => {
    const fetchMock = vi.fn().mockResolvedValueOnce(jsonResponse({ items: [], total: 0, page: 2, pageSize: 20 })).mockResolvedValueOnce(new Response(JSON.stringify({ success: false, error: { message: '更新失败' } }), { status: 503 }));
    vi.stubGlobal('fetch', fetchMock);
    await knowledgeApi.cards(11, 2, 20, 'Java & 集合');
    const url = new URL(fetchMock.mock.calls[0][0], 'http://localhost');
    expect(url.searchParams.get('keyword')).toBe('Java & 集合');
    expect(url.searchParams.get('page')).toBe('2');
    expect(url.searchParams.get('pageSize')).toBe('20');
    await expect(knowledgeApi.setEnrollment('stable-card', true)).rejects.toThrow('更新失败');
  });
});
