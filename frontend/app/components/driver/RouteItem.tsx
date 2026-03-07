import {useContext} from 'react';
import type {Order} from '~/domain/objects';
import {DriverTokenContext} from '../DriverTokenContext';
import Button from '../Button';
import {formatOrderName} from '~/util/format';
import {orderApi} from '~/api/endpoints/order';

interface Props {
  order: Order;
}

export default function RouteItem({order}: Props) {
  const token = useContext(DriverTokenContext);
  const deliverOrder = async () => {
    if (!token || (await orderApi.markDelivered(order.id, token))) {
      alert('Failed to mark order as delivered');
    }
  };

  return (
    <li>
      <Button onClick={deliverOrder}>Delivered {formatOrderName(order)}</Button>
    </li>
  );
}
