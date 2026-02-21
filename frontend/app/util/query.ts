import {useEffect, useReducer, useState} from 'react';

export interface Loader<T> {
  loaded: boolean;
  response: T | null;
  reload: () => void;
}

/**
 * A hook to wrap around an asynchronous operation, which can be invoked multiple times.
 * Calling the returned object's reload method causes the loading to restart, in case of new data.
 */
export function useLoader<T>(
  loader: () => Promise<T>,
  dependencies: React.DependencyList = [],
): Loader<T> {
  const [flag, toggleFlag] = useReducer(flag => !flag, false);
  const [loaded, setLoaded] = useState(true);
  const [response, setResponse] = useState<T | null>(null);

  useEffect(() => {
    if (!loaded) return;

    setLoaded(false);

    void loader().then(response => {
      setResponse(response);
      setLoaded(true);
    });
  }, [flag, ...dependencies]);

  return {
    loaded: response ? true : loaded,
    response,
    reload: toggleFlag,
  };
}
