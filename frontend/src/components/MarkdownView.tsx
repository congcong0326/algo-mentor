import ReactMarkdown from 'react-markdown';
import rehypeRaw from 'rehype-raw';
import rehypeSanitize from 'rehype-sanitize';
import remarkGfm from 'remark-gfm';
import type { ExtraProps } from 'react-markdown';
import type { ComponentProps, ReactNode } from 'react';
import { useEffect, useId, useState } from 'react';
import mermaid from 'mermaid';
import SyntaxHighlightedCode from './SyntaxHighlightedCode';

const BOLD_LABEL_WITHOUT_SPACE = /(^|[\s([{"'“‘])\*\*([^*\n]+[:：])\*\*(?=\S)/g;
const BOLD_WITH_EXTRA_SPACE = /\*\*([ \t]*[^*\n]*?\S[^*\n]*?[ \t]*)\*\*/g;
const ITALIC_UNDERSCORE_WITH_EXTRA_SPACE = /(^|[^\p{L}\p{N}_])_([ \t]*[^_\n]*?\S[^_\n]*?[ \t]*)_/gu;
const ORPHANED_PUNCTUATION_UNDERSCORES = /_([，。！？、；：,.!?;:])_/g;
const ESCAPED_LINE_BREAK = /\\r\\n|\\n|\\r/g;
const LINE_BREAK = /(\r\n|\n|\r)/;
const FENCE_MARKER = /^ {0,3}(`{3,}|~{3,})/;
const INLINE_CODE_SPAN = /(`+)([\s\S]*?)\1/g;
const INLINE_CODE_SUPERSCRIPT = /<sup>([\s\S]*?)<\/sup>/g;
const CODE_LANGUAGE_CLASS = /(?:^|\s)language-([^\s]+)/;
const MERMAID_LANGUAGE = 'mermaid';

mermaid.initialize({
  startOnLoad: false,
  securityLevel: 'strict',
  htmlLabels: false,
  flowchart: { padding: 32, diagramPadding: 24, useMaxWidth: false },
});

interface MarkdownViewProps {
  content: string;
  defaultCodeLanguage?: string;
}

function isMermaidLanguage(className: string): boolean {
  return className.match(CODE_LANGUAGE_CLASS)?.[1]?.toLowerCase() === MERMAID_LANGUAGE;
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

function MarkdownCode({
  children,
  defaultCodeLanguage,
  node,
  ...props
}: ComponentProps<'code'> & ExtraProps & { defaultCodeLanguage?: string }) {
  const code = typeof children === 'string' ? children : String(children ?? '');
  const fencedLanguage = props.className?.match(CODE_LANGUAGE_CLASS)?.[1];
  const isInlineCode = !fencedLanguage
    && !code.endsWith('\n')
    && node?.position?.start.line === node?.position?.end.line;
  const renderedChildren = isInlineCode && typeof children === 'string'
    ? renderInlineCodeSuperscripts(children)
    : children;

  if (isInlineCode) {
    return <code {...props}>{renderedChildren}</code>;
  }

  if (isMermaidLanguage(props.className ?? '')) {
    return <MermaidDiagram source={code.replace(/\n$/, '')} />;
  }

  return (
    <SyntaxHighlightedCode
      {...props}
      code={code.replace(/\n$/, '')}
      language={fencedLanguage ?? defaultCodeLanguage}
    />
  );
}

function MarkdownPre({ children, node, ...props }: ComponentProps<'pre'> & ExtraProps) {
  const codeNode = node?.children[0];
  const className = codeNode?.type === 'element' ? codeNode.properties.className : undefined;
  const codeClassName = Array.isArray(className) ? className.join(' ') : String(className ?? '');

  // 图表不能继承代码块的等宽字体，否则标签宽度与 Mermaid 测量结果不同。
  if (isMermaidLanguage(codeClassName)) {
    return <>{children}</>;
  }

  return <pre {...props}>{children}</pre>;
}

function MermaidDiagram({ source }: { source: string }) {
  const id = `mermaid-${useId().replace(/:/g, '')}`;
  const [svg, setSvg] = useState<string>();
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    let active = true;
    setSvg(undefined);
    setFailed(false);
    void mermaid.render(id, source)
      .then(({ svg: renderedSvg }) => {
        if (active) setSvg(renderedSvg);
      })
      .catch(() => {
        if (active) setFailed(true);
      });
    return () => { active = false; };
  }, [id, source]);

  if (failed) {
    return <pre className="mermaid-fallback"><code>{source}</code></pre>;
  }
  if (!svg) {
    return <div className="mermaid-diagram" role="status">正在绘制图表…</div>;
  }

  return <div className="mermaid-diagram" dangerouslySetInnerHTML={{ __html: svg }} />;
}

function renderInlineCodeSuperscripts(content: string): ReactNode {
  const segments = content.split(INLINE_CODE_SUPERSCRIPT);
  if (segments.length === 1) {
    return content;
  }

  return segments.map((segment, index) => (
    index % 2 === 0 ? segment : <sup key={index}>{segment}</sup>
  ));
}

export default function MarkdownView({ content, defaultCodeLanguage }: MarkdownViewProps) {
  const normalizedContent = normalizeMarkdownContent(content);

  return (
    <div className="markdown-view">
      <ReactMarkdown
        components={{
          code: (props) => <MarkdownCode {...props} defaultCodeLanguage={defaultCodeLanguage} />,
          pre: MarkdownPre,
        }}
        rehypePlugins={[rehypeRaw, rehypeSanitize]}
        remarkPlugins={[remarkGfm]}
      >
        {normalizedContent}
      </ReactMarkdown>
    </div>
  );
}
