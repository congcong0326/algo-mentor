import type {
  ProblemAlgorithmKey,
  ProblemComplexityKey,
  ProblemDataStructureKey,
  ProblemSolutionOutlineV1,
} from '../types/api';

export const problemDataStructureOptions: Array<{ key: ProblemDataStructureKey; label: string }> = [
  { key: 'ARRAY', label: '数组' },
  { key: 'HASH_MAP', label: '哈希表' },
  { key: 'LINKED_LIST', label: '链表' },
  { key: 'STACK', label: '栈' },
  { key: 'QUEUE', label: '队列' },
  { key: 'HEAP', label: '堆' },
  { key: 'TREE', label: '树' },
  { key: 'GRAPH', label: '图' },
  { key: 'TRIE', label: '字典树' },
  { key: 'UNION_FIND', label: '并查集' },
  { key: 'OTHER', label: '其他' },
];

export const problemAlgorithmOptions: Array<{ key: ProblemAlgorithmKey; label: string }> = [
  { key: 'TWO_POINTERS', label: '双指针' },
  { key: 'SLIDING_WINDOW', label: '滑动窗口' },
  { key: 'BINARY_SEARCH', label: '二分查找' },
  { key: 'DFS', label: '深度优先搜索' },
  { key: 'BFS', label: '广度优先搜索' },
  { key: 'BACKTRACKING', label: '回溯' },
  { key: 'GREEDY', label: '贪心' },
  { key: 'DYNAMIC_PROGRAMMING', label: '动态规划' },
  { key: 'PREFIX_SUM', label: '前缀和' },
  { key: 'SORTING', label: '排序' },
  { key: 'MONOTONIC_STACK', label: '单调栈' },
  { key: 'DIJKSTRA', label: 'Dijkstra' },
  { key: 'OTHER', label: '其他' },
];

export const problemComplexityOptions: Array<{ key: ProblemComplexityKey; label: string }> = [
  { key: 'O_1', label: 'O(1)' },
  { key: 'O_LOG_N', label: 'O(log n)' },
  { key: 'O_N', label: 'O(n)' },
  { key: 'O_N_LOG_N', label: 'O(n log n)' },
  { key: 'O_N2', label: 'O(n²)' },
  { key: 'O_N3', label: 'O(n³)' },
  { key: 'O_2N', label: 'O(2ⁿ)' },
  { key: 'OTHER', label: '其他' },
];

export function emptyProblemSolutionOutline(): ProblemSolutionOutlineV1 {
  return {
    schemaVersion: 1,
    coreIdea: '',
    dataStructures: [],
    customDataStructures: [],
    dataStructureNotes: '',
    algorithms: [],
    customAlgorithms: [],
    algorithmNotes: '',
    timeComplexity: { key: null, customText: null },
    spaceComplexity: { key: null, customText: null },
    edgeCases: '',
  };
}
