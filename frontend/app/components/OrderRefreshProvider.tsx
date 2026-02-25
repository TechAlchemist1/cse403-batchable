import {createContext, useContext, useEffect, useState} from 'react';
import {RestaurantContext} from './RestaurantProvider';
import type {Restaurant} from '~/domain/objects';

class RefreshMonitor extends EventTarget {
  private eventSource: EventSource;

  constructor(restaurant: Restaurant['id']) {
    super();
    this.eventSource = new EventSource(`/topic/orders/${restaurant.id}`);
    this.eventSource.addEventListener('message', () => {
      this.dispatchEvent(new Event('orderUpdate'));
    });
  }
  close() {
    this.eventSource.close();
  }
}

export const OrderRefreshContext = createContext<RefreshMonitor | null>(null);

/**
 * Provides an EventTarget to which any component can attach via useEffect.
 * It emits an event whenever orders or batches have changed.
 */
export default function OrderRefreshProvider({
  children,
}: React.PropsWithChildren<{}>) {
  const restaurant = useContext(RestaurantContext);
  const [monitor, setMonitor] = useState<RefreshMonitor | null>(null);

  useEffect(() => {
    if (!restaurant) return;
    const newMonitor = new RefreshMonitor(restaurant);
    setMonitor(newMonitor);
    return () => newMonitor?.close();
  }, [restaurant]);

  return <OrderRefreshContext value={monitor}>{children}</OrderRefreshContext>;
}
