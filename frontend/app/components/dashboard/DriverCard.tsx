import type {Driver} from '~/domain/objects';
import {formatDriverName, formatPhoneNumber} from '~/util/format';
import Card from '../Card';

interface Props {
  driver: Driver;
}

export default function DriverCard({driver}: Props) {
  // const editDriverModal = useModal();
  return (
    <>
      <Card>
        <div className="flex justify-between items-startr flex-col">
          <h2 className="font-bold text-gray-900 dark:text-gray-100">
            {formatDriverName(driver)}
          </h2>

          <p className="text-xs font-medium text-blue-500">
            {formatPhoneNumber(driver.phoneNumber)}
          </p>
        </div>
        <p
          className={`text-sm text-gray-500 ${
            driver.onShift
              ? 'text-green-800 dark:text-green-200'
              : 'text-red-400 dark:text-red-600'
          }`}
        >
          Status: {driver.onShift ? 'ON' : 'OFF'} SHIFT
        </p>
      </Card>
      {/* <EditDriverModal driver={driver} state={editDriverModal} /> */}
    </>
  );
}
