export default function OrderList() {
  const orders = [
    {
      id: '101',
      address: '123 Seattle St',
      state: 'COOKING',
      time: '12:30 PM',
      prep: '15 min',
    },
    {
      id: '102',
      address: '456 Washington St',
      state: 'READY',
      time: '12:45 PM',
      prep: '10 min',
    },
  ];

  return (
    <div className="space-y-4">
      {orders.map(order => (
        <div
          key={order.id}
          className="p-4 border rounded-lg flex justify-between items-center bg-gray-50 dark:bg-gray-800 border-gray-200 dark:border-gray-700"
        >
          <div>
            <p className="font-bold text-gray-900 dark:text-gray-100">
              Order #{order.id}
            </p>
            <p className="text-sm text-white-500">{order.address}</p>
            <p className="text-sm text-gray-500">
              {order.time} • Prep: {order.prep}
            </p>
          </div>
          <span
            className={`px-3 py-1 text-xs font-semibold rounded-full ${
              order.state === 'READY'
                ? 'bg-green-100 text-green-700'
                : 'bg-orange-100 text-orange-700'
            }`}
          >
            {order.state}
          </span>
        </div>
      ))}
    </div>
  );
}
