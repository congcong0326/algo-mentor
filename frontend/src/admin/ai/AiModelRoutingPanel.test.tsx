import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup } from '@testing-library/react';
import AiModelRoutingPanel from './AiModelRoutingPanel';
import {
  createAdminPolicy,
  getAdminAiEffectiveRoute,
  getAdminAiProviderTypes,
  getAdminAiProviderModels,
  getAdminAiProviders,
  getAdminAiRoutingScenarios,
  getAdminPolicies,
  updateAdminPolicy,
} from '../../services/api';

vi.mock('../../services/api', () => ({
  ApiRequestError: class ApiRequestError extends Error {},
  createAdminPolicy: vi.fn(),
  deleteAdminPolicy: vi.fn(),
  getAdminAiEffectiveRoute: vi.fn(),
  getAdminAiProviderModels: vi.fn(),
  getAdminAiProviders: vi.fn(),
  getAdminAiProviderTypes: vi.fn(),
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
    vi.mocked(getAdminAiProviderTypes).mockResolvedValue({ data: { items: [{
      code: 'openai',
      displayName: 'OpenAI',
      reasoningEfforts: ['none', 'minimal', 'low', 'medium', 'high', 'xhigh', 'max'],
      defaultConfig: {},
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
      content: { modelId: 101, reasoningEffort: null },
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
      content: { modelId: 101, reasoningEffort: null },
    }));
    expect(getAdminAiProviderModels).toHaveBeenCalledWith(1);
  });

  it('uses provider directory options and saves explicit none distinctly from the default', async () => {
    render(<AiModelRoutingPanel />);

    await screen.findByRole('heading', { name: 'Practice chat' });
    fireEvent.click(screen.getByRole('button', { name: 'New route rule' }));
    const effort = screen.getByRole('combobox', { name: 'Reasoning effort' });
    expect(screen.getByRole('option', { name: 'Provider default' })).toBeInTheDocument();
    expect(screen.getByRole('option', { name: 'max' })).toBeInTheDocument();
    fireEvent.change(screen.getByRole('textbox', { name: 'Rule name' }), { target: { value: 'No reasoning' } });
    fireEvent.change(effort, { target: { value: 'none' } });
    fireEvent.click(screen.getByRole('button', { name: 'Save route rule' }));

    await waitFor(() => expect(createAdminPolicy).toHaveBeenCalledWith(expect.objectContaining({
      content: { modelId: 101, reasoningEffort: 'none' },
    })));
  });

  it('resets an effort that is unsupported after changing the target model', async () => {
    vi.mocked(getAdminAiProviders).mockResolvedValue({ data: { items: [
      { id: 1, name: 'OpenAI', providerType: 'openai', enabled: true, modelCount: 1 },
      { id: 2, name: 'DeepSeek', providerType: 'deepseek', enabled: true, modelCount: 1 },
    ] } } as never);
    vi.mocked(getAdminAiProviderTypes).mockResolvedValue({ data: { items: [
      { code: 'openai', displayName: 'OpenAI', reasoningEfforts: ['none', 'high'], defaultConfig: {} },
      { code: 'deepseek', displayName: 'DeepSeek', reasoningEfforts: ['none', 'low'], defaultConfig: {} },
    ] } } as never);
    vi.mocked(getAdminAiProviderModels).mockImplementation(async (providerId) => ({ data: { items: [{
      id: providerId === 1 ? 101 : 202,
      providerInstanceId: providerId,
      displayName: providerId === 1 ? 'OpenAI model' : 'DeepSeek model',
      modelId: providerId === 1 ? 'gpt-test' : 'deepseek-test',
      enabled: true,
    }] } } as never));
    render(<AiModelRoutingPanel />);

    await screen.findByRole('heading', { name: 'Practice chat' });
    fireEvent.click(screen.getByRole('button', { name: 'New route rule' }));
    fireEvent.change(screen.getByRole('combobox', { name: 'Reasoning effort' }), { target: { value: 'high' } });
    fireEvent.change(screen.getByRole('combobox', { name: 'Target model' }), { target: { value: '202' } });

    expect(screen.getByRole('combobox', { name: 'Reasoning effort' })).toHaveValue('');
    expect(screen.getByRole('status')).toHaveTextContent('Reasoning effort reset to Provider default');
  });

  it('shows diagnostic availability returned by the effective-route simulation', async () => {
    vi.mocked(getAdminAiEffectiveRoute).mockResolvedValue({ data: {
      scenarioCode: scenario.scenarioCode,
      configured: true,
      matched: true,
      priority: 2,
      matchSource: 'GROUP',
      reason: 'AI_MODEL_UNAVAILABLE',
      reasoningEffort: 'high',
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
    expect(screen.getByText(/effort high/)).toBeInTheDocument();
  });

  it('shows and rejects a route effort that is no longer recognized', async () => {
    vi.mocked(getAdminPolicies).mockResolvedValue({ data: { items: [{
      id: 9,
      typeCode: scenario.policyTypeCode,
      name: 'Legacy route',
      description: '',
      status: 'ENABLED',
      priority: 1,
      subjectRange: { allSubject: true, subjects: [] },
      content: { modelId: 101, reasoningEffort: 'legacy' },
      version: 1,
    }], total: 1, page: 1, pageSize: 100 } } as never);
    render(<AiModelRoutingPanel />);

    await screen.findByRole('heading', { name: 'Practice chat' });
    fireEvent.click(screen.getByRole('button', { name: 'Edit Legacy route' }));

    expect(screen.getByRole('alert')).toHaveTextContent('unknown or unsupported reasoning effort');
    expect(screen.getByRole('combobox', { name: 'Reasoning effort' })).toHaveAttribute('aria-invalid', 'true');
    fireEvent.click(screen.getByRole('button', { name: 'Save route rule' }));

    expect(updateAdminPolicy).not.toHaveBeenCalled();
    expect(screen.getByRole('status')).toHaveTextContent('unknown or unsupported reasoning effort');
  });
});
