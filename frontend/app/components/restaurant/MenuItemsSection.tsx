import {
  useContext,
  useEffect,
  useState,
  type Dispatch,
  type SetStateAction,
} from 'react';
import type {MenuItem} from '../../domain/objects';
import {useModal} from '../Modal';
import AddMenuItemModal from './AddMenuItemModal';
import Button from '../Button';
import {menuApi} from '~/api/endpoints/menu';
import {restaurantApi} from '~/api/endpoints/restaurant';
import MenuItemRow from './MenuItemRow';
import {RestaurantContext} from '../RestaurantProvider';

type MenuItemsSectionProps = {
  initialMenuItems: MenuItem[];
  isEditing: boolean;
  setIsEditing: Dispatch<SetStateAction<boolean>>;
};

function MenuItemsSection({
  initialMenuItems,
  isEditing,
  setIsEditing,
}: MenuItemsSectionProps) {
  const restaurantId = useContext(RestaurantContext);
  const [menuItems, setMenuItems] = useState<MenuItem[]>(initialMenuItems);
  const addMenuItemModal = useModal();
  const [editingMenuItemId, setEditingMenuItemId] = useState<number | null>(
    null,
  );

  useEffect(() => {
    if (!isEditing) {
      setEditingMenuItemId(null);
    }
  }, [isEditing]);

  const toggleSectionEditing = () => {
    setIsEditing(!isEditing);
  };

  const refreshMenuItems = async () => {
    if (!restaurantId) {
      return false;
    }
    const latestMenuItems = await restaurantApi.getMenuItems(restaurantId);
    if (!latestMenuItems) {
      return false;
    }
    setMenuItems(latestMenuItems);
    return true;
  };

  const addCreatedMenuItem = (menuItem: MenuItem) => {
    setMenuItems(current => [...current, menuItem]);
  };

  const saveMenuItem = async (menuItem: MenuItem) => {
    const updated = await menuApi.update(menuItem);
    if (!updated) {
      alert('Failed to update menu item.');
      await refreshMenuItems();
      return;
    }
    setMenuItems(current =>
      current.map(currentItem =>
        currentItem.id.id === menuItem.id.id ? menuItem : currentItem,
      ),
    );
    setEditingMenuItemId(null);
  };

  const deleteMenuItem = async (menuItem: MenuItem) => {
    const deleted = await menuApi.delete(menuItem.id);
    if (!deleted) {
      alert('Failed to delete menu item.');
      return;
    }
    if (editingMenuItemId === menuItem.id.id) {
      setEditingMenuItemId(null);
    }
    setMenuItems(current =>
      current.filter(currentItem => currentItem.id.id !== menuItem.id.id),
    );
  };

  return (
    <section className="bg-white dark:bg-gray-900 p-6 rounded-2xl shadow-sm border border-gray-200 dark:border-gray-800">
      <div className="mb-4 flex items-center justify-between">
        <h2 className="text-xl font-bold">Menu Items</h2>
        <div className="flex items-center gap-2">
          <Button onClick={() => addMenuItemModal.setOpen(true)} style="amber">
            + Add Menu Item
          </Button>
          <Button onClick={toggleSectionEditing} style="orange">
            {isEditing ? 'Done Editing' : 'Edit Menu'}
          </Button>
        </div>
      </div>

      <div className="overflow-x-auto">
        <table className="w-full text-left text-sm">
          <thead>
            <tr className="border-b border-gray-200 dark:border-gray-800 text-gray-600 dark:text-gray-300">
              <th className="px-3 py-2 font-semibold">Name</th>
              <th className="px-3 py-2 font-semibold">Menu Item ID</th>
              {isEditing && (
                <th className="px-3 py-2 font-semibold">Actions</th>
              )}
            </tr>
          </thead>
          <tbody>
            {menuItems.map(item => {
              const isEditingMenuItem = editingMenuItemId === item.id.id;

              return (
                <MenuItemRow
                  key={item.id.id}
                  menuItem={item}
                  isEditingSection={isEditing}
                  isEditingMenuItem={isEditingMenuItem}
                  onStartEdit={() => setEditingMenuItemId(item.id.id)}
                  onSave={updatedMenuItem => void saveMenuItem(updatedMenuItem)}
                  onDelete={() => void deleteMenuItem(item)}
                />
              );
            })}
          </tbody>
        </table>
      </div>

      <AddMenuItemModal
        state={addMenuItemModal}
        onCreated={addCreatedMenuItem}
      />
    </section>
  );
}

export default MenuItemsSection;
