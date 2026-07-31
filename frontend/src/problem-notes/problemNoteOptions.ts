import type {
  ProblemAlgorithmKey,
  ProblemComplexityKey,
  ProblemDataStructureKey,
  ProblemSolutionOutlineV1,
} from '../types/api';

export const problemDataStructureOptions: ProblemDataStructureKey[] = [
  'ARRAY',
  'HASH_MAP',
  'LINKED_LIST',
  'STACK',
  'QUEUE',
  'HEAP',
  'TREE',
  'GRAPH',
  'TRIE',
  'UNION_FIND',
  'OTHER',
];

export const problemAlgorithmOptions: ProblemAlgorithmKey[] = [
  'TWO_POINTERS',
  'SLIDING_WINDOW',
  'BINARY_SEARCH',
  'DFS',
  'BFS',
  'BACKTRACKING',
  'GREEDY',
  'DYNAMIC_PROGRAMMING',
  'PREFIX_SUM',
  'SORTING',
  'MONOTONIC_STACK',
  'DIJKSTRA',
  'OTHER',
];

export const problemComplexityOptions: ProblemComplexityKey[] = [
  'O_1',
  'O_LOG_N',
  'O_N',
  'O_N_LOG_N',
  'O_N2',
  'O_N3',
  'O_2N',
  'OTHER',
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
