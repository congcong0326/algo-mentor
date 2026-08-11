import Prism from 'prismjs';
import 'prismjs/components/prism-bash';
import 'prismjs/components/prism-c';
import 'prismjs/components/prism-cpp';
import 'prismjs/components/prism-csharp';
import 'prismjs/components/prism-go';
import 'prismjs/components/prism-java';
import 'prismjs/components/prism-json';
import 'prismjs/components/prism-jsx';
import 'prismjs/components/prism-kotlin';
import 'prismjs/components/prism-markdown';
import 'prismjs/components/prism-python';
import 'prismjs/components/prism-rust';
import 'prismjs/components/prism-sql';
import 'prismjs/components/prism-swift';
import 'prismjs/components/prism-tsx';
import 'prismjs/components/prism-typescript';
import 'prismjs/components/prism-yaml';
import { Fragment, memo } from 'react';
import type { ComponentProps, ReactNode } from 'react';

const CODE_LANGUAGE_ALIASES: Readonly<Record<string, string>> = {
  'c#': 'csharp',
  'c++': 'cpp',
  bash: 'bash',
  c: 'c',
  cpp: 'cpp',
  csharp: 'csharp',
  cs: 'csharp',
  go: 'go',
  golang: 'go',
  html: 'markup',
  java: 'java',
  javascript: 'javascript',
  js: 'javascript',
  json: 'json',
  jsx: 'jsx',
  kotlin: 'kotlin',
  kt: 'kotlin',
  markdown: 'markdown',
  md: 'markdown',
  py: 'python',
  python: 'python',
  python3: 'python',
  rs: 'rust',
  rust: 'rust',
  sh: 'bash',
  shell: 'bash',
  sql: 'sql',
  swift: 'swift',
  ts: 'typescript',
  tsx: 'tsx',
  typescript: 'typescript',
  xml: 'markup',
  yaml: 'yaml',
  yml: 'yaml',
};

interface SyntaxHighlightedCodeProps extends Omit<ComponentProps<'code'>, 'children'> {
  code: string;
  language?: string;
}

export function resolveCodeLanguage(language: string | undefined): string | undefined {
  if (!language?.trim()) {
    return undefined;
  }

  const normalized = language.trim().toLowerCase().replace(/^language-/, '');
  const resolved = CODE_LANGUAGE_ALIASES[normalized];
  return resolved && Prism.languages[resolved] ? resolved : undefined;
}

function SyntaxHighlightedCode({ className, code, language, ...props }: SyntaxHighlightedCodeProps) {
  const resolvedLanguage = resolveCodeLanguage(language);
  const grammar = resolvedLanguage ? Prism.languages[resolvedLanguage] : undefined;
  const codeClassName = Array.from(new Set([
    className,
    resolvedLanguage ? `language-${resolvedLanguage}` : undefined,
    grammar ? 'syntax-highlighted-code' : undefined,
  ].filter((value): value is string => Boolean(value)))).join(' ');

  return (
    <code
      {...props}
      className={codeClassName || undefined}
      data-language={resolvedLanguage}
    >
      {grammar ? renderTokenStream(Prism.tokenize(code, grammar), 'syntax') : code}
    </code>
  );
}

function renderTokenStream(stream: Prism.TokenStream, keyPrefix: string): ReactNode {
  if (typeof stream === 'string') {
    return stream;
  }

  if (Array.isArray(stream)) {
    return stream.map((token, index) => (
      <Fragment key={`${keyPrefix}-${index}`}>
        {renderTokenStream(token, `${keyPrefix}-${index}`)}
      </Fragment>
    ));
  }

  const aliases = Array.isArray(stream.alias)
    ? stream.alias
    : stream.alias
      ? [stream.alias]
      : [];

  return (
    <span className={['token', stream.type, ...aliases].join(' ')}>
      {renderTokenStream(stream.content, `${keyPrefix}-${stream.type}`)}
    </span>
  );
}

export default memo(SyntaxHighlightedCode);
