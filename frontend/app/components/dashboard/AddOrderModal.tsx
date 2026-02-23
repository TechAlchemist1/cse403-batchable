import {fakeId, type Order} from '~/domain/objects';
import {type ModalState} from '../Modal';
import {useContext, useEffect, useState} from 'react';
import {RestaurantContext} from '../RestaurantProvider';
import FormField from '../FormField';
import FormModal from '../FormModal';
import MenuItemSelector from '../MenuItemSelector';
import {MS_PER_MINUTE} from '~/util/time';
import {orderApi} from '~/api/endpoints/order';

interface Props {
  modal: ModalState;
}

export default function AddOrderModal({modal}: Props) {
  const restaurant = useContext(RestaurantContext);
  const [selectedItemNames, setSelectedItemNames] = useState<string[]>([]);

  useEffect(() => {
    if (!modal.open) {
      setSelectedItemNames([]);
    }
  }, [modal.open]);

  const addOrder = async (data: {
    address: string;
    cookTime: string;
    deliverTime: string;
  }) => {
    if (!restaurant) {
      alert("You aren't logged in");
      return;
    }

    const itemNames = selectedItemNames
      .map(name => name.trim())
      .filter(name => name.length > 0);
    if (itemNames.length === 0) {
      alert('Select at least one menu item');
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
      itemNames,
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
      <MenuItemSelector
        restaurant={restaurant}
        onItemsChange={setSelectedItemNames}
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
