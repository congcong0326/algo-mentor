import { ArrowLeft, ChevronDown, LogOut, Menu, Moon, Sun, UserRound } from 'lucide-react';
import { useEffect, useRef, useState } from 'react';
import type { ReactNode } from 'react';
import HeaderActionTooltip from '../../app/HeaderActionTooltip';
import type { AppTheme } from '../../app/theme';
import LanguageSelector from '../../i18n/LanguageSelector';
import { useI18n } from '../../i18n/I18nProvider';
import type { AuthPermission, CurrentUser } from '../../types/api';
import AdminSidebar from './AdminSidebar';
import {
  accessibleAdminModules,
  adminModuleFromLocation,
  adminPageFromLocation,
  flattenAdminPages,
  type AdminModuleId,
} from './adminNavigation';

interface AdminShellProps {
  children: ReactNode;
  currentUser: CurrentUser;
  feedbackUnreadCount?: number;
  logoutError?: string;
  logoutPending?: boolean;
  onLogout: () => void;
  onNavigate: (path: string) => void;
  onToggleTheme: () => void;
  pageStatus?: ReactNode;
  pathname: string;
  search?: string;
  theme: AppTheme;
}

export default function AdminShell({
  children,
  currentUser,
  feedbackUnreadCount,
  logoutError,
  logoutPending = false,
  onLogout,
  onNavigate,
  onToggleTheme,
  pageStatus,
  pathname,
  search = '',
  theme,
}: AdminShellProps) {
  const { resources } = useI18n();
  const t = resources.adminShell;
  const permissions = new Set<AuthPermission>(currentUser.permissions ?? []);
  const modules = accessibleAdminModules(permissions);
  const activeModule = adminModuleFromLocation(pathname, search, modules);
  const activePage = adminPageFromLocation(pathname, search, modules);
  const [sidebarOpen, setSidebarOpen] = useState(false);
  const [sidebarCollapsed, setSidebarCollapsed] = useState(false);
  const [expandedModuleId, setExpandedModuleId] = useState<AdminModuleId | undefined>(activeModule?.id);
  const [accountMenuOpen, setAccountMenuOpen] = useState(false);
  const accountMenuRef = useRef<HTMLDivElement>(null);
  const sidebarRef = useRef<HTMLElement>(null);
  const mobileMenuButtonRef = useRef<HTMLButtonElement>(null);
  const userLabel = currentUser.displayName || currentUser.email || resources.app.unknownUser(currentUser.id);
  const ThemeIcon = theme === 'light' ? Moon : Sun;
  const themeLabel = theme === 'light' ? resources.app.switchToDarkMode : resources.app.switchToLightMode;
  const activeModuleLabel = activeModule ? t.labels[activeModule.labelKey] : t.workspace;
  const activePageLabel = activePage ? t.labels[activePage.labelKey] : activeModuleLabel;

  useEffect(() => {
    setSidebarOpen(false);
  }, [pathname, search]);

  useEffect(() => {
    if (activeModule && flattenAdminPages(activeModule).length > 1) {
      setExpandedModuleId(activeModule.id);
    }
  }, [activeModule?.id]);

  useEffect(() => {
    if (!accountMenuOpen) {
      return undefined;
    }
    function handleEscape(event: KeyboardEvent) {
      if (event.key === 'Escape') {
        setAccountMenuOpen(false);
      }
    }
    function handleOutside(event: MouseEvent) {
      if (!accountMenuRef.current?.contains(event.target as Node)) {
        setAccountMenuOpen(false);
      }
    }
    document.addEventListener('keydown', handleEscape);
    document.addEventListener('mousedown', handleOutside);
    return () => {
      document.removeEventListener('keydown', handleEscape);
      document.removeEventListener('mousedown', handleOutside);
    };
  }, [accountMenuOpen]);

  useEffect(() => {
    if (!sidebarOpen) {
      return undefined;
    }

    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    const focusTimer = window.setTimeout(() => {
      sidebarRef.current?.querySelector<HTMLElement>('.admin-sidebar-heading button')?.focus();
    });

    function handleDrawerKeyDown(event: KeyboardEvent) {
      if (event.key === 'Escape') {
        event.preventDefault();
        closeMobileSidebar(true);
        return;
      }
      if (event.key !== 'Tab') {
        return;
      }
      const focusable = Array.from(sidebarRef.current?.querySelectorAll<HTMLElement>('button:not(:disabled)') ?? []);
      if (focusable.length === 0) {
        return;
      }
      const first = focusable[0];
      const last = focusable[focusable.length - 1];
      if (event.shiftKey && document.activeElement === first) {
        event.preventDefault();
        last.focus();
      } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault();
        first.focus();
      }
    }

    document.addEventListener('keydown', handleDrawerKeyDown);
    return () => {
      window.clearTimeout(focusTimer);
      document.body.style.overflow = previousOverflow;
      document.removeEventListener('keydown', handleDrawerKeyDown);
    };
  }, [sidebarOpen]);

  function closeMobileSidebar(restoreFocus = false) {
    setSidebarOpen(false);
    if (restoreFocus) {
      window.setTimeout(() => mobileMenuButtonRef.current?.focus());
    }
  }

  function toggleModule(moduleId: AdminModuleId) {
    if (sidebarCollapsed) {
      setSidebarCollapsed(false);
      setExpandedModuleId(moduleId);
      return;
    }
    setExpandedModuleId((current) => current === moduleId ? undefined : moduleId);
  }

  return (
    <main className={`admin-shell${sidebarCollapsed ? ' sidebar-collapsed' : ''}`}>
      <header className="admin-shell-header" role="banner">
        <div className="admin-mobile-header-start">
          <button
            aria-label={t.openNavigation}
            className="icon-button admin-mobile-menu"
            onClick={() => setSidebarOpen(true)}
            ref={mobileMenuButtonRef}
            type="button"
          >
            <Menu aria-hidden="true" />
          </button>
          <div aria-hidden="true" className="admin-shell-brand admin-mobile-brand">
            <strong>{resources.app.brandName}</strong>
          </div>
        </div>

        <div className="admin-header-location" aria-label={t.pageNavigation}>
          <span>{activeModuleLabel}</span>
          {activePageLabel !== activeModuleLabel ? <strong>{activePageLabel}</strong> : null}
        </div>

        <div className="admin-shell-header-actions">
          <button className="admin-return-button" onClick={() => onNavigate('/')} type="button">
            <ArrowLeft aria-hidden="true" />
            <span>{t.returnToLearning}</span>
          </button>
          <HeaderActionTooltip id="admin-theme-tooltip" label={themeLabel}>
            <button aria-describedby="admin-theme-tooltip" aria-label={themeLabel} className="icon-button" onClick={onToggleTheme} type="button">
              <ThemeIcon aria-hidden="true" />
            </button>
          </HeaderActionTooltip>
          <LanguageSelector />
          <div className="account-menu" ref={accountMenuRef}>
            <button aria-expanded={accountMenuOpen} aria-haspopup="true" className="account-menu-trigger" onClick={() => setAccountMenuOpen((open) => !open)} type="button">
              <span className="account-avatar" aria-hidden="true">
                {currentUser.avatarUrl ? <img alt="" src={currentUser.avatarUrl} /> : <UserRound />}
              </span>
              <span className="account-menu-user-label">{userLabel}</span>
              <ChevronDown aria-hidden="true" />
            </button>
            {accountMenuOpen ? (
              <div className="account-menu-popover" aria-label={resources.app.loginStatus}>
                <div className="account-menu-identity"><strong>{userLabel}</strong><span>{currentUser.email}</span></div>
                <button className="account-menu-logout" disabled={logoutPending} onClick={onLogout} type="button">
                  <LogOut aria-hidden="true" />
                  <span>{logoutPending ? resources.app.loggingOut : resources.app.logout}</span>
                </button>
              </div>
            ) : null}
          </div>
        </div>
      </header>

      {sidebarOpen ? (
        <button aria-label={t.closeNavigation} className="admin-sidebar-scrim" onClick={() => closeMobileSidebar(true)} type="button" />
      ) : null}
      <AdminSidebar
        activeModuleId={activeModule?.id}
        activePageId={activePage?.id}
        collapsed={sidebarCollapsed}
        expandedModuleId={expandedModuleId}
        feedbackUnreadCount={feedbackUnreadCount}
        modules={modules}
        onClose={() => closeMobileSidebar(true)}
        onNavigate={onNavigate}
        onToggleCollapsed={() => setSidebarCollapsed((collapsed) => !collapsed)}
        onToggleModule={toggleModule}
        open={sidebarOpen}
        resources={resources}
        sidebarRef={sidebarRef}
      />

      <section className="admin-main-column">
        {logoutError ? <p className="error-text admin-shell-error" role="alert">{logoutError}</p> : null}
        <section className="admin-workspace">
          {pageStatus ? <div className="admin-workspace-status">{pageStatus}</div> : null}
          {children}
        </section>
      </section>
    </main>
  );
}
