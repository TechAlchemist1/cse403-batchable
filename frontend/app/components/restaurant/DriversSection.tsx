import {
  useContext,
  useEffect,
  useState,
  type Dispatch,
  type SetStateAction,
} from 'react';
import type {Driver} from '../../domain/objects';
import {useModal} from '../Modal';
import AddDriverModal from './AddDriverModal';
import Button from '../Button';
import {driverApi} from '~/api/endpoints/driver';
import {restaurantApi} from '~/api/endpoints/restaurant';
import DriverRow from './DriverRow';
import {RestaurantContext} from '../RestaurantProvider';

type DriversSectionProps = {
  initialDrivers: Driver[];
  isEditing: boolean;
  setIsEditing: Dispatch<SetStateAction<boolean>>;
};

function DriversSection({
  initialDrivers,
  isEditing,
  setIsEditing,
}: DriversSectionProps) {
  const restaurantId = useContext(RestaurantContext);
  const [drivers, setDrivers] = useState<Driver[]>(initialDrivers);
  const addDriverModal = useModal();
  const [editingDriverId, setEditingDriverId] = useState<number | null>(null);

  useEffect(() => {
    if (!isEditing) {
      setEditingDriverId(null);
    }
  }, [isEditing]);

  const toggleSectionEditing = () => {
    setIsEditing(!isEditing);
  };

  const refreshDrivers = async () => {
    if (!restaurantId) {
      return false;
    }
    const latestDrivers = await restaurantApi.getDrivers(restaurantId);
    if (!latestDrivers) {
      return false;
    }
    setDrivers(latestDrivers);
    return true;
  };

  const createDriver = async (driver: Driver) => {
    const createdId = await driverApi.create(driver);
    if (!createdId) {
      alert('Failed to create driver.');
      return;
    }
    setDrivers(current => [...current, {...driver, id: createdId}]);
  };

  const saveDriver = async (driver: Driver) => {
    const updated = await driverApi.update(driver);
    if (!updated) {
      alert('Failed to update driver.');
      await refreshDrivers();
      return;
    }
    setDrivers(current =>
      current.map(item => (item.id.id === driver.id.id ? driver : item)),
    );
    setEditingDriverId(null);
  };

  const deleteDriver = async (driver: Driver) => {
    const deleted = await driverApi.delete(driver.id);
    if (!deleted) {
      alert('Failed to delete driver.');
      return;
    }
    if (editingDriverId === driver.id.id) {
      setEditingDriverId(null);
    }
    setDrivers(current => current.filter(item => item.id.id !== driver.id.id));
  };

  return (
    <section className="bg-white dark:bg-gray-900 p-6 rounded-2xl shadow-sm border border-gray-200 dark:border-gray-800">
      <div className="mb-4 flex items-center justify-between">
        <h2 className="text-xl font-bold">Drivers</h2>
        <div className="flex items-center gap-2">
          <Button style="indigo" onClick={() => addDriverModal.setOpen(true)}>
            + Add Driver
          </Button>
          <Button style="blue" onClick={toggleSectionEditing}>
            {isEditing ? 'Done Editing' : 'Edit Drivers'}
          </Button>
        </div>
      </div>

      <div className="overflow-x-auto">
        <table className="w-full text-left text-sm">
          <thead>
            <tr className="border-b border-gray-200 dark:border-gray-800 text-gray-600 dark:text-gray-300">
              <th className="px-3 py-2 font-semibold">Name</th>
              <th className="px-3 py-2 font-semibold">Phone</th>
              <th className="px-3 py-2 font-semibold">Shift</th>
              {isEditing && (
                <th className="px-3 py-2 font-semibold">Actions</th>
              )}
            </tr>
          </thead>
          <tbody>
            {drivers.map(driver => {
              const isEditingDriver = editingDriverId === driver.id.id;

              return (
                <DriverRow
                  key={driver.id.id}
                  driver={driver}
                  isEditingSection={isEditing}
                  isEditingDriver={isEditingDriver}
                  onStartEdit={() => setEditingDriverId(driver.id.id)}
                  onSave={updatedDriver => void saveDriver(updatedDriver)}
                  onDelete={() => void deleteDriver(driver)}
                />
              );
            })}
          </tbody>
        </table>
      </div>

      <AddDriverModal state={addDriverModal} onCreate={createDriver} />
    </section>
  );
}

export default DriversSection;
