import type {Order} from '~/domain/objects';
import RouteItem from './RouteItem';
import ReturnedButton from './ReturnedButton';
import MapLink from './MapLink';

interface Props {
  orders: Order[] | null;
  mapLink: string | null;
}

export default function RouteOverview({orders, mapLink}: Props) {
  return (
    <main className="flex flex-col gap-5 text-center">
      {orders && mapLink ? (
        <>
          <MapLink link={mapLink} />
          <ol className="flex flex-col gap-3">
            {orders.map(order => (
              <RouteItem order={order} key={order.id.id} />
            ))}
          </ol>
          <ReturnedButton />
        </>
      ) : (
        <p>You have no active batch.</p>
      )}
    </main>
  );
}
