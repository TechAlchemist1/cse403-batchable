import {useEffect, useState} from 'react';
import {restaurantApi} from '~/api/endpoints/restaurant';
import type {MenuItem, Restaurant} from '~/domain/objects';
import FormField from './FormField';
import LoadError from './LoadError';
import Loading from './Loading';

interface Props {
  restaurant: Restaurant['id'] | null;
  onItemsChange: (selectedItemNames: string[]) => void;
  label?: string;
  loadingMessage?: string;
  errorMessage?: string;
  emptyMessage?: string;
}

export default function MenuItemSelector({
  restaurant,
  onItemsChange,
  label = 'Menu Items',
  loadingMessage = 'Loading menu items...',
  errorMessage = 'Could not load menu items. Try opening the modal again.',
  emptyMessage = 'No menu items available yet. Add items from Restaurant Page first.',
}: Props) {
  const [menuItems, setMenuItems] = useState<MenuItem[]>([]);
  const [selectedItemNames, setSelectedItemNames] = useState<string[]>([]);
  const [loadingMenuItems, setLoadingMenuItems] = useState(false);
  const [menuItemsLoadFailed, setMenuItemsLoadFailed] = useState(false);

  useEffect(() => {
    onItemsChange(selectedItemNames);
  }, [onItemsChange, selectedItemNames]);

  useEffect(() => {
    if (!restaurant) {
      setMenuItems([]);
      setSelectedItemNames([]);
      setLoadingMenuItems(false);
      setMenuItemsLoadFailed(false);
      return;
    }

    let cancelled = false;

    const loadMenuItems = async () => {
      setLoadingMenuItems(true);
      setMenuItemsLoadFailed(false);

      const loadedMenuItems = await restaurantApi.getMenuItems(restaurant);
      if (cancelled) {
        return;
      }

      if (!loadedMenuItems) {
        setMenuItems([]);
        setSelectedItemNames([]);
        setMenuItemsLoadFailed(true);
        setLoadingMenuItems(false);
        return;
      }

      setMenuItems(loadedMenuItems);
      setSelectedItemNames(current =>
        current.filter(selectedItemName =>
          loadedMenuItems.some(menuItem => menuItem.name === selectedItemName),
        ),
      );
      setLoadingMenuItems(false);
    };

    void loadMenuItems();

    return () => {
      cancelled = true;
    };
  }, [restaurant]);

  return (
    <div className="space-y-2">
      <p className="block text-sm font-medium text-gray-700 dark:text-gray-300">
        {label}
      </p>
      {loadingMenuItems ? (
        <Loading>{loadingMessage}</Loading>
      ) : menuItemsLoadFailed ? (
        <LoadError>{errorMessage}</LoadError>
      ) : menuItems.length === 0 ? (
        <p className="text-sm text-gray-500 dark:text-gray-400">
          {emptyMessage}
        </p>
      ) : (
        <div className="max-h-40 space-y-2 overflow-y-auto rounded border border-gray-300 p-3 dark:border-gray-700">
          {menuItems.map(menuItem => {
            const checked = selectedItemNames.includes(menuItem.name);
            return (
              <FormField
                key={menuItem.id.id}
                label={menuItem.name}
                type="checkbox"
                name={`menu-item-${menuItem.id.id}`}
                value={menuItem.name}
                checked={checked}
                required={selectedItemNames.length === 0}
                onChange={event => {
                  setSelectedItemNames(current =>
                    event.target.checked
                      ? current.includes(menuItem.name)
                        ? current
                        : [...current, menuItem.name]
                      : current.filter(name => name !== menuItem.name),
                  );
                }}
              />
            );
          })}
        </div>
      )}
      {selectedItemNames.length > 0 && (
        <p className="text-xs text-gray-500 dark:text-gray-400">
          Selected: {selectedItemNames.join(', ')}
        </p>
      )}
    </div>
  );
}
