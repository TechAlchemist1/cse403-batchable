import {
  isStateBefore,
  nextStateAfter,
  ORDER_STATES,
  type Order,
} from '~/domain/objects';
import {type ModalState} from '../Modal';
import FormModal from '../FormModal';
import FormField from '../FormField';
import OrderState from '../OrderState';
import {MS_PER_MINUTE} from '~/util/time';
import {formatOrderName, formatTimeInterval} from '~/util/format';
import {orderApi} from '~/api/endpoints/order';
import Button from '../Button';

interface Props {
  order: Order;
  state: ModalState;
}

export default function EditOrderModal({order, state}: Props) {
  const cookTime =
    (order.cookedTime.getTime() - order.initialTime.getTime()) / MS_PER_MINUTE;

  const editable = isStateBefore(order.state, 'cooked');

  const applyChanges = async (data: {
    cookTime: string;
    state: Order['state'];
  }) => {
    if (!editable) return;

    // edit cooked time
    const cookedTime = new Date(
      order.initialTime.getTime() + Number(data.cookTime) * MS_PER_MINUTE,
    );
    console.log('Edit Order Cooked Time:', cookedTime);
    await orderApi.updateCookedTime(order.id, cookedTime);

    // edit state
    let currentState = order.state;
    while (isStateBefore(currentState, data.state)) {
      currentState = nextStateAfter(currentState);
      console.log('Advance Order State:', currentState);
      await orderApi.advanceState(order.id);
    }
  };

  const remake = async () => {
    await orderApi.remake(order.id);
    state.setOpen(false);
  };

  const cancel = async () => {
    await orderApi.delete(order.id);
    state.setOpen(false);
  };

  return (
    <FormModal
      title={`Edit ${formatOrderName(order)}`}
      state={state}
      apply={applyChanges}
      confirm={editable ? 'Apply Changes' : 'OK'}
    >
      <div className="flex gap-3">
        <Button style="red" onClick={cancel} tw="grow flex-0">
          Cancel Order
        </Button>
        <Button style="amber" onClick={remake} tw="grow flex-0">
          Remake Order
        </Button>
      </div>
      <div className="text-sm text-gray-500">
        <p>Items: {order.itemNames.join(', ')}</p>
        <p>
          Destination: <b>{order.destination.address}</b>{' '}
          {formatTimeInterval(order.deliveryTime.getTime() - Date.now())}
        </p>
      </div>
      {editable && (
        <FormField
          label="Prep Time (min)"
          type="number"
          name="cookTime"
          defaultValue={cookTime}
        />
      )}
      {editable &&
        ORDER_STATES.map(state => {
          const disabled = isStateBefore(state, order.state);
          return (
            <FormField
              label={<OrderState state={state} disabled={disabled} />}
              type="radio"
              name="state"
              key={state}
              value={state}
              disabled={disabled}
              defaultChecked={order.state === state}
            />
          );
        })}
    </FormModal>
  );
}
