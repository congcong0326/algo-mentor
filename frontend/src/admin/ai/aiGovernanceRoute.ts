export const AI_GOVERNANCE_TABS = ['providers', 'routing', 'usage', 'pricing', 'audit'] as const;

export type AiGovernanceTab = (typeof AI_GOVERNANCE_TABS)[number];

export function isAiGovernanceTab(value: string | null | undefined): value is AiGovernanceTab {
  return AI_GOVERNANCE_TABS.some((tab) => tab === value);
}

export function aiGovernanceTabFromParam(value: string | null): AiGovernanceTab {
  return isAiGovernanceTab(value) ? value : 'usage';
}
