import {act} from '@testing-library/react';
import {beforeEach} from 'vitest';
import {useEffect, useState} from 'react';
import {vi} from 'vitest';
import {type Loader} from '~/util/query';

const LOADING_STATE = {
  response: null,
  loaded: false,
  reload: () => {},
} as const;

// The handleLoader property can be set to specify a loading result associated with a given async loader function
export const loaderMock: {
  handleLoader: <T>(load: () => Promise<T>) => Promise<Partial<Loader<T>>>;
} = {
  handleLoader: async () => ({}),
};

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
