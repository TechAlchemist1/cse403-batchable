import RouteOverview from './RouteOverview';
import RouteHeader from './RouteHeader';
import {useContext, useEffect} from 'react';
import {OrderRefreshContext} from '../OrderRefreshProvider';
import {useLoader} from '~/util/query';
import {driverApi} from '~/api/endpoints/driver';
import {DriverTokenContext} from '../DriverTokenContext';
import LoadBoundary from '../LoadBoundary';

export default function DriverPage() {
  const refresher = useContext(OrderRefreshContext);
  const token = useContext(DriverTokenContext);

  const loader = useLoader(async () => {
    if (!token) return null;
    const route = await driverApi.getRouteInfo(token);
    if (!route) throw new Error('failed to load driver info by token');
    return route;
  }, [token]);

  useEffect(() => {
    if (!refresher) return;
    const listener = () => loader.reload();
    refresher.addEventListener('orderUpdate', listener);
    return () => {
      refresher.removeEventListener('orderUpdate', listener);
    };
  }, [refresher]);

  return (
    <LoadBoundary loader={loader} name="route dashboard">
      {route => (
        <>
          <RouteHeader driver={route.driver} />
          <RouteOverview orders={route.orders} mapLink={route.mapLink} />
        </>
      )}
    </LoadBoundary>
  );
}
