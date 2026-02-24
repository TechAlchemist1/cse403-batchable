import {renderHook, waitFor} from '@testing-library/react';
import {act} from 'react';
import {describe, it, expect} from 'vitest';
import {useLoader} from '~/util/query';

const loadData = async () => {
  await new Promise(resolve => setTimeout(resolve, 10));
  return [
    {name: 'me', age: 10},
    {name: 'you', age: -10},
  ];
};

const failToLoadData = async () => {
  await new Promise(resolve => setTimeout(resolve, 10));
  throw new Error('Failed to load');
};

const unableToLoadData = async () => {
  await new Promise(resolve => setTimeout(resolve, 10));
  return null;
};

const makeLoadDifferent = (fail: boolean) => {
  let first = true;
  return async () => {
    await new Promise(resolve => setTimeout(resolve, 10));
    if (first) {
      first = false;
      if (fail) throw new Error('failed to load');
      return 1;
    }
    return 2;
  };
};

describe('useLoader', () => {
  it('should initially be loading', () => {
    const {result} = renderHook(() => useLoader(loadData));
    const {loaded, response} = result.current;
    expect(loaded).toBe(false);
    expect(response).toBe(null);
  });

  it('it finishes loading successfully', async () => {
    const {result} = renderHook(() => useLoader(loadData));
    await waitFor(() => {
      const {loaded, response} = result.current;
      expect(loaded).toBe(true);
      expect(response).not.toBe(null);
    });
  });

  it('it finishes loading with an error', async () => {
    const {result} = renderHook(() => useLoader(failToLoadData));
    await waitFor(() => {
      const {loaded, response} = result.current;
      expect(loaded).toBe(true);
      expect(response).toBe(null);
    });
  });

  it('it fails to finish loading', async () => {
    const {result} = renderHook(() => useLoader(unableToLoadData));
    await expect(
      waitFor(() => {
        const {loaded, response} = result.current;
        expect(loaded).toBe(true);
        expect(response).not.toBe(null);
      }),
    ).rejects.not.toBe(null);
  });

  it('skips refetching mid-load', async () => {
    const loadFn = makeLoadDifferent(false);
    const {result} = renderHook(() => useLoader(loadFn));
    act(() => {
      result.current.reload();
    });
    await waitFor(() => {
      const {loaded, response} = result.current;
      expect(loaded).toBe(true);
      expect(response).toBe(1);
    });
  });

  it('can be reloaded from success', async () => {
    const loadFn = makeLoadDifferent(false);
    const {result} = renderHook(() => useLoader(loadFn));
    await waitFor(() => {
      const {loaded, response} = result.current;
      expect(loaded).toBe(true);
      expect(response).toBe(1);
    });
    act(() => result.current.reload());
    await waitFor(() => {
      const {loaded, response} = result.current;
      expect(loaded).toBe(true);
      expect(response).toBe(1);
    });
    await waitFor(() => {
      const {loaded, response} = result.current;
      expect(loaded).toBe(true);
      expect(response).toBe(2);
    });
  });

  it('can be reloaded from failure', async () => {
    const loadFn = makeLoadDifferent(true);
    const {result} = renderHook(() => useLoader(loadFn));
    await waitFor(() => {
      const {loaded, response} = result.current;
      expect(loaded).toBe(true);
      expect(response).toBe(null);
    });
    act(() => result.current.reload());
    await waitFor(() => {
      expect(result.current.loaded).toBe(false);
    });
    await waitFor(() => {
      const {loaded, response} = result.current;
      expect(loaded).toBe(true);
      expect(response).toBe(2);
    });
  });
});
