import { ChevronDown, PanelLeftClose, PanelLeftOpen, X } from 'lucide-react';
import type { RefObject } from 'react';
import HeaderActionTooltip from '../../app/HeaderActionTooltip';
import type { LocaleResources } from '../../i18n/locales';
import {
  adminPageHref,
  flattenAdminPages,
  isAdminPageGroup,
  type AdminModuleDefinition,
  type AdminModuleId,
  type AdminPageDefinition,
  type AdminPageId,
} from './adminNavigation';

interface AdminSidebarProps {
  activeModuleId?: AdminModuleId;
  activePageId?: AdminPageId;
  collapsed: boolean;
  expandedModuleId?: AdminModuleId;
  feedbackUnreadCount?: number;
  modules: AdminModuleDefinition[];
  onClose: () => void;
  onNavigate: (path: string) => void;
  onToggleCollapsed: () => void;
  onToggleModule: (moduleId: AdminModuleId) => void;
  open: boolean;
  resources: LocaleResources;
  sidebarRef: RefObject<HTMLElement | null>;
}

export default function AdminSidebar({
  activeModuleId,
  activePageId,
  collapsed,
  expandedModuleId,
  feedbackUnreadCount,
  modules,
  onClose,
  onNavigate,
  onToggleCollapsed,
  onToggleModule,
  open,
  resources,
  sidebarRef,
}: AdminSidebarProps) {
  const t = resources.adminShell;
  const SidebarToggleIcon = collapsed ? PanelLeftOpen : PanelLeftClose;
  const sidebarToggleLabel = collapsed ? t.expandNavigation : t.collapseNavigation;

  function navigate(page: AdminPageDefinition) {
    onNavigate(adminPageHref(page));
  }

  return (
    <aside className={`admin-sidebar ${open ? 'open' : ''}`} aria-label={t.businessNavigation} ref={sidebarRef}>
      <div className="admin-sidebar-topbar">
        <div className="admin-sidebar-brand">
          <span className="admin-sidebar-brand-mark" aria-hidden="true">LM</span>
          <span className="admin-sidebar-brand-copy">
            <strong>{resources.app.brandName}</strong>
            <small>{t.workspace}</small>
          </span>
        </div>
        <HeaderActionTooltip className="admin-sidebar-toggle-tooltip" id="admin-sidebar-toggle-tooltip" label={sidebarToggleLabel}>
          <button
            aria-controls="admin-business-navigation"
            aria-describedby="admin-sidebar-toggle-tooltip"
            aria-expanded={!collapsed}
            aria-label={sidebarToggleLabel}
            className="icon-button admin-sidebar-collapse"
            onClick={onToggleCollapsed}
            type="button"
          >
            <SidebarToggleIcon aria-hidden="true" />
          </button>
        </HeaderActionTooltip>
      </div>

      <div className="admin-sidebar-heading">
        <span>{t.navigation}</span>
        <button aria-label={t.closeNavigation} className="icon-button" onClick={onClose} type="button">
          <X aria-hidden="true" />
        </button>
      </div>

      <nav id="admin-business-navigation">
        {modules.map((module) => {
          const Icon = module.icon;
          const pages = flattenAdminPages(module);
          const directPage = pages.length === 1 ? pages[0] : undefined;
          const expanded = expandedModuleId === module.id && !collapsed;
          const active = activeModuleId === module.id;
          const unread = module.id === 'operations' && Boolean(feedbackUnreadCount && feedbackUnreadCount > 0);

          return (
            <div className={`admin-sidebar-module${active ? ' active' : ''}${unread ? ' unread' : ''}`} key={module.id}>
              <button
                aria-current={active ? 'page' : undefined}
                aria-expanded={directPage ? undefined : expanded}
                className="admin-sidebar-module-button"
                onClick={() => directPage ? navigate(directPage) : onToggleModule(module.id)}
                title={t.labels[module.labelKey]}
                type="button"
              >
                <Icon aria-hidden="true" />
                <span>{t.labels[module.labelKey]}</span>
                {unread ? <b>{feedbackUnreadCount! > 99 ? '99+' : feedbackUnreadCount}</b> : null}
                {directPage ? null : <ChevronDown aria-hidden="true" className="admin-sidebar-module-chevron" />}
              </button>

              {!directPage && expanded ? (
                <div className="admin-sidebar-children">
                  {module.items.map((entry) => {
                    if (!isAdminPageGroup(entry)) {
                      return (
                        <AdminSidebarPage
                          active={activePageId === entry.id}
                          feedbackUnreadCount={entry.id === 'feedback' ? feedbackUnreadCount : undefined}
                          key={entry.id}
                          label={t.labels[entry.labelKey]}
                          onClick={() => navigate(entry)}
                        />
                      );
                    }

                    return (
                      <div className="admin-sidebar-subgroup" key={entry.id}>
                        <span>{t.labels[entry.labelKey]}</span>
                        <div>
                          {entry.items.map((page) => (
                            <AdminSidebarPage
                              active={activePageId === page.id}
                              key={page.id}
                              label={t.labels[page.labelKey]}
                              nested
                              onClick={() => navigate(page)}
                            />
                          ))}
                        </div>
                      </div>
                    );
                  })}
                </div>
              ) : null}
            </div>
          );
        })}
      </nav>
    </aside>
  );
}

function AdminSidebarPage({
  active,
  feedbackUnreadCount,
  label,
  nested = false,
  onClick,
}: {
  active: boolean;
  feedbackUnreadCount?: number;
  label: string;
  nested?: boolean;
  onClick: () => void;
}) {
  return (
    <button
      aria-current={active ? 'page' : undefined}
      className={`admin-sidebar-page${nested ? ' nested' : ''}`}
      onClick={onClick}
      type="button"
    >
      <span aria-hidden="true" />
      <strong>{label}</strong>
      {feedbackUnreadCount && feedbackUnreadCount > 0 ? <b>{feedbackUnreadCount > 99 ? '99+' : feedbackUnreadCount}</b> : null}
    </button>
  );
}
