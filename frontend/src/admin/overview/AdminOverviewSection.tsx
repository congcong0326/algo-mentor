import type { ReactNode } from 'react';
import type { AdminOverviewSection as Section } from '../../types/api';

export default function AdminOverviewSection<T>({ title, section, children }: { title: string; section: Section<T>; children: (data: T) => ReactNode }) {
  return <section className="admin-overview-section"><h2>{title}</h2>{section.available && section.data ? children(section.data) : <p className="admin-overview-unavailable" role="status">此区块暂不可用</p>}</section>;
}
