import { describe, expect, it } from 'vitest';
import indexHtml from '../../index.html?raw';
import faviconSvg from '../../public/favicon.svg?raw';

describe('static assets', () => {
  it('uses an AM browser-tab mark while keeping the product title', () => {
    expect(indexHtml).toContain('<link rel="icon" href="/favicon.svg" type="image/svg+xml" />');
    expect(indexHtml).toContain('<title>Algo Mentor</title>');
    expect(faviconSvg).toContain('aria-label="Algo Mentor"');
    expect(faviconSvg).toContain('>A</text>');
    expect(faviconSvg).toContain('>M</text>');
  });
});
