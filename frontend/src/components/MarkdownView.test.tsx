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

  it('renders bold text with extra spaces inside delimiters', () => {
    render(<MarkdownView content="** 注意** 和 **边界 ** 都很重要。" />);

    expect(screen.getByText('注意').closest('strong')).toBeInTheDocument();
    expect(screen.getByText('边界').closest('strong')).toBeInTheDocument();
  });

  it('renders italic text with trailing spaces before underscore delimiters', () => {
    render(<MarkdownView content="变量 _nums1 _ 需要先排序。" />);

    expect(screen.getByText('nums1').closest('em')).toBeInTheDocument();
  });

  it('renders loose underscore emphasis next to Chinese text', () => {
    render(<MarkdownView content="给你两个有序整数数组 _nums1 _和 _nums2_。" />);

    expect(screen.getByText('nums1').closest('em')).toBeInTheDocument();
    expect(screen.getByText('nums2').closest('em')).toBeInTheDocument();
  });

  it('renders markdown when content contains escaped newlines', () => {
    render(<MarkdownView content={'# 合并两个有序数组\\n\\n- 初始化 _n _。\\n\\n```text\\n输出：[1]\\n```'} />);

    expect(screen.getByRole('heading', { name: '合并两个有序数组' })).toBeInTheDocument();
    expect(screen.getByText('n').closest('em')).toBeInTheDocument();
    expect(screen.getByText('输出：[1]').closest('code')).toBeInTheDocument();
  });

  it('renders loose markdown in imported problem statements', () => {
    const content = '# 合并两个有序数组\\n\\n'
      + '给你两个有序整数数组 _nums1 _和 _nums2_，请你将 _nums2 _合并到 _nums1 _中_，_使 _nums1 _成为一个有序数组。\\n\\n'
      + '**说明：**\\n\\n'
      + '- 初始化 _nums1_ 和 _nums2_ 的元素数量分别为 _m_ 和 _n _。\\n\\n'
      + '- 你可以假设 _nums1 _有足够的空间（空间大小大于或等于 _m + n_）来保存 _nums2_ 中的元素。\\n\\n'
      + '**示例：**\\n\\n'
      + '```text\\n\\n输入：\\nnums1 = [1,2,3,0,0,0], m = 3\\nnums2 = [2,5,6],       n = 3\\n\\n输出：[1,2,2,3,5,6]\\n```\\n\\n'
      + '**提示：**\\n\\n'
      + '- `nums1.length == m + n`';

    render(<MarkdownView content={content} />);

    expect(screen.getByRole('heading', { name: '合并两个有序数组' })).toBeInTheDocument();
    expect(screen.getByText('说明：').closest('strong')).toBeInTheDocument();
    expect(screen.getByText('示例：').closest('strong')).toBeInTheDocument();
    expect(screen.getByText('提示：').closest('strong')).toBeInTheDocument();
    expect(screen.getAllByText('nums1').some((element) => element.closest('em'))).toBe(true);
    expect(screen.getAllByText('nums2').some((element) => element.closest('em'))).toBe(true);
    expect(screen.getByText(/输出：\[1,2,2,3,5,6]/).closest('code')).toBeInTheDocument();
    expect(screen.queryByText(/_，_/)).not.toBeInTheDocument();
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
    expect(normalizeMarkdownContent('** 注意 ** 和 _nums1 _')).toBe('**注意** 和 *nums1*');
    expect(normalizeMarkdownContent('数组 _nums1 _和 _nums2_。')).toBe('数组 *nums1*和 *nums2*。');
    expect(normalizeMarkdownContent('_nums1 _中_，_使 _nums1 _')).toBe('*nums1*中，使 *nums1*');
    expect(normalizeMarkdownContent('# 标题\\n\\n正文')).toBe('# 标题\n\n正文');
    expect(normalizeMarkdownContent('示例：`** 注意 ** 和 _nums1 _`。')).toBe(
      '示例：`** 注意 ** 和 _nums1 _`。',
    );
  });
});
