import {useEffect, useRef, useState} from 'react';
import type {MenuItem} from '~/domain/objects';
import Button from '../Button';

type MenuItemRowProps = {
  menuItem: MenuItem;
  isEditingSection: boolean;
  isEditingMenuItem: boolean;
  onStartEdit: () => void;
  onSave: (menuItem: MenuItem) => void;
  onDelete: () => void;
};

export default function MenuItemRow({
  menuItem,
  isEditingSection,
  isEditingMenuItem,
  onStartEdit,
  onSave,
  onDelete,
}: MenuItemRowProps) {
  const [draftMenuItem, setDraftMenuItem] = useState(menuItem);
  const wasEditingSection = useRef(isEditingSection);
  const wasEditingMenuItem = useRef(isEditingMenuItem);

  const hasDraftChanges = draftMenuItem.name !== menuItem.name;

  useEffect(() => {
    if (!isEditingMenuItem) {
      setDraftMenuItem(menuItem);
    }
  }, [menuItem, isEditingMenuItem]);

  useEffect(() => {
    const closedSection = wasEditingSection.current && !isEditingSection;
    if (closedSection && wasEditingMenuItem.current && hasDraftChanges) {
      onSave(draftMenuItem);
    }
    wasEditingSection.current = isEditingSection;
    wasEditingMenuItem.current = isEditingMenuItem;
  }, [
    draftMenuItem,
    hasDraftChanges,
    isEditingMenuItem,
    isEditingSection,
    onSave,
  ]);

  return (
    <tr className="border-b border-gray-100 dark:border-gray-800">
      <td className="px-3 py-3">
        {isEditingSection && isEditingMenuItem ? (
          <input
            value={draftMenuItem.name}
            onChange={event =>
              setDraftMenuItem(current => ({
                ...current,
                name: event.target.value,
              }))
            }
            className="w-full rounded-md border border-gray-300 dark:border-gray-700 bg-white dark:bg-gray-950 px-2 py-1"
          />
        ) : (
          menuItem.name
        )}
      </td>
      {isEditingSection && (
        <td className="px-3 py-3">
          <div className="flex items-center gap-2">
            <Button
              style={isEditingMenuItem ? 'orange' : 'amber'}
              small
              onClick={() =>
                isEditingMenuItem ? onSave(draftMenuItem) : onStartEdit()
              }
            >
              {isEditingMenuItem ? 'Done' : 'Edit'}
            </Button>
            <Button style="red" small onClick={onDelete}>
              Delete
            </Button>
          </div>
        </td>
      )}
    </tr>
  );
}
