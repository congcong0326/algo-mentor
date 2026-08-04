import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import AiProviderModelPanel from './AiProviderModelPanel';
import {
  createAdminAiProvider,
  getAdminAiProvider,
  getAdminAiProviderModels,
  getAdminAiProviders,
  getAdminAiProviderTypes,
} from '../../services/api';

vi.mock('../../services/api', () => ({
  ApiRequestError: class ApiRequestError extends Error {},
  createAdminAiProvider: vi.fn(),
  createAdminAiProviderModel: vi.fn(),
  getAdminAiProvider: vi.fn(),
  getAdminAiProviderModels: vi.fn(),
  getAdminAiProviders: vi.fn(),
  getAdminAiProviderTypes: vi.fn(),
  requireApiData: (response: { data: unknown }) => response.data,
  updateAdminAiModel: vi.fn(),
  updateAdminAiProvider: vi.fn(),
}));

const provider = {
  id: 1,
  name: 'Primary OpenAI',
  providerType: 'openai',
  enabled: true,
  baseUrl: 'https://api.openai.com/v1',
  modelCount: 1,
  updatedAt: '2026-07-27T00:00:00Z',
};

describe('AiProviderModelPanel', () => {
  afterEach(() => {
    cleanup();
  });

  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(getAdminAiProviders).mockResolvedValue({ data: { items: [provider] } } as never);
    vi.mocked(getAdminAiProviderTypes).mockResolvedValue({ data: { items: [
      {
        code: 'openai',
        displayName: 'OpenAI',
        reasoningEfforts: ['none', 'high'],
        defaultConfig: { apiKey: '', baseUrl: 'https://api.openai.com/v1', timeoutSeconds: 300, maxRetries: 2 },
      },
      {
        code: 'deepseek',
        displayName: 'DeepSeek',
        reasoningEfforts: ['none', 'low'],
        defaultConfig: { apiKey: '', baseUrl: 'https://api.deepseek.com', timeoutSeconds: 300, maxRetries: 2 },
      },
    ] } } as never);
    vi.mocked(getAdminAiProvider).mockResolvedValue({ data: {
      ...provider,
      config: { apiKey: 'sk-current', baseUrl: provider.baseUrl, timeoutSeconds: 300, maxRetries: 2 },
    } } as never);
    vi.mocked(getAdminAiProviderModels).mockResolvedValue({ data: { items: [{
      id: 101,
      providerInstanceId: 1,
      displayName: 'Sol',
      modelId: 'gpt-5.6-sol',
      enabled: true,
      updatedAt: '2026-07-27T00:00:00Z',
    }] } } as never);
  });

  it('shows low-sensitivity list details and exposes full config only after opening a provider', async () => {
    render(<AiProviderModelPanel />);

    expect(await screen.findByText('https://api.openai.com/v1')).toBeInTheDocument();
    expect(screen.queryByDisplayValue(/sk-current/)).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Edit' }));

    await waitFor(() => expect(getAdminAiProvider).toHaveBeenCalledWith(1));
    expect(await screen.findByDisplayValue(/sk-current/)).toBeInTheDocument();
    expect(screen.getByText('gpt-5.6-sol')).toBeInTheDocument();
  });

  it('uses the selected type template for a new provider and preserves manually edited config on later type changes', async () => {
    render(<AiProviderModelPanel />);

    await screen.findByText('https://api.openai.com/v1');
    fireEvent.click(screen.getByRole('button', { name: 'New provider' }));
    expect((screen.getByRole('textbox', { name: 'Configuration' }) as HTMLTextAreaElement).value).toContain('api.openai.com');
    fireEvent.change(screen.getByRole('combobox', { name: 'Provider type' }), { target: { value: 'deepseek' } });
    expect((screen.getByRole('textbox', { name: 'Configuration' }) as HTMLTextAreaElement).value).toContain('api.deepseek.com');
    fireEvent.change(screen.getByRole('textbox', { name: 'Configuration' }), { target: { value: '{"apiKey":""}' } });
    fireEvent.change(screen.getByRole('combobox', { name: 'Provider type' }), { target: { value: 'openai' } });

    expect(screen.getByRole('textbox', { name: 'Configuration' })).toHaveValue('{"apiKey":""}');
    expect(createAdminAiProvider).not.toHaveBeenCalled();
  });
});
