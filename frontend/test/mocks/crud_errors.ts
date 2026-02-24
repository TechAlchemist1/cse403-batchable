import {vi} from 'vitest';

vi.mock('~/api/crud', async loadOriginal => {
  const original: {
    CrudApi: {
      prototype: {
        error: () => void;
      };
      DELAY: number;
    };
  } = await loadOriginal();
  original.CrudApi.prototype.error = vi.fn();
  original.CrudApi.DELAY = 0;
  return original;
});
