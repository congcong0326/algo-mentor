import { describe, expect, it } from 'vitest';
import indexHtml from '../../index.html?raw';
import faviconSvg from '../../public/favicon.svg?raw';

describe('static assets', () => {
  it('declares the root favicon expected by browsers', () => {
    expect(indexHtml).toContain('<link rel="icon" href="/favicon.svg" type="image/svg+xml" />');
    expect(faviconSvg).toContain('aria-label="Algo Mentor"');
    expect(faviconSvg).toContain('#ffba18');
    expect(faviconSvg).toContain('#0b0d12');
  });
});
