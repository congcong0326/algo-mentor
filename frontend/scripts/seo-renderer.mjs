import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';
import React from 'react';
import { renderToStaticMarkup } from 'react-dom/server';

const SCRIPT_DIR = path.dirname(fileURLToPath(import.meta.url));
const FRONTEND_DIR = path.resolve(SCRIPT_DIR, '..');
const REPOSITORY_DIR = path.resolve(FRONTEND_DIR, '..');
const DEFAULT_SITE_URL = 'https://leetmentor.com';
const SLUG_PATTERN = /^[A-Za-z0-9][A-Za-z0-9_-]*$/;
const CONTENT_STATUSES = new Set(['BILINGUAL', 'CN_ONLY']);
const DIFFICULTIES = new Set(['EASY', 'MEDIUM', 'HARD']);

export const DIFFICULTY_LABELS = {
  zh: { EASY: '简单', MEDIUM: '中等', HARD: '困难' },
  en: { EASY: 'Easy', MEDIUM: 'Medium', HARD: 'Hard' },
};

export function escapeHtml(value) {
  return String(value)
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#39;');
}

export function problemPath(slug, locale = 'zh') {
  assertValidSlug(slug);
  return locale === 'en' ? `/en/problems/${slug}` : `/problems/${slug}`;
}

export function assertValidSlug(slug) {
  if (typeof slug !== 'string' || !SLUG_PATTERN.test(slug)) {
    throw new Error(`Invalid problem slug: ${JSON.stringify(slug)}`);
  }
}

export function selectPublishedProblems(problems) {
  return problems.filter((problem) => {
    if (!CONTENT_STATUSES.has(problem.contentStatus)) {
      throw new Error(`Invalid contentStatus for ${problem.slug}: ${problem.contentStatus}`);
    }
    return true;
  });
}

export function mergeProblemSeeds(problems, insights) {
  const seen = new Set();
  const insightBySlug = new Map();
  for (const insight of insights) {
    assertValidSlug(insight.slug);
    if (insightBySlug.has(insight.slug)) {
      throw new Error(`Duplicate problem insight slug: ${insight.slug}`);
    }
    if (!isNonEmptyString(insight.reasonZH) || !isNonEmptyString(insight.reasonEN)) {
      throw new Error(`Missing learning insight for ${insight.slug}`);
    }
    insightBySlug.set(insight.slug, insight);
  }

  const merged = problems.map((problem) => {
    assertValidSlug(problem.slug);
    if (seen.has(problem.slug)) throw new Error(`Duplicate problem slug: ${problem.slug}`);
    seen.add(problem.slug);
    validateProblem(problem);
    const insight = insightBySlug.get(problem.slug);
    if (!insight) throw new Error(`Missing learning insight for ${problem.slug}`);
    return {
      ...problem,
      displayId: problem.frontendDisplayId || String(problem.frontendId),
      reasonZh: insight.reasonZH,
      reasonEn: insight.reasonEN,
    };
  });
  if (insightBySlug.size !== merged.length) {
    const extra = [...insightBySlug.keys()].filter((slug) => !seen.has(slug));
    throw new Error(`Insights do not match problem seed: ${extra.join(', ')}`);
  }
  return merged;
}

export function buildSitemapUrls(problems, siteUrl = DEFAULT_SITE_URL) {
  const baseUrl = normalizeSiteUrl(siteUrl);
  const urls = [`${baseUrl}/problems`];
  for (const problem of problems) {
    urls.push(`${baseUrl}${problemPath(problem.slug)}`);
    if (problem.contentStatus === 'BILINGUAL') {
      urls.push(`${baseUrl}${problemPath(problem.slug, 'en')}`);
    }
  }
  return [...new Set(urls)];
}

export function renderSitemap(urls) {
  return `<?xml version="1.0" encoding="UTF-8"?>\n<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">\n${urls.map((url) => `  <url><loc>${escapeHtml(url)}</loc></url>`).join('\n')}\n</urlset>\n`;
}

export function renderRobots(siteUrl = DEFAULT_SITE_URL) {
  const baseUrl = normalizeSiteUrl(siteUrl);
  return `User-agent: *\nAllow: /\nDisallow: /api/\nDisallow: /admin/\nDisallow: /me\nDisallow: /settings\nDisallow: /password\nDisallow: /learning-plans\nDisallow: /mistakes\nDisallow: /oauth2/\nDisallow: /login/oauth2/\n\nSitemap: ${baseUrl}/sitemap.xml\n`;
}

