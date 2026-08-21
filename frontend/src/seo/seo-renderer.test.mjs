import { describe, expect, it } from 'vitest';
import {
  buildSitemapUrls,
  escapeHtml,
  mergeProblemSeeds,
  problemPath,
  renderRobots,
  renderSeoDocument,
} from '../../scripts/seo-renderer.mjs';

const bilingual = {
  slug: 'two-sum',
  frontendId: 1,
  frontendDisplayId: '1',
  titleZh: '两数之和 <测试>',
  titleEn: 'Two Sum',
  difficulty: 'EASY',
  tagLabelsZh: ['数组', '哈希表'],
  tagLabelsEn: ['Array', 'Hash Table'],
  contentStatus: 'BILINGUAL',
  leetcodeUrl: 'https://leetcode.com/problems/two-sum/',
};

const chineseOnly = {
  slug: 'cn-only',
  frontendId: null,
  frontendDisplayId: 'LCR 1',
  titleZh: '中文题',
  titleEn: null,
  difficulty: 'MEDIUM',
  tagLabelsZh: ['数组'],
  tagLabelsEn: ['Array'],
  contentStatus: 'CN_ONLY',
  leetcodeUrl: 'https://leetcode.cn/problems/cn-only/',
};

const insights = [
  { slug: 'two-sum', reasonZH: '训练哈希查找。', reasonEN: 'Practice hash lookup.' },
  { slug: 'cn-only', reasonZH: '训练建模。', reasonEN: 'Practice modeling.' },
];

describe('SEO renderer', () => {
  it('builds locale-aware problem paths', () => {
    expect(problemPath('two-sum')).toBe('/problems/two-sum');
    expect(problemPath('two-sum', 'en')).toBe('/en/problems/two-sum');
  });

  it('escapes HTML content before rendering', () => {
    expect(escapeHtml('<script>"x" & \'y\'')).toBe('&lt;script&gt;&quot;x&quot; &amp; &#39;y&#39;');
    const [problem] = mergeProblemSeeds([bilingual], [insights[0]]);
    const html = renderSeoDocument({ problem, siteUrl: 'https://example.test' });
    expect(html).toContain('两数之和 &lt;测试&gt;');
    expect(html).not.toContain('<script>alert');
  });

  it('renders canonical and hreflang links for bilingual pages', () => {
    const [problem] = mergeProblemSeeds([bilingual], [insights[0]]);
    const html = renderSeoDocument({ problem, siteUrl: 'https://example.test' });
    expect(html).toContain('rel="canonical" href="https://example.test/problems/two-sum"');
    expect(html).toContain('hrefLang="en" href="https://example.test/en/problems/two-sum"');
    expect(html).toContain('application/ld+json');
  });

  it('excludes CN_ONLY problems from English sitemap URLs', () => {
    const problems = mergeProblemSeeds([bilingual, chineseOnly], insights);
    const urls = buildSitemapUrls(problems, 'https://example.test');
    expect(urls).toEqual([
      'https://example.test/problems',
      'https://example.test/problems/two-sum',
      'https://example.test/en/problems/two-sum',
      'https://example.test/problems/cn-only',
    ]);
    expect(urls).not.toContain('https://example.test/en/problems/cn-only');
  });

  it('renders a robots policy with sitemap location', () => {
    const robots = renderRobots('https://preview.example.test/');
    expect(robots).toContain('Disallow: /api/');
    expect(robots).toContain('Disallow: /admin/');
    expect(robots).toContain('Disallow: /password');
    expect(robots).toContain('Disallow: /oauth2/');
    expect(robots).toContain('Sitemap: https://preview.example.test/sitemap.xml');
  });
});
