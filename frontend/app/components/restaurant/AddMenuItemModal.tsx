import {useContext} from 'react';
import FormField from '../FormField';
import FormModal from '../FormModal';
import type {ModalState} from '../Modal';
import {RestaurantContext} from '../RestaurantProvider';
import {fakeId, type MenuItem} from '~/domain/objects';
import {menuApi} from '~/api/endpoints/menu';

interface Props {
  state: ModalState;
  onCreated: (menuItem: MenuItem) => void;
}
export default function AddMenuItemModal({state, onCreated}: Props) {
  const restaurant = useContext(RestaurantContext);
  const submitNewMenuItem = async (data: {name: string}) => {
    if (!restaurant) {
      alert("You're not logged in");
      return;
    }

    const newMenuItem: MenuItem = {
      id: fakeId('MenuItem'),
      restaurant,
      name: data.name,
    };

    const createdId = await menuApi.create(newMenuItem);
    if (!createdId) {
      alert('Failed to create menu item.');
      return;
    }

    onCreated({...newMenuItem, id: createdId});
  };

  return (
    <FormModal
      title="Add Menu Item"
      state={state}
      apply={submitNewMenuItem}
      confirm="Add Item"
      style="amber"
    >
      <FormField
        label="Item Name"
        type="text"
        name="name"
        placeholder="Enter menu item name"
      />
    </FormModal>
  );
}
