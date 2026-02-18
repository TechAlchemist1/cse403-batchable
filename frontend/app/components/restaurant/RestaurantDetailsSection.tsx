import {useContext, useEffect, useState} from 'react';
import type {Restaurant} from '../../domain/objects';
import Button from '../Button';
import {restaurantApi} from '~/api/endpoints/restaurant';
import {RestaurantContext} from '../RestaurantProvider';

type RestaurantDetailsSectionProps = {
  initialRestaurant: Restaurant;
};

function RestaurantDetailsSection({
  initialRestaurant,
}: RestaurantDetailsSectionProps) {
  const restaurantId = useContext(RestaurantContext);
  const [restaurant, setRestaurant] = useState(initialRestaurant);
  const [draftRestaurant, setDraftRestaurant] = useState(initialRestaurant);
  const [isEditing, setIsEditing] = useState(false);

  useEffect(() => {
    if (!isEditing) {
      setDraftRestaurant(restaurant);
    }
  }, [restaurant, isEditing]);

  const refreshRestaurant = async () => {
    if (!restaurantId) {
      return false;
    }
    const latestRestaurant = await restaurantApi.read(restaurantId);
    if (!latestRestaurant) {
      return false;
    }
    setRestaurant(latestRestaurant);
    setDraftRestaurant(latestRestaurant);
    return true;
  };

  const toggleEditing = async () => {
    if (!isEditing) {
      setDraftRestaurant(restaurant);
      setIsEditing(true);
      return;
    }

    const updated = await restaurantApi.update(draftRestaurant);
    if (!updated) {
      alert('Failed to update restaurant details.');
      await refreshRestaurant();
      return;
    }

    setRestaurant(draftRestaurant);
    setIsEditing(false);
  };

  return (
    <section className="bg-white dark:bg-gray-900 p-6 rounded-2xl shadow-sm border border-gray-200 dark:border-gray-800">
      <div className="mb-4 flex items-center justify-between">
        <h2 className="text-xl font-bold">Restaurant Details</h2>
        <Button style="emerald" onClick={() => void toggleEditing()}>
          {isEditing ? 'Done Editing' : 'Edit Restaurant'}
        </Button>
      </div>

      <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
        <label className="text-sm font-semibold text-gray-700 dark:text-gray-300">
          Restaurant Name
          <input
            value={draftRestaurant.name}
            disabled={!isEditing}
            onChange={event =>
              setDraftRestaurant(current => ({
                ...current,
                name: event.target.value,
              }))
            }
            className="mt-1 w-full rounded-md border border-gray-300 dark:border-gray-700 bg-white dark:bg-gray-950 px-3 py-2 disabled:bg-gray-100 dark:disabled:bg-gray-800"
          />
        </label>

        <label className="text-sm font-semibold text-gray-700 dark:text-gray-300 md:col-span-2">
          Address
          <input
            value={draftRestaurant.location.address}
            disabled={!isEditing}
            onChange={event =>
              setDraftRestaurant(current => ({
                ...current,
                location: {
                  ...current.location,
                  address: event.target.value,
                },
              }))
            }
            className="mt-1 w-full rounded-md border border-gray-300 dark:border-gray-700 bg-white dark:bg-gray-950 px-3 py-2 disabled:bg-gray-100 dark:disabled:bg-gray-800"
          />
        </label>
      </div>
    </section>
  );
}

export default RestaurantDetailsSection;
