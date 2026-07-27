import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import SystemPromptManagementPage from './SystemPromptManagementPage';
import {
  createAdminPolicy,
  getAdminPolicies,
  getSystemPromptType,
  getSystemPromptTypes,
} from '../../services/api';

vi.mock('../../services/api', () => ({
  createAdminPolicy: vi.fn(),
  deleteAdminPolicy: vi.fn(),
  getAdminPolicies: vi.fn(),
  getEffectiveSystemPrompt: vi.fn(),
  getSystemPromptType: vi.fn(),
  getSystemPromptTypes: vi.fn(),
  reorderAdminPolicies: vi.fn(),
  requireApiData: (response: { data: unknown }) => response.data,
  updateAdminPolicy: vi.fn(),
}));

const promptType = {
  typeCode: 'ai.system-prompt.new-scenario.v1',
  categoryCode: 'TEST',
  displayName: '动态测试场景',
  description: '通过目录动态返回的测试提示词。',
  sourceRevision: '2026-07-25.1',
  snapshotScope: 'RUN',
  sectionCount: 1,
  configured: false,
  livePolicyCount: 0,
  effectiveSource: 'CODE_DEFAULT',
};

describe('SystemPromptManagementPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(getSystemPromptTypes).mockResolvedValue({ data: { items: [promptType] } } as never);
    vi.mocked(getSystemPromptType).mockResolvedValue({
      data: {
        typeCode: promptType.typeCode,
        sourceRevision: promptType.sourceRevision,
        snapshotScope: 'RUN',
        sections: [{
          key: 'new-scenario.rule',
          displayName: '动态 section',
          description: '由后端 definition 提供。',
          displayOrder: 10,
          required: true,
          maxLength: 800,
          defaultText: '代码默认正文。',
        }],
      },
    } as never);
    vi.mocked(getAdminPolicies).mockResolvedValue({
      data: { items: [], total: 0, page: 1, pageSize: 100 },
    } as never);
    vi.mocked(createAdminPolicy).mockResolvedValue({ data: { id: 9 } } as never);
  });

  it('renders a new registered type dynamically and saves a section override', async () => {
    render(<SystemPromptManagementPage />);

    expect(await screen.findByRole('heading', { name: '动态测试场景' })).toBeInTheDocument();
    expect(screen.getByRole('textbox', { name: '动态 section' })).toBeDisabled();
    expect(getSystemPromptType).toHaveBeenCalledWith(promptType.typeCode);

    fireEvent.click(screen.getByRole('button', { name: '新建策略' }));
    fireEvent.click(screen.getByRole('checkbox', { name: '覆盖此 section' }));
    fireEvent.change(screen.getByRole('textbox', { name: '动态 section' }), {
      target: { value: '管理员覆盖正文。' },
    });
    fireEvent.click(screen.getByRole('button', { name: '保存策略' }));

    await waitFor(() => expect(createAdminPolicy).toHaveBeenCalledWith(expect.objectContaining({
      typeCode: promptType.typeCode,
      content: { sectionOverrides: { 'new-scenario.rule': '管理员覆盖正文。' } },
    })));
  });
});
