import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { I18nProvider } from '../i18n/I18nProvider';
import type { LearnerProfileCitation, LearnerProfileDocumentResponse } from '../types/api';
import LearnerProfileDocumentRenderer from './LearnerProfileDocumentRenderer';
import { citation, learnerProfileDocument } from './testFixtures';

afterEach(() => cleanup());

describe('LearnerProfileDocumentRenderer', () => {
  it('renders every block and span as text without parsing markup or links', () => {
    const profileDocument = learnerProfileDocument();
    profileDocument.blocks[1] = {
      type: 'PARAGRAPH',
      spans: [
        { type: 'SUPPORTED_TEXT', text: '<script>alert(1)</script>', citationDisplayNumber: 1 },
        { type: 'TEXT', text: ' [guide](https://example.test) ![image](x.png) [99]' },
      ],
    };
    const openCitation = vi.fn();

    renderRenderer(profileDocument, openCitation);

    expect(screen.getByText('<script>alert(1)</script>')).toBeInTheDocument();
    expect(screen.getByText('[guide](https://example.test) ![image](x.png) [99]')).toBeInTheDocument();
    expect(globalThis.document.querySelector('script')).toBeNull();
    expect(screen.queryByRole('link')).not.toBeInTheDocument();
    expect(screen.queryByRole('img')).not.toBeInTheDocument();
  });

  it('opens the exact citation with click, Enter, and Space while keeping its number visible', () => {
    const openCitation = vi.fn();
    renderRenderer(learnerProfileDocument(), openCitation);
    const trigger = screen.getByRole('button', { name: '打开第 1 条判断的依据' });

    expect(trigger).toHaveTextContent('[1]');
    fireEvent.click(trigger);
    fireEvent.keyDown(trigger, { key: 'Enter' });
    fireEvent.keyDown(trigger, { key: ' ' });

    expect(openCitation).toHaveBeenCalledTimes(3);
    expect(openCitation).toHaveBeenLastCalledWith(expect.objectContaining({ displayNumber: 1 }));
  });

  it('shows trusted preview evidence while a citation has focus', () => {
    renderRenderer(learnerProfileDocument(), vi.fn());
    const trigger = screen.getByRole('button', { name: '打开第 1 条判断的依据' });

    fireEvent.focus(trigger);

    expect(screen.getByRole('tooltip')).toHaveTextContent('用户消息依据 1 条，正式代码复盘依据 1 条');
    expect(screen.getByRole('tooltip')).toHaveTextContent('两数之和');
    expect(trigger).toHaveAttribute('aria-describedby');
  });

  it('does not drop supported text when rendering a large complete document', () => {
    const document = largeDocument(500);
    renderRenderer(document, vi.fn());

    expect(screen.getAllByRole('button')).toHaveLength(500);
    expect(screen.getByText('判断 500')).toBeInTheDocument();
  });
});

function renderRenderer(
  document: LearnerProfileDocumentResponse,
  openCitation: (citation: LearnerProfileCitation) => void,
) {
  render(
    <I18nProvider>
      <LearnerProfileDocumentRenderer document={document} openCitation={openCitation} />
    </I18nProvider>,
  );
}

function largeDocument(count: number): LearnerProfileDocumentResponse {
  const citations = Object.fromEntries(Array.from({ length: count }, (_, index) => {
    const displayNumber = index + 1;
    return [String(displayNumber), citation(displayNumber)];
  }));
  return {
    ...learnerProfileDocument(),
    blocks: [{
      type: 'PARAGRAPH',
      spans: Array.from({ length: count }, (_, index) => ({
        type: 'SUPPORTED_TEXT' as const,
        text: `判断 ${index + 1}`,
        citationDisplayNumber: index + 1,
      })),
    }],
    citationMap: citations,
  };
}
