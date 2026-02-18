import type {Order} from '~/domain/objects';

interface Props {
  state: Order['state'];
  disabled?: boolean;
}

export default function OrderState({state, disabled}: Props) {
  const style = disabled
    ? 'bg-gray-100 text-gray-400'
    : {
        cooking: 'bg-orange-100 text-orange-700',
        cooked: 'bg-yellow-100 text-yellow-700',
        driving: 'bg-blue-100 text-blue-700',
        delivered: 'bg-green-100 text-green-700',
      }[state];
  return (
    <span className={`px-3 py-1 text-xs font-semibold rounded-full ${style}`}>
      {state.toUpperCase()}
      {disabled && ' (DONE)'}
    </span>
  );
}
