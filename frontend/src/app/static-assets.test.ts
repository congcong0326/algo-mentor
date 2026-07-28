import { describe, expect, it } from 'vitest';
import indexHtml from '../../index.html?raw';
import faviconSvg from '../../public/favicon.svg?raw';

describe('static assets', () => {
  it('uses an LM browser-tab mark while keeping the product title', () => {
    expect(indexHtml).toContain('<link rel="icon" href="/favicon.svg" type="image/svg+xml" />');
    expect(indexHtml).toContain('<title>Leet Mentor</title>');
    expect(faviconSvg).toContain('aria-label="Leet Mentor"');
    expect(faviconSvg).toContain('>L</text>');
    expect(faviconSvg).toContain('>M</text>');
  });
});
