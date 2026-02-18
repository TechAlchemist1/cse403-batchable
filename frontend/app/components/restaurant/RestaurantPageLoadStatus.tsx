type RestaurantPageLoadStatusProps = {
  isLoadingData: boolean;
  loadError: string | null;
};

export default function RestaurantPageLoadStatus({
  isLoadingData,
  loadError,
}: RestaurantPageLoadStatusProps) {
  return (
    <>
      {isLoadingData && (
        <p className="mb-4 text-sm text-gray-600 dark:text-gray-300">
          Loading restaurant data...
        </p>
      )}
      {loadError && (
        <p className="mb-4 rounded-md border border-amber-200 bg-amber-50 px-3 py-2 text-sm text-amber-800 dark:border-amber-800 dark:bg-amber-950 dark:text-amber-100">
          {loadError}
        </p>
      )}
    </>
  );
}
