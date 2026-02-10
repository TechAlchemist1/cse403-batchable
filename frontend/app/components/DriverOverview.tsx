import React from 'react';

export default function DriverOverview() {
  const drivers = [
    {name: 'Ben', status: 'ON SHIFT', location: 'Store'},
    {name: 'Delano', status: 'DELIVERING', location: 'Store'},
  ];

  return (
    <div className="space-y-4">
      {drivers.map(driver => (
        <div
          key={driver.name}
          className="p-4 border rounded-lg bg-gray-50 dark:bg-gray-800 border-gray-200 dark:border-gray-700"
        >
          <p className="font-bold text-gray-900 dark:text-gray-100">
            {driver.name}
          </p>
          <div className="flex justify-between items-center">
            <p className="text-sm text-gray-500">Status: {driver.status}</p>
            <p className="text-xs font-medium text-blue-500">
              📍 {driver.location}
            </p>
          </div>
        </div>
      ))}
    </div>
  );
}
