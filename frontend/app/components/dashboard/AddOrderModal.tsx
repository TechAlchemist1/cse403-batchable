import {fakeId, type Order} from '~/domain/objects';
import {type ModalState} from '../Modal';
import {useContext} from 'react';
import {RestaurantContext} from '../RestaurantProvider';
import FormField from '../FormField';
import FormModal from '../FormModal';
import {MS_PER_MINUTE} from '~/util/time';
import {orderApi} from '~/api/endpoints/order';

interface Props {
  modal: ModalState;
}

export default function AddOrderModal({modal}: Props) {
  const restaurant = useContext(RestaurantContext);

  const addOrder = async (data: {
    address: string;
    items: string;
    cookTime: string;
    deliverTime: string;
  }) => {
    if (!restaurant) {
      alert("You aren't logged in");
      return;
    }

    const initialTime = new Date();
    const cookedTime = new Date(
      initialTime.getTime() + MS_PER_MINUTE * Number(data.cookTime),
    );
    const deliveryTime = new Date(
      cookedTime.getTime() + MS_PER_MINUTE * Number(data.deliverTime),
    );

    const order: Order = {
      id: fakeId('Order'),
      restaurant,
      destination: {
        address: data.address,
      },
      initialTime,
      cookedTime,
      deliveryTime,
      currentBatch: null,
      highPriority: false,
      itemNames: data.items.split(',').map(name => name.trim()),
      state: 'cooking',
    };

    console.log('New Order Data:', order);
    if (!(await orderApi.create(order))) {
      alert('Failed to create order');
    }
  };

  return (
    <FormModal
      title="Create New Order"
      state={modal}
      apply={addOrder}
      confirm="Create New Order"
    >
      <FormField
        label="Customer Address"
        type="text"
        name="address"
        placeholder="123 Batch St"
        required
      />
      <FormField
        label="Item Name(s)"
        type="text"
        name="items"
        placeholder="Tiramisu, Shrimp Fried Rice, ..."
        required
      />
      <FormField
        label="Prep Time (min)"
        type="number"
        name="cookTime"
        defaultValue="15"
        required
      />
      <FormField
        label="Delivery Time (min)"
        type="number"
        name="deliverTime"
        defaultValue="30"
        required
      />
    </FormModal>
  );
}
