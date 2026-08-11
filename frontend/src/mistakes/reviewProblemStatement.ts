import type { ReviewProblem } from '../types/api';

const LEADING_LEVEL_ONE_HEADING = /^\uFEFF?[ \t]{0,3}#(?!#)[ \t]+([^\r\n]*)(?:\r?\n|$)/;
const OPTIONAL_CLOSING_HEADING_MARKERS = /[ \t]+#+[ \t]*$/;
const LEADING_BLANK_LINES = /^(?:[ \t]*\r?\n)+/;

export function reviewProblemStatementMarkdown(
  problem: Pick<ReviewProblem, 'contentMarkdown' | 'title'>,
): string {
  const match = problem.contentMarkdown.match(LEADING_LEVEL_ONE_HEADING);
  if (!match) {
    return problem.contentMarkdown;
  }

  const heading = normalizeHeading(match[1]);
  if (heading !== normalizeHeading(problem.title)) {
    return problem.contentMarkdown;
  }

  return problem.contentMarkdown.slice(match[0].length).replace(LEADING_BLANK_LINES, '');
}

function normalizeHeading(value: string): string {
  return value
    .replace(OPTIONAL_CLOSING_HEADING_MARKERS, '')
    .trim()
    .replace(/\s+/g, ' ')
    .normalize('NFKC');
}
