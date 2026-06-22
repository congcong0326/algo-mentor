import { describe, expect, it } from 'vitest';
import { resolveApiProxyTarget } from './vite.config';

describe('vite config', () => {
  it('uses the default backend proxy target', () => {
    expect(resolveApiProxyTarget({})).toBe('http://localhost:8080');
  });

  it('allows overriding the backend proxy target', () => {
    expect(resolveApiProxyTarget({ VITE_API_PROXY_TARGET: 'http://localhost:18080' })).toBe(
      'http://localhost:18080',
    );
  });
});
