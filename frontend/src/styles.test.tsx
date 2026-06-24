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
    expect(styles).toContain('width: 38px');
    expect(styles).toContain('height: 38px');
  });

  it('keeps primary UI chrome within the reference landing vocabulary', () => {
    expect(styles).toContain('position: fixed');
    expect(styles).toContain('backdrop-filter: saturate(180%) blur(20px)');
    expect(styles).toContain('border: 1px solid var(--border-subtle)');
    expect(styles).toContain('background: var(--action-primary)');
    expect(styles).toContain('border-radius: var(--radius-pill)');
  });

  it('keeps rendered Markdown lists compact and aligned', () => {
    expect(styles).toContain('.practice-message .markdown-view {\n  margin: 0;\n  min-height: 0;\n  padding: 0;\n  border: 0;\n  background: transparent;\n  overflow: visible;');
    expect(styles).toContain('line-height: 1.55;\n  white-space: normal;');
    expect(styles).toContain('margin-bottom: 10px;');
    expect(styles).toContain('.practice-message .markdown-view p,\n.practice-message .markdown-view li {\n  color: var(--text-secondary);\n  line-height: 1.55;');
    expect(styles).toContain('.practice-message .markdown-view li {\n  margin: 4px 0;\n  padding-left: 2px;');
    expect(styles).toContain('.practice-message .markdown-view li > p {\n  display: inline;');
    expect(styles).toContain('.practice-message .markdown-view > :last-child {\n  margin-bottom: 0;');
  });
});
