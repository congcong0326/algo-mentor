import { lazy } from 'react';

const loadPracticeSubmissionHistoryPage = () => import('./PracticeSubmissionHistoryPage');

/** 提前加载画像深链会用到的提交历史页代码分片。 */
export function preloadPracticeSubmissionHistoryPage(): void {
  void loadPracticeSubmissionHistoryPage();
}

export const PracticeSubmissionHistoryPage = lazy(loadPracticeSubmissionHistoryPage);
