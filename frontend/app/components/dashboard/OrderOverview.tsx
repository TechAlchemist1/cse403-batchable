import OverviewSection from '../Overview';
import OrderCard from './OrderCard';
import {restaurantApi} from '~/api/endpoints/restaurant';
import {useContext, useEffect} from 'react';
import {RestaurantContext} from '../RestaurantProvider';
import {OrderRefreshContext} from '../OrderRefreshProvider';
import {useLoader} from '~/util/query';
import {anyModalOpen} from '../Modal';

export default function OrderList() {
  const restaurant = useContext(RestaurantContext);
  const monitor = useContext(OrderRefreshContext);

  useEffect(() => {
    monitor?.addEventListener('orderUpdate', () => {
      if (!anyModalOpen()) loader.reload();
    });
  }, [monitor]);

  const loader = useLoader(async () => {
    if (!restaurant) return null;
    const orders = await restaurantApi.getOrders(restaurant);
    return orders;
  });

  useEffect(() => {
    loader.reload();
  }, [restaurant]);

  return (
    <OverviewSection
      title="📦 Active Orders"
      itemsLoader={loader}
      renderItem={order => <OrderCard order={order} />}
    />
  );
}
