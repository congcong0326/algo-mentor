import { afterEach, describe, expect, it, vi } from 'vitest';
import { generateClientId } from './id';

const uuidV4Pattern = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

describe('generateClientId', () => {
  it('generates a UUID when randomUUID is unavailable', () => {
    vi.stubGlobal('crypto', undefined);
    vi.spyOn(Math, 'random').mockReturnValue(0.5);

    expect(generateClientId()).toMatch(uuidV4Pattern);
  });
});
