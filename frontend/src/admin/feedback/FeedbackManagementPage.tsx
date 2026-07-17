import { RefreshCw } from 'lucide-react';
import { useEffect, useState } from 'react';
import { ApiRequestError, getAdminFeedbackThread, getAdminFeedbackThreads, markAdminFeedbackRead, replyAdminFeedback, requireApiData, updateAdminFeedbackStatus } from '../../services/api';
import type { AdminFeedbackListQuery, FeedbackStatus, FeedbackThreadDetail, FeedbackThreadPage } from '../../types/api';
import FeedbackComposer from '../../feedback/FeedbackComposer';
import FeedbackThreadTimeline from '../../feedback/FeedbackThreadTimeline';

export default function FeedbackManagementPage({ search, onNavigate, onUnreadCountChanged }: { search: string; onNavigate: (path: string) => void; onUnreadCountChanged?: (count: number) => void }) {
  const params = new URLSearchParams(search); const selectedId = positive(params.get('threadId'));
  const query: AdminFeedbackListQuery = { status: (params.get('status') as FeedbackStatus) || '', category: (params.get('category') as AdminFeedbackListQuery['category']) || '', userId: positive(params.get('userId')), unreadOnly: params.get('unreadOnly') === 'true' };
  const [page, setPage] = useState<FeedbackThreadPage>(); const [detail, setDetail] = useState<FeedbackThreadDetail>(); const [error, setError] = useState(''); const [pending, setPending] = useState(false);
  useEffect(() => { void load(); }, [search]); useEffect(() => { if (selectedId) void loadDetail(selectedId); else setDetail(undefined); }, [selectedId]);
  async function load() { try { const next = requireApiData(await getAdminFeedbackThreads(query), '反馈列表加载失败'); setPage(next); onUnreadCountChanged?.(next.unreadMessageCount); } catch (caught) { setError(text(caught)); } }
  async function loadDetail(id: number) { try { const next = requireApiData(await getAdminFeedbackThread(id), '反馈详情加载失败'); setDetail(next); if (next.unreadMessageCount) { const read = requireApiData(await markAdminFeedbackRead(id), '标记已读失败'); setDetail({ ...next, unreadMessageCount: 0 }); onUnreadCountChanged?.(read.unreadMessageCount); await load(); } } catch (caught) { setError(text(caught)); } }
  function changeFilters(next: Record<string, string>) {
    const value = new URLSearchParams(search);
    Object.entries(next).forEach(([key, item]) => item ? value.set(key, item) : value.delete(key));
    value.delete('threadId');
    onNavigate(`/admin/feedback${value.size ? `?${value}` : ''}`);
  }
  function select(id: number) {
    const value = new URLSearchParams(search);
    value.set('threadId', String(id));
    onNavigate(`/admin/feedback?${value}`);
  }
  async function send(content: string) { if (!detail) return; setPending(true); try { setDetail(requireApiData(await replyAdminFeedback(detail.id, { content }), '回复失败')); await load(); } catch (caught) { setError(text(caught)); } finally { setPending(false); } }
  async function setStatus(status: FeedbackStatus) { if (!detail) return; setPending(true); try { setDetail(requireApiData(await updateAdminFeedbackStatus(detail.id, status), '状态更新失败')); await load(); } catch (caught) { setError(text(caught)); } finally { setPending(false); } }
  return <section className="admin-feedback-page"><header className="feedback-page-header"><div><h1>反馈管理</h1>{error ? <p className="error-text">{error}</p> : null}</div><button aria-label="刷新" className="icon-button" onClick={() => void load()} title="刷新" type="button"><RefreshCw aria-hidden="true" /></button></header><div className="feedback-filter-bar"><select aria-label="状态" onChange={(e) => changeFilters({ status: e.target.value })} value={query.status}><option value="">全部状态</option><option value="OPEN">OPEN</option><option value="CLOSED">CLOSED</option></select><select aria-label="分类" onChange={(e) => changeFilters({ category: e.target.value })} value={query.category}><option value="">全部分类</option><option value="BUG">BUG</option><option value="SUGGESTION">SUGGESTION</option><option value="OTHER">OTHER</option></select><label><input checked={query.unreadOnly} onChange={(e) => changeFilters({ unreadOnly: String(e.target.checked) })} type="checkbox" /> 仅未读</label></div><div className="admin-feedback-layout"><div className="admin-feedback-table-wrap"><table><thead><tr><th>未读</th><th>状态</th><th>分类</th><th>用户</th><th>主题</th><th>更新时间</th></tr></thead><tbody>{(page?.items ?? []).map(item => <tr className={item.id === selectedId ? 'selected' : ''} key={item.id} onClick={() => select(item.id)}><td>{item.unreadMessageCount || ''}</td><td>{item.status}</td><td>{item.category}</td><td>{item.user?.email || item.user?.id || item.id}</td><td>{item.subject || '未命名反馈'}</td><td>{new Date(item.updatedAt).toLocaleString()}</td></tr>)}</tbody></table></div><aside className="admin-feedback-detail">{detail ? <><header><h2>{detail.subject || '未命名反馈'}</h2><p>{detail.status} · {detail.category}</p>{detail.sourcePath ? <small>{detail.sourcePath}</small> : null}</header><FeedbackThreadTimeline detail={detail} /><div className="admin-feedback-actions"><button className="secondary-button" disabled={pending || detail.status === 'CLOSED'} onClick={() => void setStatus('CLOSED')} type="button">关闭</button><button className="secondary-button" disabled={pending || detail.status === 'OPEN'} onClick={() => void setStatus('OPEN')} type="button">重新打开</button></div><FeedbackComposer onSend={send} pending={pending} /></> : <p className="feedback-empty">选择一条反馈进行处理。</p>}</aside></div></section>;
}
function positive(value: string | null): number | undefined { const id = Number(value); return Number.isSafeInteger(id) && id > 0 ? id : undefined; }
function text(error: unknown): string { return error instanceof ApiRequestError || error instanceof Error ? error.message || '请求失败' : '请求失败'; }
