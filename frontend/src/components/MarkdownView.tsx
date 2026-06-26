import ReactMarkdown from 'react-markdown';
import rehypeRaw from 'rehype-raw';
import rehypeSanitize from 'rehype-sanitize';
import remarkGfm from 'remark-gfm';

const BOLD_LABEL_WITHOUT_SPACE = /(^|[\s([{"'“‘])\*\*([^*\n]+[:：])\*\*(?=\S)/g;
const BOLD_WITH_EXTRA_SPACE = /\*\*([ \t]*[^*\n]*?\S[^*\n]*?[ \t]*)\*\*/g;
const ITALIC_UNDERSCORE_WITH_EXTRA_SPACE = /(^|[^\p{L}\p{N}_])_([ \t]*[^_\n]*?\S[^_\n]*?[ \t]*)_/gu;
const ORPHANED_PUNCTUATION_UNDERSCORES = /_([，。！？、；：,.!?;:])_/g;
const ESCAPED_LINE_BREAK = /\\r\\n|\\n|\\r/g;
const LINE_BREAK = /(\r\n|\n|\r)/;
const FENCE_MARKER = /^ {0,3}(`{3,}|~{3,})/;
const INLINE_CODE_SPAN = /(`+)([\s\S]*?)\1/g;

interface MarkdownViewProps {
  content: string;
}

export function normalizeMarkdownContent(content: string): string {
  let inFence = false;
  let fenceChar = '';
  let fenceLength = 0;
  const markdownContent = normalizeEscapedLineBreaks(content);

  return markdownContent
    .split(LINE_BREAK)
    .map((part) => {
      if (LINE_BREAK.test(part)) {
        return part;
      }

      const fence = part.match(FENCE_MARKER)?.[1];
      if (fence) {
        if (!inFence) {
          inFence = true;
          fenceChar = fence[0];
          fenceLength = fence.length;
          return part;
        }
        if (fence[0] === fenceChar && fence.length >= fenceLength) {
          inFence = false;
          fenceChar = '';
          fenceLength = 0;
          return part;
        }
      }

      if (inFence || part.startsWith('    ') || part.startsWith('\t')) {
        return part;
      }

      return normalizeProseMarkdownLine(part);
    })
    .join('');
}

function normalizeProseMarkdownLine(line: string): string {
  let cursor = 0;
  let normalized = '';

  for (const codeSpan of line.matchAll(INLINE_CODE_SPAN)) {
    const start = codeSpan.index ?? 0;
    normalized += normalizeEmphasisSpacing(line.slice(cursor, start));
    normalized += codeSpan[0];
    cursor = start + codeSpan[0].length;
  }

  normalized += normalizeEmphasisSpacing(line.slice(cursor));
  return normalized;
}

function normalizeEmphasisSpacing(text: string): string {
  return text
    .replace(BOLD_WITH_EXTRA_SPACE, (_, content: string) => `**${content.trim()}**`)
    .replace(ORPHANED_PUNCTUATION_UNDERSCORES, '$1')
    .replace(ITALIC_UNDERSCORE_WITH_EXTRA_SPACE, (_, prefix: string, content: string) => (
      `${prefix}*${content.trim()}*`
    ))
    .replace(BOLD_LABEL_WITHOUT_SPACE, '$1**$2** ');
}

function normalizeEscapedLineBreaks(content: string): string {
  if (LINE_BREAK.test(content)) {
    return content;
  }

  const escapedLineBreaks = content.match(ESCAPED_LINE_BREAK);
  if (!escapedLineBreaks || escapedLineBreaks.length < 2) {
    return content;
  }

  return content.replace(ESCAPED_LINE_BREAK, (lineBreak) => {
    if (lineBreak === '\\r\\n') {
      return '\r\n';
    }
    if (lineBreak === '\\r') {
      return '\r';
    }
    return '\n';
  });
}

export default function MarkdownView({ content }: MarkdownViewProps) {
  const normalizedContent = normalizeMarkdownContent(content);

  return (
    <div className="markdown-view">
      <ReactMarkdown rehypePlugins={[rehypeRaw, rehypeSanitize]} remarkPlugins={[remarkGfm]}>
        {normalizedContent}
      </ReactMarkdown>
    </div>
  );
}
