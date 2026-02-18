import type {Driver} from '~/domain/objects';
import type {ModalState} from '../Modal';
import FormModal from '../FormModal';
import FormField from '../FormField';
import {formatDriverName} from '~/util/format';
import * as json from '~/domain/json';

interface Props {
  driver: Driver;
  state: ModalState;
}

export default function EditDriverModal({driver, state}: Props) {
  const applyChanges = (data: {
    name: string;
    phoneNumber: string;
    onShift?: 'on';
  }) => {
    const newDriver: Driver = {
      ...driver,
      name: data.name,
      phoneNumber: {compact: data.phoneNumber},
      onShift: !!data.onShift,
    };
    console.log('Edited Driver:', json.driver.unparse(newDriver));
    // call back-end API
  };

  return (
    <FormModal
      title={`Edit ${formatDriverName(driver)}`}
      state={state}
      apply={applyChanges}
    >
      <FormField
        label="Name"
        type="text"
        name="name"
        defaultValue={driver.name}
      />
      <FormField
        label="Phone Number (digits only)"
        type="tel"
        name="phoneNumber"
        pattern="\d{10,}"
        defaultValue={driver.phoneNumber.compact}
      />
      <FormField
        label="On Shift"
        type="checkbox"
        name="onShift"
        defaultChecked={driver.onShift}
      />
    </FormModal>
  );
}
