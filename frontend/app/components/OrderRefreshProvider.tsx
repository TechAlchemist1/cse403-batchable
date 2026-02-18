import {createContext, useContext, useEffect, useState} from 'react';
import {RestaurantContext} from './RestaurantProvider';

class RefreshMonitor extends EventTarget {
  private handle: number;

  constructor(/* restaurant: Restaurant['id'] */) {
    super();
    this.handle = setInterval(() => {
      this.dispatchEvent(new Event('orderUpdate'));
    }, 1500) as unknown as number; // TODO: make this a websocket
  }
  close() {
    clearInterval(this.handle);
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
    monitor?.close();
    setMonitor(new RefreshMonitor(/* restaurant */));
  }, [restaurant]);

  return <OrderRefreshContext value={monitor}>{children}</OrderRefreshContext>;
}
