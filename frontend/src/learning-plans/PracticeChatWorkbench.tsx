import { ArrowLeft } from 'lucide-react';
import { useEffect, useState } from 'react';
import ReactMarkdown from 'react-markdown';
import rehypeRaw from 'rehype-raw';
import rehypeSanitize from 'rehype-sanitize';
import remarkGfm from 'remark-gfm';
import { getProblemDetail } from '../services/api';
import type { LearningPlanDetailResponse, LearningPlanProblemDraft, ProblemDetail } from '../types/api';

function problemLabel(problem?: LearningPlanProblemDraft): string {
  if (!problem) {
    return '题目训练';
  }
  const id = problem.frontendId ? `${problem.frontendId}. ` : '';
  return `${id}${problem.titleCn || problem.title}`;
}

function MarkdownMessage({ content }: { content: string }) {
  return (
    <div className="markdown-view">
      <ReactMarkdown rehypePlugins={[rehypeRaw, rehypeSanitize]} remarkPlugins={[remarkGfm]}>
        {content}
      </ReactMarkdown>
    </div>
  );
}

export default function PracticeChatWorkbench({
  onBack,
  phaseIndex,
  plan,
  problemSlug,
}: {
  onBack: () => void;
  phaseIndex: number;
  plan: LearningPlanDetailResponse;
  problemSlug: string;
}) {
  const phase = plan.phases.find((candidate) => candidate.phaseIndex === phaseIndex);
  const problem = phase?.problems.find((candidate) => candidate.slug === problemSlug);
  const [problemDetail, setProblemDetail] = useState<ProblemDetail>();
  const [problemError, setProblemError] = useState('');

  useEffect(() => {
    const controller = new AbortController();
    setProblemDetail(undefined);
    setProblemError('');

    getProblemDetail(problemSlug, controller.signal)
      .then((response) => {
        if (!response.success || !response.data) {
          throw new Error(response.error?.message ?? '题目详情加载失败');
        }
        setProblemDetail(response.data);
      })
      .catch((error) => {
        if (!controller.signal.aborted) {
          setProblemError(error instanceof Error ? error.message : '题目详情加载失败');
        }
      });

    return () => controller.abort();
  }, [problemSlug]);

  const statement = problemDetail?.contentMarkdown.trim() || '题面暂未收录。';

  return (
    <article className="practice-workbench" aria-labelledby="practice-workbench-title">
      <header className="practice-toolbar">
        <div className="practice-toolbar-main">
          <button className="secondary-button compact detail-back-button" onClick={onBack} type="button">
            <ArrowLeft aria-hidden="true" />
            <span>返回方案</span>
          </button>
          <div>
            <p className="eyebrow">{phase?.title ?? `第 ${phaseIndex} 阶段`}</p>
            <h2 id="practice-workbench-title">{problemLabel(problem)}</h2>
          </div>
        </div>
        <div className="practice-toolbar-actions">
          <span className={`difficulty-badge ${String(problem?.difficulty ?? '').toLowerCase()}`}>
            {problem?.difficulty ?? '-'}
          </span>
          <span className="status-badge">未开始</span>
        </div>
      </header>

      <section className="practice-message-list" aria-label="聊天消息">
        <article className="practice-message assistant-message">
          <span>教练</span>
          <MarkdownMessage content={problemError || (problemDetail ? statement : '正在加载题面...')} />
        </article>
      </section>

      <form className="practice-composer" aria-label="发送消息">
        <input aria-label="输入你的思路、问题、代码或 LeetCode 反馈" disabled placeholder="输入你的思路、问题、代码或 LeetCode 反馈..." />
        <button className="primary-button compact" disabled type="button">发送</button>
      </form>
    </article>
  );
}
