import type { ReviewIntervalPreview, ReviewRating } from './api';

export type KnowledgeLearningState = { enrolled: boolean; phase?: string; dueAt?: string; isDue: boolean; lastRating?: string };
export type KnowledgeNode = { id: number; title: string; summary?: string; directCardCount: number; directArticleCount: number; subtreeCardCount: number; subtreeEnrolledCardCount: number };
export type KnowledgeTree = { node: KnowledgeNode; children: KnowledgeTree[] };
export type KnowledgeBreadcrumb = { id: number; title: string; slug: string };
export type KnowledgeNodeDetail = { node: KnowledgeNode; breadcrumbs: KnowledgeBreadcrumb[]; children: KnowledgeNode[] };
export type KnowledgeRelationType = 'prerequisites' | 'followups' | 'contrasts' | 'related';
export type KnowledgeRelation = { type: KnowledgeRelationType; slug: string; question: string };
export type KnowledgeCard = { slug: string; outlineNodeId: number; question: string; tags: string[]; learningState: KnowledgeLearningState; answerMarkdown?: string; explanationMarkdown?: string; relations?: KnowledgeRelation[] };
export type KnowledgeArticle = { id: number; outlineNodeId: number; title: string; bodyMarkdown?: string };
export type KnowledgePage<T> = { items: T[]; total: number; page: number; pageSize: number };
export type KnowledgeReviewSummary = { enrolledCount: number; dueCount: number; nextDueAt?: string };
export const KNOWLEDGE_RELATION_LABELS: Record<KnowledgeRelationType, string> = { prerequisites: '前置知识', followups: '继续追问', contrasts: '对比辨析', related: '相关卡片' };

/** 知识卡复习预览和评级结果，与后端 KnowledgeModels 保持一致。 */
export type KnowledgeReviewPreview = { cardSlug: string; enrolled: boolean; timezone: string; asOf: string; options: ReviewIntervalPreview[] };
export type KnowledgeReviewResult = { id: number; cardSlug: string; clientAttemptId: string; rating: ReviewRating; reviewedAt: string; dueAt: string; firstReview: boolean; duplicate: boolean };
