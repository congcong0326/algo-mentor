import type {
  LearnerProfileCitation,
  LearnerProfileDocumentResponse,
  LearnerProfileEvidenceItem,
} from '../types/api';

export function userMessageEvidence(sourceId = 11): LearnerProfileEvidenceItem {
  return {
    type: 'USER_MESSAGE',
    sourceId,
    occurredAt: '2026-07-20T12:00:00Z',
    messageRole: 'DECLARED',
    codeReview: null,
    userMessage: { excerpt: '我希望在三个月内完成后端面试准备。' },
  };
}

export function codeReviewEvidence(sourceId = 12): LearnerProfileEvidenceItem {
  return {
    type: 'CODE_REVIEW',
    sourceId,
    occurredAt: '2026-07-21T12:00:00Z',
    reviewRole: 'OBSERVED',
    codeReview: {
      reviewId: sourceId,
      sessionId: 21,
      planId: 31,
      phaseIndex: 2,
      problemSlug: 'two-sum',
      versionNo: 3,
      totalScore: 8.5,
      passed: true,
    },
    userMessage: null,
  };
}

export function citation(
  displayNumber: number,
  statementRef = `statement-ref-${displayNumber}`,
): LearnerProfileCitation {
  return {
    displayNumber,
    statementRef,
    claimRevisionId: 100 + displayNumber,
    claimKey: `00000000-0000-0000-0000-${String(displayNumber).padStart(12, '0')}`,
    origin: 'USER_EXPLICIT',
    sourceSummary: '用户消息依据 1 条，正式代码复盘依据 1 条',
    evidenceCount: 3,
    previewEvidence: [userMessageEvidence(), codeReviewEvidence()],
  };
}

export function learnerProfileDocument(): LearnerProfileDocumentResponse {
  const firstCitation = citation(1);
  const secondCitation = citation(2);
  return {
    format: 'MARKDOWN_DOCUMENT_V1',
    projectorVersion: 'v1',
    locale: 'zh-CN',
    documentRevision: 'a'.repeat(64),
    title: '学习画像',
    blocks: [
      { type: 'HEADING', spans: [{ type: 'TEXT', text: '学习背景与目标' }] },
      {
        type: 'PARAGRAPH',
        spans: [
          { type: 'SUPPORTED_TEXT', text: '准备 Java 后端面试。', citationDisplayNumber: 1 },
          { type: 'TEXT', text: '；' },
          { type: 'SUPPORTED_TEXT', text: '编码前会先拆解状态。', citationDisplayNumber: 2 },
        ],
      },
    ],
    citationMap: {
      '1': firstCitation,
      '2': secondCitation,
    },
    updatedAt: '2026-07-21T12:00:00Z',
  };
}
