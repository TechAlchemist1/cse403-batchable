import {fakeId, type Driver} from '~/domain/objects';
import FormField from '../FormField';
import type {ModalState} from '../Modal';
import {useContext} from 'react';
import {RestaurantContext} from '../RestaurantProvider';
import FormModal from '../FormModal';

interface Props {
  state: ModalState;
  onCreate: (driver: Driver) => Promise<void> | void;
}

export default function AddDriverModal({state, onCreate}: Props) {
  const restaurant = useContext(RestaurantContext);
  const submitNewDriver = (data: {
    name: string;
    phoneNumber: string;
    onShift?: 'on';
  }) => {
    if (!restaurant) {
      alert("You're not logged in");
      return;
    }

    const driver: Driver = {
      id: fakeId('Driver'),
      restaurant,
      name: data.name,
      phoneNumber: {compact: data.phoneNumber},
      onShift: !!data.onShift,
    };

    void onCreate(driver);
  };

  return (
    <FormModal
      state={state}
      title="Add Driver"
      apply={submitNewDriver}
      confirm="Add Driver"
      style="indigo"
    >
      <FormField
        label="Driver Name"
        type="text"
        name="name"
        placeholder="Enter driver name"
        required
      />
      <FormField
        label="Phone Number (digits only)"
        type="tel"
        name="phoneNumber"
        pattern="\d{10,}"
        placeholder="1087235555"
        required
      />
      <FormField label="On Shift" type="checkbox" name="onShift" />
    </FormModal>
  );
}
