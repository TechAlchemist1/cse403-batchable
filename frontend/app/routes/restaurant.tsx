import {
  useContext,
  useEffect,
  useState,
  type Dispatch,
  type SetStateAction,
} from 'react';
import {restaurantApi} from '~/api/endpoints/restaurant';
import type {Driver, MenuItem, Restaurant} from '~/domain/objects';
import DriversSection from '../components/restaurant/DriversSection';
import MenuItemsSection from '../components/restaurant/MenuItemsSection';
import RestaurantDetailsSection from '../components/restaurant/RestaurantDetailsSection';
import RestaurantPageHeader from '../components/restaurant/RestaurantPageHeader';
import RestaurantPageLoadStatus from '../components/restaurant/RestaurantPageLoadStatus';
import {RestaurantContext} from '~/components/RestaurantProvider';

type RestaurantPageData = {
  restaurant: Restaurant;
  drivers: Driver[];
  menuItems: MenuItem[];
};

function RestaurantPage() {
  const restaurantId = useContext(RestaurantContext);
  const [data, setData] = useState<RestaurantPageData | null>(null);
  const [isLoadingData, setIsLoadingData] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [activeEditSection, setActiveEditSection] = useState<
    'drivers' | 'menu' | null
  >(null);

  useEffect(() => {
    if (!restaurantId) {
      setData(null);
      setLoadError('Could not determine restaurant.');
      setIsLoadingData(false);
      return;
    }

    let cancelled = false;

    const loadRestaurantData = async () => {
      setIsLoadingData(true);
      setLoadError(null);
      try {
        const [restaurant, drivers, menuItems] = await Promise.all([
          restaurantApi.read(restaurantId),
          restaurantApi.getDrivers(restaurantId),
          restaurantApi.getMenuItems(restaurantId),
        ]);

        if (cancelled) {
          return;
        }

        if (!restaurant || !drivers || !menuItems) {
          setData(null);
          setLoadError('Could not load restaurant data from the backend.');
          return;
        }

        setData({restaurant, drivers, menuItems});
      } catch (error) {
        if (cancelled) {
          return;
        }
        console.error('Failed to load restaurant admin data', error);
        setData(null);
        setLoadError('Could not load restaurant data from the backend.');
      } finally {
        if (!cancelled) {
          setIsLoadingData(false);
        }
      }
    };

    void loadRestaurantData();

    return () => {
      cancelled = true;
    };
  }, [restaurantId]);

  const setIsEditingDriversExclusive: Dispatch<
    SetStateAction<boolean>
  > = value =>
    setActiveEditSection(current => {
      const isEditingDrivers = current === 'drivers';
      const nextValue =
        typeof value === 'function' ? value(isEditingDrivers) : value;

      if (nextValue) {
        return 'drivers';
      }

      return isEditingDrivers ? null : current;
    });

  const setIsEditingMenuExclusive: Dispatch<SetStateAction<boolean>> = value =>
    setActiveEditSection(current => {
      const isEditingMenu = current === 'menu';
      const nextValue =
        typeof value === 'function' ? value(isEditingMenu) : value;

      if (nextValue) {
        return 'menu';
      }

      return isEditingMenu ? null : current;
    });

  const isEditingDrivers = activeEditSection === 'drivers';
  const isEditingMenu = activeEditSection === 'menu';

  return (
    <div className="p-8 max-w-7xl mx-auto min-h-screen bg-white dark:bg-gray-950 text-gray-900 dark:text-gray-100">
      <RestaurantPageHeader />
      <RestaurantPageLoadStatus
        isLoadingData={isLoadingData}
        loadError={loadError}
      />

      {data && (
        <div className="grid grid-cols-1 gap-6">
          <DriversSection
            initialDrivers={data.drivers}
            isEditing={isEditingDrivers}
            setIsEditing={setIsEditingDriversExclusive}
          />

          <RestaurantDetailsSection initialRestaurant={data.restaurant} />

          <MenuItemsSection
            initialMenuItems={data.menuItems}
            isEditing={isEditingMenu}
            setIsEditing={setIsEditingMenuExclusive}
          />
        </div>
      )}
    </div>
  );
}

export default RestaurantPage;
