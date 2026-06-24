import { cleanup, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it } from 'vitest';
import MarkdownView, { normalizeMarkdownContent } from './MarkdownView';

afterEach(cleanup);

describe('MarkdownView', () => {
  it('renders bold labels that are immediately followed by Chinese text', () => {
    render(<MarkdownView content="**注意：**给定 n 是一个正整数。" />);

    expect(screen.getByText('注意：').closest('strong')).toBeInTheDocument();
    expect(screen.queryByText('**注意：**给定 n 是一个正整数。')).not.toBeInTheDocument();
  });

  it('does not rewrite fenced code blocks', () => {
    const content = '```text\n**注意：**给定 n 是一个正整数。\n```';

    render(<MarkdownView content={content} />);

    expect(screen.getByText('**注意：**给定 n 是一个正整数。').closest('code')).toBeInTheDocument();
  });

  it('does not rewrite inline code spans', () => {
    const content = '示例：`a **注意：**给定 n`。';

    render(<MarkdownView content={content} />);

    expect(screen.getByText('a **注意：**给定 n').closest('code')).toBeInTheDocument();
  });

  it('normalizes only non-code prose lines', () => {
    expect(normalizeMarkdownContent('**注意：**给定 n 是一个正整数。')).toBe(
      '**注意：** 给定 n 是一个正整数。',
    );
    expect(normalizeMarkdownContent('示例：`a **注意：**给定 n`。')).toBe(
      '示例：`a **注意：**给定 n`。',
    );
    expect(normalizeMarkdownContent('    **注意：**给定 n 是一个正整数。')).toBe(
      '    **注意：**给定 n 是一个正整数。',
    );
  });
});
