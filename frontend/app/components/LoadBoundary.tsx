import {type ReactNode} from 'react';
import type {Loader} from '~/util/query';
import LoadError from './LoadError';
import Loading from './Loading';

interface Props<T> {
  loader: Loader<T>;
  name: string;
  children: (data: T) => ReactNode;
}

export default function LoadBoundary<T>({loader, name, children}: Props<T>) {
  return loader.loaded ? (
    loader.response ? (
      children(loader.response)
    ) : (
      <LoadError>Failed to load {name}.</LoadError>
    )
  ) : (
    <Loading>Loading {name}...</Loading>
  );
}
