import type {Order} from '~/domain/objects';
import {formatOrderName, formatTimeInterval} from '~/util/format';
import EditOrderModal from './EditOrderModal';
import Card from '../Card';
import {useModal} from '../Modal';
import OrderState from '../OrderState';

interface Props {
  order: Order;
}

export default function OrderCard({order}: Props) {
  const editOrderModal = useModal();
  const prepTime = formatTimeInterval(order.cookedTime.getTime() - Date.now());
  const deliverTime = formatTimeInterval(
    order.deliveryTime.getTime() - Date.now(),
  );
  // const items = order.itemNames.join(', ');

  return (
    <>
      <Card onClick={() => editOrderModal.setOpen(true)}>
        <div>
          <p className="font-bold text-gray-900 dark:text-gray-100">
            {formatOrderName(order)}
          </p>
          <p className="text-sm text-white-500">{order.destination.address}</p>
          {/* <p className="text-sm text-grey-500">{items}</p> */}
          <p className="text-sm text-gray-500">
            Prepared {prepTime} • Delivered {deliverTime}
          </p>
        </div>
        <OrderState state={order.state} />
      </Card>
      <EditOrderModal order={order} state={editOrderModal} />
    </>
  );
}
