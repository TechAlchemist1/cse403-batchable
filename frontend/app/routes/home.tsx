import {useState} from 'react';
import OrderList from '../components/OrderList';
import DriverOverview from '../components/DriverOverview';
import AddOrderModal from '../components/AddOrderModal';

export default function Home() {
  const [isModalOpen, setIsModalOpen] = useState(false);

  return (
    <div className="p-8 max-w-7xl mx-auto min-h-screen bg-white dark:bg-gray-950 text-gray-900 dark:text-gray-100">
      <div className="flex justify-between items-center mb-8">
        <h1 className="text-3xl font-extrabold tracking-tight">
          Batchable Dashboard
        </h1>
        <button
          onClick={() => setIsModalOpen(true)}
          className="px-5 py-2.5 bg-blue-600 text-white font-semibold rounded-lg shadow-md hover:bg-blue-700 transition-all active:scale-95"
        >
          + Add New Order
        </button>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-8">
        <section className="bg-white dark:bg-gray-900 p-6 rounded-2xl shadow-sm border border-gray-200 dark:border-gray-800">
          <h2 className="text-xl font-bold mb-6 flex items-center gap-2">
            📦 Active Orders
          </h2>
          <OrderList />
        </section>

        <section className="bg-white dark:bg-gray-900 p-6 rounded-2xl shadow-sm border border-gray-200 dark:border-gray-800">
          <h2 className="text-xl font-bold mb-6 flex items-center gap-2">
            🚗 Driver Status
          </h2>
          <DriverOverview />
        </section>
      </div>

      <AddOrderModal
        isOpen={isModalOpen}
        onClose={() => setIsModalOpen(false)}
      />
    </div>
  );
}
