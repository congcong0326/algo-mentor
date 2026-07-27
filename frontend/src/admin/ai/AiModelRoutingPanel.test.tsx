import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup } from '@testing-library/react';
import AiModelRoutingPanel from './AiModelRoutingPanel';
import {
  createAdminPolicy,
  getAdminAiEffectiveRoute,
  getAdminAiProviderModels,
  getAdminAiProviders,
  getAdminAiRoutingScenarios,
  getAdminPolicies,
} from '../../services/api';

vi.mock('../../services/api', () => ({
  ApiRequestError: class ApiRequestError extends Error {},
  createAdminPolicy: vi.fn(),
  deleteAdminPolicy: vi.fn(),
  getAdminAiEffectiveRoute: vi.fn(),
  getAdminAiProviderModels: vi.fn(),
  getAdminAiProviders: vi.fn(),
  getAdminAiRoutingScenarios: vi.fn(),
  getAdminPolicies: vi.fn(),
  reorderAdminPolicies: vi.fn(),
  requireApiData: (response: { data: unknown }) => response.data,
  updateAdminPolicy: vi.fn(),
}));

const scenario = {
  scenarioCode: 'practice-chat',
  categoryCode: 'PRACTICE',
  displayName: 'Practice chat',
  description: 'Practice chat route.',
  policyTypeCode: 'ai.model-route.practice-chat.v1',
  configured: false,
  enabledPolicyCount: 0,
  totalPolicyCount: 0,
};

describe('AiModelRoutingPanel', () => {
  afterEach(() => {
    cleanup();
  });

  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(getAdminAiRoutingScenarios).mockResolvedValue({ data: { items: [scenario] } } as never);
    vi.mocked(getAdminAiProviders).mockResolvedValue({ data: { items: [{
      id: 1,
      name: 'Primary OpenAI',
      providerType: 'openai',
      enabled: true,
      baseUrl: 'https://api.openai.com/v1',
      modelCount: 1,
    }] } } as never);
    vi.mocked(getAdminAiProviderModels).mockResolvedValue({ data: { items: [{
      id: 101,
      providerInstanceId: 1,
      displayName: 'Sol',
      modelId: 'gpt-5.6-sol',
      enabled: true,
    }] } } as never);
    vi.mocked(getAdminPolicies).mockResolvedValue({ data: { items: [], total: 0, page: 1, pageSize: 100 } } as never);
    vi.mocked(createAdminPolicy).mockResolvedValue({ data: {
      id: 9,
      typeCode: scenario.policyTypeCode,
      name: 'Pro users',
      description: '',
      status: 'ENABLED',
      priority: 1,
      subjectRange: { allSubject: true, subjects: [] },
      content: { modelId: 101 },
      version: 1,
    } } as never);
  });

  it('discovers scenarios and models from APIs then creates an all-user route', async () => {
    render(<AiModelRoutingPanel />);

    expect(await screen.findByRole('heading', { name: 'Practice chat' })).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'New route rule' }));
    fireEvent.change(screen.getByRole('textbox', { name: 'Rule name' }), { target: { value: 'Pro users' } });
    fireEvent.change(screen.getByRole('spinbutton', { name: 'Priority' }), { target: { value: '1' } });
    fireEvent.click(screen.getByRole('button', { name: 'Save route rule' }));

    await waitFor(() => expect(createAdminPolicy).toHaveBeenCalledWith({
      typeCode: scenario.policyTypeCode,
      name: 'Pro users',
      description: '',
      status: 'ENABLED',
      subjectRange: { allSubject: true, subjects: [] },
      content: { modelId: 101 },
    }));
    expect(getAdminAiProviderModels).toHaveBeenCalledWith(1);
  });

  it('shows diagnostic availability returned by the effective-route simulation', async () => {
    vi.mocked(getAdminAiEffectiveRoute).mockResolvedValue({ data: {
      scenarioCode: scenario.scenarioCode,
      configured: true,
      matched: true,
      priority: 2,
      matchSource: 'GROUP',
      reason: 'AI_MODEL_UNAVAILABLE',
      model: {
        id: 101,
        displayName: 'Sol',
        modelId: 'gpt-5.6-sol',
        enabled: false,
        providerInstanceId: 1,
        providerInstanceName: 'Primary OpenAI',
        providerType: 'openai',
        providerEnabled: true,
        reason: 'AI_MODEL_UNAVAILABLE',
      },
    } } as never);
    render(<AiModelRoutingPanel />);

    await screen.findByRole('heading', { name: 'Practice chat' });
    fireEvent.change(screen.getByRole('textbox', { name: 'User ID' }), { target: { value: '42' } });
    fireEvent.click(screen.getByRole('button', { name: 'Simulate' }));

    expect(await screen.findByText(/AI_MODEL_UNAVAILABLE/)).toBeInTheDocument();
  });
});
