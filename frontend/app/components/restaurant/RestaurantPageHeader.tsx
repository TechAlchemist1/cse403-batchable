import Button from '../Button';

export default function RestaurantPageHeader() {
  return (
    <div className="mb-8 flex flex-wrap items-center justify-between gap-3">
      <div>
        <h1 className="text-3xl font-black tracking-tight">Restaurant Admin</h1>
        <p className="mt-1 text-sm text-gray-600 dark:text-gray-300">
          Manage restaurant profile, drivers, and menu items in one place.
        </p>
      </div>
      <div className="flex items-center gap-2">
        <Button to="/" style="dark">
          Back to Dashboard
        </Button>
      </div>
    </div>
  );
}
