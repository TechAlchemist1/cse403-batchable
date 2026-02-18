import {useEffect, useState} from 'react';
import type {Driver} from '~/domain/objects';
import Button from '../Button';

type DriverRowProps = {
  driver: Driver;
  isEditingSection: boolean;
  isEditingDriver: boolean;
  onStartEdit: () => void;
  onSave: (driver: Driver) => void;
  onDelete: () => void;
};

export default function DriverRow({
  driver,
  isEditingSection,
  isEditingDriver,
  onStartEdit,
  onSave,
  onDelete,
}: DriverRowProps) {
  const [draftDriver, setDraftDriver] = useState(driver);

  useEffect(() => {
    if (!isEditingDriver) {
      setDraftDriver(driver);
    }
  }, [driver, isEditingDriver]);

  return (
    <tr className="border-b border-gray-100 dark:border-gray-800">
      <td className="px-3 py-3">
        {isEditingSection && isEditingDriver ? (
          <input
            value={draftDriver.name}
            onChange={event =>
              setDraftDriver(current => ({
                ...current,
                name: event.target.value,
              }))
            }
            className="w-full rounded-md border border-gray-300 dark:border-gray-700 bg-white dark:bg-gray-950 px-2 py-1"
          />
        ) : (
          driver.name
        )}
      </td>
      <td className="px-3 py-3">
        {isEditingSection && isEditingDriver ? (
          <input
            value={draftDriver.phoneNumber.compact}
            onChange={event =>
              setDraftDriver(current => ({
                ...current,
                phoneNumber: {compact: event.target.value},
              }))
            }
            className="w-full rounded-md border border-gray-300 dark:border-gray-700 bg-white dark:bg-gray-950 px-2 py-1"
          />
        ) : (
          driver.phoneNumber.compact
        )}
      </td>
      <td className="px-3 py-3">
        {isEditingSection && isEditingDriver ? (
          <label className="inline-flex items-center gap-2">
            <input
              type="checkbox"
              checked={draftDriver.onShift}
              onChange={event =>
                setDraftDriver(current => ({
                  ...current,
                  onShift: event.target.checked,
                }))
              }
            />
            <span>On Shift</span>
          </label>
        ) : (
          <span
            className={`rounded-full px-2 py-1 text-xs font-semibold ${
              driver.onShift
                ? 'bg-emerald-100 text-emerald-700'
                : 'bg-gray-100 text-gray-700 dark:bg-gray-800 dark:text-gray-200'
            }`}
          >
            {driver.onShift ? 'On Shift' : 'Off Shift'}
          </span>
        )}
      </td>
      {isEditingSection && (
        <td className="px-3 py-3">
          <div className="flex items-center gap-2">
            <Button
              style={isEditingDriver ? 'blue' : 'indigo'}
              small
              onClick={() =>
                isEditingDriver ? onSave(draftDriver) : onStartEdit()
              }
            >
              {isEditingDriver ? 'Done' : 'Edit'}
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