export function renderSeoDocument({ problem, locale = 'zh', problems = [], siteUrl = DEFAULT_SITE_URL }) {
  const baseUrl = normalizeSiteUrl(siteUrl);
  const isEnglish = locale === 'en';
  const title = problem
    ? `${isEnglish ? problem.titleEn : problem.titleZh} - Leet Mentor`
    : isEnglish ? 'Algorithm Problems and Learning Paths - Leet Mentor' : '算法题库与学习路径 - Leet Mentor';
  const description = problem
    ? isEnglish ? `${problem.titleEn}: problem ${problem.displayId}. Learn the core pattern with Leet Mentor's focused study insight.` : `${problem.titleZh}：第 ${problem.displayId} 题。使用 Leet Mentor 的学习提示，掌握题型思路与解题方法。`
    : isEnglish ? 'Explore curated algorithm problems, learning insights, and practice paths with Leet Mentor.' : '在 Leet Mentor 题库中发现算法题、学习提示和适合自己的练习路径。';
  const canonicalPath = problem ? problemPath(problem.slug, isEnglish ? 'en' : 'zh') : '/problems';
  const canonical = `${baseUrl}${canonicalPath}`;
  const alternateChinese = problem ? `${baseUrl}${problemPath(problem.slug)}` : `${baseUrl}/problems`;
  const alternateEnglish = problem?.contentStatus === 'BILINGUAL' ? `${baseUrl}${problemPath(problem.slug, 'en')}` : null;
  const jsonLd = problem ? {
    '@context': 'https://schema.org',
    '@type': 'LearningResource',
    name: isEnglish ? problem.titleEn : problem.titleZh,
    url: canonical,
    inLanguage: isEnglish ? 'en' : 'zh-CN',
    learningResourceType: 'Algorithm problem study guide',
    isAccessibleForFree: true,
    educationalLevel: DIFFICULTY_LABELS[isEnglish ? 'en' : 'zh'][problem.difficulty],
    keywords: (isEnglish ? problem.tagLabelsEn : problem.tagLabelsZh).join(', '),
  } : {
    '@context': 'https://schema.org',
    '@type': 'CollectionPage',
    name: title,
    url: canonical,
    inLanguage: isEnglish ? 'en' : 'zh-CN',
  };
  const content = problem
    ? React.createElement(ProblemPage, { problem, isEnglish, canonical, alternateChinese, alternateEnglish, title, description, jsonLd })
    : React.createElement(ProblemIndexPage, { problems, baseUrl, isEnglish, canonical, title, description, jsonLd });
  return `<!doctype html>${renderToStaticMarkup(React.createElement('html', { lang: isEnglish ? 'en' : 'zh-CN' }, content))}`;
}

