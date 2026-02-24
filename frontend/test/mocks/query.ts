import {vi} from 'vitest';

vi.mock('~/util/query.ts', () => {
  return {
    useLoader: () => {
      return {
        response: [],
        loaded: true,
        reload: () => {},
      };
    },
  };
});
