import { describe, expect, it } from 'vitest';
import indexHtml from '../../index.html?raw';

describe('static assets', () => {
  it('declares the root favicon expected by browsers', () => {
    expect(indexHtml).toContain('<link rel="icon" href="/favicon.ico" sizes="32x32" />');
  });
});
