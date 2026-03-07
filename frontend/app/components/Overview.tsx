import type {DomainObject} from '~/domain/objects';
import type {Loader} from '~/util/query';
import LoadBoundary from './LoadBoundary';

interface Props<T extends DomainObject> {
  title: string;
  itemsLoader: Loader<T[]>;
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
  return (
    <section className="bg-white dark:bg-gray-900 p-6 rounded-2xl shadow-sm border border-gray-200 dark:border-gray-800">
      <h2 className="text-xl font-bold mb-6 flex items-center gap-2">
        {title}
      </h2>
      <LoadBoundary loader={itemsLoader} name="items">
        {items => (
          <ol className="space-y-4">
            {items.map(item => (
              <li key={item.id.id}>{renderItem(item)}</li>
            ))}
          </ol>
        )}
      </LoadBoundary>
    </section>
  );
}
