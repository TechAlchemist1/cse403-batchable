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

interface Props {
  order: Order;
  state: ModalState;
}

export default function EditOrderModal({order, state}: Props) {
  const cookTime =
    (order.cookedTime.getTime() - order.initialTime.getTime()) / MS_PER_MINUTE;

  const canChangeCookTime = isStateBefore(order.state, 'cooked');
  const canChangeState = isStateBefore(order.state, 'delivered');

  const applyChanges = async (data: {
    cookTime: string;
    state: Order['state'];
  }) => {
    if (canChangeCookTime) {
      const cookedTime = new Date(
        order.initialTime.getTime() + Number(data.cookTime) * MS_PER_MINUTE,
      );
      console.log('Edit Order Cooked Time:', cookedTime);
      await orderApi.updateCookedTime(order.id, cookedTime);
    }

    if (canChangeState) {
      let currentState = order.state;
      while (isStateBefore(currentState, data.state)) {
        currentState = nextStateAfter(currentState);
        console.log('Advance Order State:', currentState);
        await orderApi.advanceState(order.id);
      }
    }
  };

  return (
    <FormModal
      title={`Edit ${formatOrderName(order)}`}
      state={state}
      apply={applyChanges}
      confirm={canChangeCookTime || canChangeState ? 'Apply Changes' : 'OK'}
    >
      <div className="text-sm text-gray-500">
        <p>Items: {order.itemNames.join(', ')}</p>
        <p>
          Destination: <b>{order.destination.address}</b>{' '}
          {formatTimeInterval(order.deliveryTime.getTime() - Date.now())}
        </p>
      </div>
      {canChangeCookTime && (
        <FormField
          label="Prep Time (min)"
          type="number"
          name="cookTime"
          defaultValue={cookTime}
        />
      )}
      {canChangeState &&
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
