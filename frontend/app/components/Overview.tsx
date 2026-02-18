import {type ReactNode} from 'react';
import type {DomainObject} from '~/domain/objects';
import LoadError from './LoadError';
import Loading from './Loading';
import type {Loader} from '~/util/query';

interface Props<T extends DomainObject> {
  title: string;
  itemsLoader: Loader<T[] | null>;
  renderItem: (item: T) => React.ReactElement;
}

/**
 * Represents a list of {@link DomainObject}s.
 * @param title The header for the entire list
 * @param loadItems An array of domain objects
 * @param renderItem A function to convert a single domain object to JSX
 */
export default function OverviewSection<T extends DomainObject>({
  title,
  itemsLoader,
  renderItem,
}: Props<T>) {
  const {response: items, loaded} = itemsLoader;

  let content: ReactNode = <Loading>Loading items...</Loading>;
  if (loaded) {
    content = items ? (
      <ol className="space-y-4">
        {items.map(item => (
          <li key={item.id.id}>{renderItem(item)}</li>
        ))}
      </ol>
    ) : (
      <LoadError>Failed to load</LoadError>
    );
  }

  return (
    <section className="bg-white dark:bg-gray-900 p-6 rounded-2xl shadow-sm border border-gray-200 dark:border-gray-800">
      <h2 className="text-xl font-bold mb-6 flex items-center gap-2">
        {title}
      </h2>
      {content}
    </section>
  );
}
