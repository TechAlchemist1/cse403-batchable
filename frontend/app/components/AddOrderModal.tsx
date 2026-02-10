interface Props {
  isOpen: boolean;
  onClose: () => void;
}

export default function AddOrderModal({isOpen, onClose}: Props) {
  if (!isOpen) return null;

  const handleSubmit = (e: React.ChangeEvent<HTMLFormElement>) => {
    e.preventDefault();
    const formData = new FormData(e.currentTarget);
    const data = Object.fromEntries(formData);

    console.log('New Order Data:', data);
    // call Backend Web Server API
    onClose();
  };

  return (
    <div className="fixed inset-0 bg-black/60 backdrop-blur-sm flex items-center justify-center p-4 z-50">
      <div className="bg-white dark:bg-gray-900 p-6 rounded-xl w-full max-w-md shadow-2xl border border-gray-200 dark:border-gray-800">
        <h2 className="text-xl font-bold mb-4 text-gray-900 dark:text-white">
          Create New Order
        </h2>
        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label className="block text-sm font-medium mb-1 text-gray-700 dark:text-gray-300">
              Customer Address
            </label>
            <input
              name="address"
              type="text"
              className="w-full p-2 border rounded bg-transparent border-gray-300 dark:border-gray-700"
              placeholder="123 Batch St"
              required
            />
          </div>
          <div>
            <label className="block text-sm font-medium mb-1 text-gray-700 dark:text-gray-300">
              Prep Time (min)
            </label>
            <input
              name="prepTime"
              type="number"
              className="w-full p-2 border rounded bg-transparent border-gray-300 dark:border-gray-700"
              defaultValue="15"
            />
          </div>
          <div className="mt-6 flex gap-3">
            <button
              type="button"
              onClick={onClose}
              className="flex-1 py-2 text-gray-600 dark:text-gray-400 hover:bg-gray-100 dark:hover:bg-gray-800 rounded-lg transition"
            >
              Cancel
            </button>
            <button
              type="submit"
              className="flex-1 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition"
            >
              Submit Order
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