function ProblemPage({ problem, isEnglish, canonical, alternateChinese, alternateEnglish, title, description, jsonLd }) {
  const language = isEnglish ? 'en' : 'zh';
  const tags = isEnglish ? problem.tagLabelsEn : problem.tagLabelsZh;
  const learningReason = isEnglish ? problem.reasonEn : problem.reasonZh;
  const titleText = isEnglish ? problem.titleEn : problem.titleZh;
  const leetcodeLabel = isEnglish ? 'Open on LeetCode' : '在 LeetCode 查看题目';
  return React.createElement(React.Fragment, null,
    React.createElement('head', null,
      React.createElement('meta', { charSet: 'UTF-8' }),
      React.createElement('meta', { name: 'viewport', content: 'width=device-width, initial-scale=1.0' }),
      React.createElement('meta', { name: 'description', content: description }),
      React.createElement('link', { rel: 'canonical', href: canonical }),
      React.createElement('link', { rel: 'alternate', hrefLang: 'zh-CN', href: alternateChinese }),
      alternateEnglish ? React.createElement('link', { rel: 'alternate', hrefLang: 'en', href: alternateEnglish }) : null,
      React.createElement('link', { rel: 'alternate', hrefLang: 'x-default', href: alternateChinese }),
      React.createElement('meta', { property: 'og:type', content: 'article' }),
      React.createElement('meta', { property: 'og:title', content: title }),
      React.createElement('meta', { property: 'og:description', content: description }),
      React.createElement('meta', { property: 'og:url', content: canonical }),
      React.createElement('meta', { property: 'og:site_name', content: 'Leet Mentor' }),
      React.createElement('title', null, title),
      React.createElement('script', { type: 'application/ld+json', dangerouslySetInnerHTML: { __html: safeJsonLd(jsonLd) } }),
      React.createElement('style', { dangerouslySetInnerHTML: { __html: SEO_STYLE } }),
    ),
    React.createElement('body', null,
      React.createElement('header', { className: 'seo-header' },
        React.createElement('a', { className: 'seo-brand', href: isEnglish ? '/en/problems' : '/problems' }, 'Leet Mentor'),
        React.createElement('a', { className: 'seo-login', href: '/login' }, isEnglish ? 'Sign in' : '登录'),
      ),
      React.createElement('main', { className: 'seo-main' },
        React.createElement('p', { className: 'seo-kicker' }, isEnglish ? `Problem ${problem.displayId}` : `第 ${problem.displayId} 题`),
        React.createElement('h1', null, titleText),
        React.createElement('div', { className: 'seo-meta' },
          React.createElement('span', { className: `seo-difficulty ${problem.difficulty.toLowerCase()}` }, DIFFICULTY_LABELS[language][problem.difficulty]),
          ...tags.map((tag) => React.createElement('span', { className: 'seo-tag', key: tag }, tag)),
        ),
        React.createElement('section', { className: 'seo-section', 'aria-labelledby': 'learning-insight' },
          React.createElement('h2', { id: 'learning-insight' }, isEnglish ? 'What this problem teaches' : '这道题适合怎样学习'),
          React.createElement('p', null, learningReason),
        ),
        React.createElement('section', { className: 'seo-section', 'aria-labelledby': 'mentor-value' },
          React.createElement('h2', { id: 'mentor-value' }, isEnglish ? 'Practice with a mentor' : '用学习路径把练习串起来'),
          React.createElement('p', null, isEnglish ? 'Leet Mentor helps you turn isolated problem solving into a repeatable learning path with review, practice, and AI explanations.' : 'Leet Mentor 将零散刷题整理成可复习、可练习、可获得 AI 讲解的个人学习路径。'),
          React.createElement('a', { className: 'seo-cta', href: '/login' }, isEnglish ? 'Start learning' : '开始学习'),
        ),
        React.createElement('p', { className: 'seo-source' },
          React.createElement('a', { href: problem.leetcodeUrl, rel: 'nofollow noopener noreferrer' }, leetcodeLabel),
        ),
      ),
      React.createElement('footer', { className: 'seo-footer' }, `Leet Mentor · ${isEnglish ? 'Focused algorithm learning' : '专注算法学习'}`),
    ),
  );
}

function ProblemIndexPage({ problems, baseUrl, isEnglish, canonical, title, description, jsonLd }) {
  const visibleProblems = isEnglish ? problems.filter((problem) => problem.contentStatus === 'BILINGUAL') : problems;
  return React.createElement(React.Fragment, null,
    React.createElement('head', null,
      React.createElement('meta', { charSet: 'UTF-8' }),
      React.createElement('meta', { name: 'viewport', content: 'width=device-width, initial-scale=1.0' }),
      React.createElement('meta', { name: 'description', content: description }),
      React.createElement('link', { rel: 'canonical', href: canonical }),
      React.createElement('meta', { property: 'og:type', content: 'website' }),
      React.createElement('meta', { property: 'og:title', content: title }),
      React.createElement('meta', { property: 'og:description', content: description }),
      React.createElement('meta', { property: 'og:url', content: canonical }),
      React.createElement('meta', { property: 'og:site_name', content: 'Leet Mentor' }),
      React.createElement('title', null, title),
      React.createElement('script', { type: 'application/ld+json', dangerouslySetInnerHTML: { __html: safeJsonLd(jsonLd) } }),
      React.createElement('style', { dangerouslySetInnerHTML: { __html: SEO_STYLE } }),
    ),
    React.createElement('body', null,
      React.createElement('header', { className: 'seo-header' },
        React.createElement('a', { className: 'seo-brand', href: isEnglish ? '/en/problems' : '/problems' }, 'Leet Mentor'),
        React.createElement('a', { className: 'seo-login', href: '/login' }, isEnglish ? 'Sign in' : '登录'),
      ),
      React.createElement('main', { className: 'seo-main seo-index' },
        React.createElement('p', { className: 'seo-kicker' }, isEnglish ? 'Algorithm problem library' : '算法题库'),
        React.createElement('h1', null, isEnglish ? 'Algorithm problems for deliberate practice' : '为有效练习整理的算法题库'),
        React.createElement('p', { className: 'seo-lead' }, description),
        React.createElement('div', { className: 'seo-index-grid' }, visibleProblems.map((problem) => {
          const problemTitle = isEnglish ? problem.titleEn : problem.titleZh;
          return React.createElement('a', { className: 'seo-index-item', href: `${baseUrl === DEFAULT_SITE_URL ? '' : baseUrl}${problemPath(problem.slug, isEnglish ? 'en' : 'zh')}`, key: problem.slug },
            React.createElement('strong', null, problemTitle),
            React.createElement('span', null, `${problem.displayId} · ${DIFFICULTY_LABELS[isEnglish ? 'en' : 'zh'][problem.difficulty]}`),
          );
        })),
      ),
    ),
  );
}

