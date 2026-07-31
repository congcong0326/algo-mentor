import type { ReactNode } from 'react';
import { useI18n } from '../../i18n/I18nProvider';
import type { AdminOverviewSection as Section } from '../../types/api';

export default function AdminOverviewSection<T>({ title, section, children }: { title: string; section: Section<T>; children: (data: T) => ReactNode }) {
  const { resources } = useI18n();
  return <section className="admin-overview-section"><h2>{title}</h2>{section.available && section.data ? children(section.data) : <p className="admin-overview-unavailable" role="status">{resources.adminOverview.sectionUnavailable}</p>}</section>;
}
