import { describe, expect, it } from 'vitest';
import styles from './styles.css?raw';

describe('LeetReviewer-inspired visual system', () => {
  it('defines the white product palette and yellow CTA tokens', () => {
    expect(styles).toContain('--surface-page: #ffffff');
    expect(styles).toContain('--surface-card: #ffffff');
    expect(styles).toContain('--border-subtle: #e2e8f0');
    expect(styles).toContain('--text-primary: #0f172a');
    expect(styles).toContain('--action-primary: #ffc01e');
    expect(styles).toContain('--action-ink: #0f172a');
  });

  it('defines a neutral dark mode palette through root theme tokens', () => {
    expect(styles).toContain(':root[data-theme="dark"]');
    expect(styles).toContain('color-scheme: dark');
    expect(styles).toContain('--surface-page: #0b1220');
    expect(styles).toContain('--surface-card: #111827');
    expect(styles).toContain('--surface-muted: #162033');
    expect(styles).toContain('--surface-soft: #1f2937');
    expect(styles).toContain('--border-subtle: #263447');
    expect(styles).toContain('--text-primary: #f8fafc');
    expect(styles).toContain('--accent-warm-soft: rgb(255 192 30 / 14%)');
    expect(styles).toContain('--accent-warm-text: #ffd166');
    expect(styles).toContain('--success-soft: rgb(20 184 166 / 15%)');
    expect(styles).toContain('--danger-soft: rgb(248 113 113 / 14%)');
  });

  it('styles the theme toggle as a stable icon control', () => {
    expect(styles).toContain('.theme-toggle-button');
    expect(styles).toContain('width: 34px');
    expect(styles).toContain('height: 34px');
  });

  it('uses one rounded-corner token for menus, tooltips, and dialogs', () => {
    expect(styles).toContain('--radius-overlay: 14px');
    expect(styles).toMatch(/\.language-selector-menu \{[^}]*border-radius: var\(--radius-overlay\);/);
    expect(styles).toMatch(/\.account-menu-popover \{[^}]*border-radius: var\(--radius-overlay\);/);
    expect(styles).toMatch(/\.header-action-tooltip \{[^}]*border-radius: var\(--radius-overlay\);/);
    expect(styles).toMatch(/\.toolbar-tooltip \{[^}]*border-radius: var\(--radius-overlay\);/);
    expect(styles).toMatch(/\.feedback-dialog \{[^}]*border-radius: var\(--radius-overlay\);/);
    expect(styles).toMatch(/\.admin-confirm-dialog \{[^}]*border-radius: var\(--radius-overlay\);/);
    expect(styles).toMatch(/@media \(max-width: 760px\) \{\n  \.feedback-dialog \{\n    border-radius: var\(--radius-overlay\);/);
  });

  it('anchors review-setting tooltips to their help icons', () => {
    expect(styles).toMatch(/\.review-setting-tooltip-wrap \{[^}]*position: relative;/);
  });

  it('positions template previews outside their source cards', () => {
    expect(styles).toMatch(/\.template-card-preview \{[^}]*bottom: calc\(100% \+ 10px\);/);
    expect(styles).not.toContain('.template-selected-summary');
  });

  it('keeps login quiet and removes decorative watermark content', () => {
    expect(styles).toContain('.login-page {\n  --login-background: #f7f7f5');
    expect(styles).toContain('--login-text: #191918');
    expect(styles).toContain('--login-submit-background: #191918');
    expect(styles).toContain(':root[data-theme="dark"] .login-page');
    expect(styles).toContain('--login-background: #111110');
    expect(styles).toContain('--login-text: #f5f5f2');
    expect(styles).not.toContain('QIANXIN');
    expect(styles).not.toContain('曹明英');
  });

  it('keeps signed-in chrome compact and neutral', () => {
    expect(styles).toContain('position: fixed');
    expect(styles).toContain('backdrop-filter: blur(12px)');
    expect(styles).toContain('border: 1px solid var(--border-subtle)');
    expect(styles).toContain('background: var(--surface-elevated)');
    expect(styles).not.toContain(':root[data-theme="dark"] .app-header {\n  background: #ffffff;');
    expect(styles).toMatch(/\.app-brand strong \{[^}]*font-weight: 700;/);
    expect(styles).not.toContain('.app-brand-mark');
    expect(styles).not.toContain('.app-brand::before');
    expect(styles).toMatch(/\.app-shell \{[^}]*--action-primary: #191918;/);
    expect(styles).toMatch(/\.app-nav-button\[aria-pressed="true"\]::after \{[^}]*height: 2px;/);
    expect(styles).toContain('color: var(--text-secondary)');
    expect(styles).toContain('color: var(--text-primary)');
    expect(styles).toContain('background: var(--action-primary)');
    expect(styles).toContain('border-radius: var(--radius-pill)');
  });

  it('uses one compact control and surface vocabulary across user pages', () => {
    expect(styles).toMatch(/\.primary-button,[\s\S]*?\.secondary-button \{[^}]*min-height: 38px;[^}]*border-radius: 7px;/);
    expect(styles).toMatch(/\.secondary-button \{[^}]*border: 1px solid var\(--border-strong\);[^}]*box-shadow: none;/);
    expect(styles).toMatch(/\.icon-button \{[^}]*width: 34px;[^}]*height: 34px;[^}]*border-radius: 7px;/);
    expect(styles).toMatch(/\.search-field \{[^}]*min-height: 38px;[^}]*border: 1px solid var\(--border-subtle\);/);
    expect(styles).toMatch(/\.plan-overview,[\s\S]*?\.plan-insight-panel \{[^}]*box-shadow: none;/);
    expect(styles).toMatch(/\.my-card \{[^}]*box-shadow: none;/);
  });

  it('keeps plan and profile summaries compact without nested cards', () => {
    expect(styles).toMatch(/\.plan-stat-grid \{[^}]*gap: 0;[^}]*border-top: 1px solid var\(--border-subtle\);/);
    expect(styles).toMatch(/\.plan-stat-card \{[^}]*display: flex;[^}]*border: 0;[^}]*background: transparent;/);
    expect(styles).toMatch(/\.plan-list-row \{[^}]*min-height: 72px;[^}]*padding: 11px 12px;/);
    expect(styles).toMatch(/\.my-summary-card \{[^}]*min-height: 82px;[^}]*padding: 13px;[^}]*box-shadow: none;/);
    expect(styles).toMatch(/\.my-page-kicker,[\s\S]*?\.my-section-eyebrow \{[^}]*letter-spacing: 0;[^}]*text-transform: none;/);
    expect(styles).toMatch(/@media \(max-width: 980px\) \{[\s\S]*?\.plan-stat-grid \{[^}]*grid-template-columns: repeat\(2, minmax\(0, 1fr\)\);/);
    expect(styles).toMatch(/@media \(max-width: 640px\) \{[\s\S]*?\.my-summary-grid \{[^}]*grid-template-columns: repeat\(2, minmax\(0, 1fr\)\);/);
    expect(styles).toMatch(/@media \(max-width: 640px\) \{[\s\S]*?\.my-summary-card p \{[^}]*display: none;/);
  });

  it('uses one icon-control vocabulary on the plan creation page', () => {
    expect(styles).toMatch(/\.learning-create-content \{[^}]*gap: 22px;[^}]*width: min\(900px, 100%\);[^}]*justify-self: center;/);
    expect(styles).toMatch(/\.learning-create-content--preview \{[^}]*width: 100%;/);
    expect(styles).toMatch(/\.learning-create-back \{[^}]*width: 34px;[^}]*height: 34px;[^}]*border-radius: 7px;/);
    expect(styles).toMatch(/\.create-mode-switch button svg \{[^}]*width: 16px;[^}]*height: 16px;/);
    expect(styles).toMatch(/\.template-card-icon \{[^}]*width: 28px;[^}]*height: 28px;[^}]*border-radius: 6px;/);
    expect(styles).toMatch(/\.segmented-grid button\.selected,[\s\S]*?\.topic-option-grid button\.selected \{[^}]*background: var\(--text-primary\);[^}]*color: var\(--surface-card\);/);
  });

  it('keeps the review center compact and list-oriented', () => {
    expect(styles).toMatch(/\.mistake-page \{[^}]*align-content: start;[^}]*gap: 16px;[^}]*width: min\(920px, 100%\);/);
    expect(styles).toMatch(/\.mistake-stat-grid \{[^}]*border-top: 1px solid var\(--border-subtle\);[^}]*border-bottom: 1px solid var\(--border-subtle\);/);
    expect(styles).toMatch(/\.mistake-note-card \{[^}]*grid-template-columns: minmax\(0, 1fr\) 198px auto;[^}]*min-height: 64px;[^}]*padding: 10px 12px;/);
    expect(styles).toMatch(/\.review-card-timeline \{[^}]*width: 198px;[^}]*min-width: 198px;[^}]*justify-content: flex-end;/);
    expect(styles).toMatch(/\.mistake-note-actions \.icon-button \{[^}]*width: 34px;[^}]*height: 34px;[^}]*border-radius: 7px;/);
    expect(styles).toMatch(/\.mistake-header \.mistake-review-button \{[^}]*min-height: 38px;[^}]*border-radius: 7px;/);
    expect(styles).toMatch(/\.mistake-list > \.loading-panel \{[^}]*min-height: 152px;[^}]*box-shadow: none;/);
    expect(styles).toMatch(/@media \(max-width: 720px\) \{[\s\S]*?\.mistake-note-meta \.mistake-note-rating \{[^}]*display: none;/);
    expect(styles).toMatch(/@media \(max-width: 720px\) \{[\s\S]*?\.review-card-timeline \{[^}]*grid-column: 1 \/ -1;[^}]*grid-row: 2;/);
  });

  it('uses theme tokens for the public home surfaces and text', () => {
    expect(styles).toMatch(/\.home-hero \{[^}]*background: var\(--surface-page\);/);
    expect(styles).toMatch(/\.home-kicker \{[^}]*color: var\(--text-secondary\);/);
    expect(styles).toMatch(/\.home-hero h1 \{[^}]*color: var\(--text-primary\);/);
    expect(styles).toMatch(/\.home-subtitle \{[^}]*color: var\(--text-secondary\);/);
    expect(styles).toMatch(/\.home-company-strip p \{[^}]*color: var\(--text-muted\);/);
    expect(styles).toMatch(/\.company-mark \{[^}]*color: var\(--text-muted\);/);
  });

  it('keeps signed-in home shortcuts compact without stretching the page', () => {
    expect(styles).toMatch(/\.today-pack-home \{[^}]*align-content: start;/);
    expect(styles).toMatch(/\.today-pack-home-entry-grid \{[^}]*grid-template-columns: repeat\(2, minmax\(0, 1fr\)\);/);
    expect(styles).toMatch(/\.home-ability-insights \{[^}]*grid-template-columns: repeat\(3, minmax\(0, 1fr\)\);/);
    expect(styles).toMatch(/@media \(max-width: 640px\) \{[\s\S]*?\.today-pack-home-entry-grid \{[^}]*grid-template-columns: 1fr;/);
    expect(styles).toMatch(/@media \(max-width: 640px\) \{[\s\S]*?\.home-ability-insights \{[^}]*grid-template-columns: minmax\(0, 1fr\);/);
    expect(styles).toMatch(/@media \(max-width: 980px\) \{[\s\S]*?\.today-pack-problem-row \{[^}]*grid-template-columns: 40px minmax\(0, 1fr\) auto 18px;/);
  });

  it('keeps the ability detail dialog above the fixed app header with top safe spacing', () => {
    expect(styles).toMatch(/\.app-header \{[^}]*z-index: 100;/);
    expect(styles).toMatch(/\.ability-dialog-backdrop \{[^}]*z-index: 160;/);
    expect(styles).toMatch(/\.ability-dialog-backdrop \{[^}]*padding: 88px 24px 24px;/);
  });

  it('moves ability bubbles without scaling or rotating their rendered text', () => {
    expect(styles).toContain('margin-left: calc(var(--bubble-parallax-x) + var(--bubble-float-x-primary));');
    expect(styles).toContain('margin-top: calc(var(--bubble-parallax-y) + var(--bubble-float-y-primary));');
    expect(styles).not.toContain('rotate(0.32deg)');
    expect(styles).not.toContain('scale(1.01)');
    expect(styles).not.toMatch(/\.ability-bubble-node \{[^}]*will-change: transform;/);
  });

  it('keeps chat and review problem Markdown rendering aligned', () => {
    expect(styles).toContain('.practice-message .markdown-view,\n.review-problem-content .markdown-view {\n  margin: 0;\n  min-height: 0;\n  padding: 0;\n  border: 0;\n  background: transparent;\n  overflow: visible;');
    expect(styles).toContain('.review-problem-full .markdown-view {\n  margin-top: 12px;\n  min-height: 0;\n  padding: 0;\n  border: 0;\n  background: transparent;\n  overflow: visible;');
    expect(styles).toContain('line-height: 1.55;\n  white-space: normal;');
    expect(styles).toContain('margin-bottom: 10px;');
    expect(styles).toContain('.practice-message .markdown-view p,\n.practice-message .markdown-view li,\n.review-problem-content .markdown-view p,\n.review-problem-content .markdown-view li {\n  color: var(--text-secondary);\n  line-height: 1.55;');
    expect(styles).toContain('.review-problem-full .markdown-view p,\n.review-problem-full .markdown-view li {\n  color: var(--text-secondary);\n  line-height: 1.55;');
    expect(styles).toContain('.practice-message .markdown-view li,\n.review-problem-content .markdown-view li {\n  margin: 4px 0;\n  padding-left: 2px;');
    expect(styles).toContain('.review-problem-full .markdown-view li {\n  margin: 4px 0;\n  padding-left: 2px;');
    expect(styles).toContain('.practice-message .markdown-view li > p,\n.review-problem-content .markdown-view li > p {\n  display: inline;');
    expect(styles).toContain('.review-problem-full .markdown-view li > p {\n  display: inline;');
    expect(styles).toContain('.practice-message .markdown-view > :last-child,\n.review-problem-content .markdown-view > :last-child {\n  margin-bottom: 0;');
    expect(styles).toContain('.review-problem-full .markdown-view > :last-child {\n  margin-bottom: 0;');
  });

  it('defines dedicated review rating button states', () => {
    expect(styles).toContain('.review-rating-button {\n  --rating-color: var(--text-secondary);');
    expect(styles).toContain('.review-rating-label {\n  font-size: 14px;');
    expect(styles).toContain('.review-rating-button.is-selected {\n  border-color: var(--rating-color);');
    expect(styles).toContain('.review-rating-button-again');
    expect(styles).toContain('.review-rating-button-hard');
    expect(styles).toContain('.review-rating-button-good');
    expect(styles).toContain('.review-rating-button-easy');
  });
});