function validateProblem(problem) {
  if (!CONTENT_STATUSES.has(problem.contentStatus)) throw new Error(`Invalid contentStatus for ${problem.slug}`);
  if (!DIFFICULTIES.has(problem.difficulty)) throw new Error(`Invalid difficulty for ${problem.slug}`);
  if (!isNonEmptyString(problem.titleZh)) throw new Error(`Missing Chinese title for ${problem.slug}`);
  if (problem.contentStatus === 'BILINGUAL' && !isNonEmptyString(problem.titleEn)) throw new Error(`Missing English title for ${problem.slug}`);
  if (!isNonEmptyString(problem.frontendDisplayId) && !Number.isInteger(problem.frontendId)) throw new Error(`Missing problem number for ${problem.slug}`);
  if (!Array.isArray(problem.tagLabelsZh) || !Array.isArray(problem.tagLabelsEn)) throw new Error(`Invalid tags for ${problem.slug}`);
  if (!isNonEmptyString(problem.leetcodeUrl) || !/^https?:\/\//.test(problem.leetcodeUrl)) throw new Error(`Invalid LeetCode URL for ${problem.slug}`);
}

function isNonEmptyString(value) {
  return typeof value === 'string' && value.trim().length > 0;
}

function safeJsonLd(value) {
  return JSON.stringify(value).replaceAll('<', '\\u003c').replaceAll('>', '\\u003e').replaceAll('&', '\\u0026');
}

function normalizeSiteUrl(siteUrl) {
  const value = String(siteUrl || DEFAULT_SITE_URL).trim().replace(/\/+$/, '');
  if (!/^https?:\/\//.test(value)) throw new Error(`Invalid SEO site URL: ${siteUrl}`);
  return value;
}

function parseJsonLines(filePath) {
  return fs.readFileSync(filePath, 'utf8').split(/\r?\n/).filter(Boolean).map((line, index) => {
    try { return JSON.parse(line); } catch (error) { throw new Error(`Invalid JSON at ${filePath}:${index + 1}: ${error.message}`); }
  });
}

function readBuildData() {
  const problems = parseJsonLines(path.join(REPOSITORY_DIR, 'data/seed/problems.jsonl'));
  const insights = JSON.parse(fs.readFileSync(path.join(REPOSITORY_DIR, 'data/problem-insight-seed/problem_reasons.json'), 'utf8'));
  const manifest = JSON.parse(fs.readFileSync(path.join(REPOSITORY_DIR, 'data/seed/manifest.json'), 'utf8'));
  return { problems: mergeProblemSeeds(selectPublishedProblems(problems), insights), sourceCommit: manifest.sourceCommit || 'unknown' };
}

export function generateSeoPages({ outputDir = path.join(FRONTEND_DIR, 'dist'), siteUrl = process.env.SEO_SITE_URL || process.env.VITE_SITE_URL || DEFAULT_SITE_URL, generatedAt = new Date().toISOString() } = {}) {
  const { problems, sourceCommit } = readBuildData();
  fs.mkdirSync(outputDir, { recursive: true });
  writePage(outputDir, 'problems/index.html', renderSeoDocument({ problems, siteUrl }));
  for (const problem of problems) {
    writePage(outputDir, `problems/${problem.slug}/index.html`, renderSeoDocument({ problem, siteUrl }));
    if (problem.contentStatus === 'BILINGUAL') {
      writePage(outputDir, `en/problems/${problem.slug}/index.html`, renderSeoDocument({ problem, locale: 'en', siteUrl }));
    }
  }
  const urls = buildSitemapUrls(problems, siteUrl);
  fs.writeFileSync(path.join(outputDir, 'sitemap.xml'), renderSitemap(urls));
  fs.writeFileSync(path.join(outputDir, 'robots.txt'), renderRobots(siteUrl));
  fs.writeFileSync(path.join(outputDir, 'seo-manifest.json'), `${JSON.stringify({ generatedAt, problemCount: problems.length, bilingualProblemCount: problems.filter((problem) => problem.contentStatus === 'BILINGUAL').length, sourceCommit }, null, 2)}\n`);
  return { problemCount: problems.length, bilingualProblemCount: problems.filter((problem) => problem.contentStatus === 'BILINGUAL').length, urls, sourceCommit };
}

function writePage(outputDir, relativePath, content) {
  const target = path.join(outputDir, relativePath);
  fs.mkdirSync(path.dirname(target), { recursive: true });
  fs.writeFileSync(target, content);
}

const SEO_STYLE = `
:root { color-scheme: light; font-family: Inter, ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif; color: #172033; background: #f7f8fb; }
* { box-sizing: border-box; }
body { margin: 0; }
a { color: inherit; }
.seo-header { align-items: center; background: #fff; border-bottom: 1px solid #e6e9ef; display: flex; justify-content: space-between; padding: 18px max(24px, calc((100% - 1040px) / 2)); }
.seo-brand { color: #172033; font-size: 1.15rem; font-weight: 800; text-decoration: none; }
.seo-login { color: #705400; font-weight: 700; text-decoration: none; }
.seo-main { margin: 0 auto; max-width: 820px; padding: 76px 24px 92px; }
.seo-kicker { color: #7c5b00; font-size: .82rem; font-weight: 800; letter-spacing: .08em; margin: 0 0 12px; text-transform: uppercase; }
h1 { font-size: clamp(2.1rem, 5vw, 3.6rem); letter-spacing: -.03em; line-height: 1.08; margin: 0 0 22px; }
h2 { font-size: 1.15rem; margin: 0 0 10px; }
.seo-meta { display: flex; flex-wrap: wrap; gap: 8px; margin-bottom: 42px; }
.seo-difficulty, .seo-tag { border: 1px solid #d9dee8; border-radius: 999px; font-size: .85rem; font-weight: 700; padding: 6px 10px; }
.seo-difficulty.easy { background: #ecfdf3; border-color: #bbf7d0; color: #166534; }
.seo-difficulty.medium { background: #fff8dd; border-color: #fde68a; color: #855d00; }
.seo-difficulty.hard { background: #fff1f2; border-color: #fecdd3; color: #9f1239; }
.seo-section { border-top: 1px solid #e1e5ec; padding: 28px 0; }
.seo-section p, .seo-lead { color: #4c596e; font-size: 1.08rem; line-height: 1.75; margin: 0; }
.seo-cta { background: #ffc01e; border-radius: 8px; display: inline-block; font-weight: 800; margin-top: 20px; padding: 12px 18px; text-decoration: none; }
.seo-source { margin-top: 34px; }
.seo-source a { color: #755800; font-weight: 700; }
.seo-footer { border-top: 1px solid #e6e9ef; color: #748096; font-size: .9rem; padding: 24px; text-align: center; }
.seo-index { max-width: 1100px; }
.seo-index-grid { display: grid; gap: 10px; grid-template-columns: repeat(auto-fill, minmax(240px, 1fr)); margin-top: 38px; }
.seo-index-item { background: #fff; border: 1px solid #e2e6ee; border-radius: 8px; display: flex; flex-direction: column; gap: 7px; padding: 15px; text-decoration: none; }
.seo-index-item:hover { border-color: #c69a00; }
.seo-index-item span { color: #748096; font-size: .85rem; }
@media (max-width: 640px) { .seo-main { padding-top: 48px; } .seo-header { padding-left: 18px; padding-right: 18px; } }
`;

const isMain = process.argv[1] && import.meta.url === pathToFileURL(path.resolve(process.argv[1])).href;
if (isMain) {
  const result = generateSeoPages();
  console.log(`Generated SEO pages for ${result.problemCount} problems (${result.bilingualProblemCount} English pages).`);
}
