import {useEffect, useReducer, useState} from 'react';

export interface Loader<T> {
  loaded: boolean;
  response: T | null;
  reload: () => void;
}

type LoaderResult<T> =
  | {
      status: 'success';
      result: T;
    }
  | {
      status: 'failure';
    }
  | {
      status: 'incomplete';
    };

/**
 * A hook to wrap around an asynchronous operation, which can be invoked multiple times.
 * Calling the returned object's reload method causes the loading to restart, in case of new data.
 * The loading function can terminate in one of three ways:
 *  1. Returning null: This indicates that the data could not meaningfully be loaded at this time
 *  2. Throwing an error: This indicates that the data could in theory be loaded, but failed
 *  3. Returning non-null: This indicates that the data was successfully loaded
 */
export function useLoader<T>(
  loader: () => Promise<T | null>,
  dependencies: React.DependencyList = [],
): Loader<T> {
  const [flag, reload] = useReducer(flag => !flag, false);
  const [loaded, setLoaded] = useState(true);
  const [response, setResponse] = useState<LoaderResult<T>>({
    status: 'incomplete',
  });

  useEffect(() => {
    if (!loaded) return;

    setLoaded(false);

    loader().then(
      response => {
        if (response) {
          setResponse({
            status: 'success',
            result: response,
          });
        } else {
          setResponse({
            status: 'incomplete',
          });
        }
        setLoaded(true);
      },
      () => {
        setResponse({
          status: 'failure',
        });
        setLoaded(true);
      },
    );
  }, [flag, ...dependencies]);

  if (response.status === 'success') {
    return {
      reload,
      loaded: true,
      response: response.result,
    };
  }

  if (response.status === 'failure') {
    return {
      reload,
      loaded,
      response: null,
    };
  }

  return {
    reload,
    loaded: false,
    response: null,
  };
}
