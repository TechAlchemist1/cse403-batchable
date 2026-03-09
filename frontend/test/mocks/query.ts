import {act} from '@testing-library/react';
import {useEffect, useState} from 'react';
import {vi, beforeEach, expect} from 'vitest';
import {type Loader} from '~/util/query';

const LOADING_STATE = {
  response: null,
  loaded: false,
  reload: () => {},
} as const;

// The handleLoader property can be set to specify a loading result associated with a given async loader function
const loaderMock: {
  handleLoader: <T>(load: () => Promise<T>) => Promise<Partial<Loader<T>>>;
} = {
  handleLoader: async () => ({}),
};

export function mockSuccess() {
  loaderMock.handleLoader = async load => ({
    loaded: true,
    response: await load(),
  });
}

export function mockFailure() {
  loaderMock.handleLoader = async load => {
    try {
      await load();
      expect.fail('request should fail');
    } catch {
      return {
        loaded: true,
        response: null,
      };
    }
  };
}

export function mockNoResponse() {
  loaderMock.handleLoader = async load => {
    await load();
    return {
      loaded: false,
      response: null,
    };
  };
}

// pretends to be useLoader in such a way that individual tests can mock the return value
vi.mock('~/util/query.ts', () => {
  return {
    useLoader<T>(load: () => Promise<T>) {
      const [result, setResult] = useState<Loader<T>>(LOADING_STATE);
      useEffect(() => {
        void loaderMock.handleLoader(load).then(state => {
          return act(() => {
            setResult({
              ...LOADING_STATE,
              ...state,
            });
          });
        });
      }, []);
      return result;
    },
  };
});

beforeEach(() => {
  loaderMock.handleLoader = async () => ({});
});
