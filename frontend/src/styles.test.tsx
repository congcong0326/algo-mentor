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

  it('keeps primary UI chrome within the reference landing vocabulary', () => {
    expect(styles).toContain('position: fixed');
    expect(styles).toContain('backdrop-filter: saturate(180%) blur(20px)');
    expect(styles).toContain('border: 1px solid var(--border-subtle)');
    expect(styles).toContain('background: var(--action-primary)');
    expect(styles).toContain('border-radius: var(--radius-pill)');
  });
});
