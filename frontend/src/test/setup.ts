import '@testing-library/jest-dom/vitest';

Object.defineProperty(window.navigator, 'language', {
  configurable: true,
  value: 'zh-CN',
});
Object.defineProperty(window.navigator, 'languages', {
  configurable: true,
  value: ['zh-CN'],
});
