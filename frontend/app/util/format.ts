import type {Driver, Order, PhoneNumber} from '~/domain/objects';
import {MS_PER_MINUTE} from './time';

/**
 * Formats a phone number for display in the UI.
 * @param phoneNumber The phone number to represent
 * @returns A more human-readable version of the phone number
 */
export function formatPhoneNumber(phoneNumber: PhoneNumber): string {
  const match = phoneNumber.compact.match(/(\d{3})(\d{3})(\d{4})/);
  if (!match) return phoneNumber.compact;
  const [, a, b, c] = match;
  return `(${a}) ${b}-${c}`;
}

/**
 * Formats a date and time for display in the UI.
 * @param dateTime The instant to represent
 * @returns A human-readable version of the instant
 */
export function formatDateTime(dateTime: Date): string {
  return dateTime.toString();
}

/**
 * Formats a time interval for display in the UI.
 * @param intervalMs The number of milliseconds in the time interval, the natural result of subtracting dates
 * @returns A human-readable version of the interval
 */
export function formatTimeInterval(intervalMs: number): string {
  return new Intl.RelativeTimeFormat(undefined, {style: 'short'}).format(
    Math.floor(intervalMs / MS_PER_MINUTE),
    'minute',
  );
}

/**
 * Formats the string name of an order for display in the UI
 * @param order The order to find the name of
 * @returns A human-readable short name summary of the order
 */
export function formatOrderName(order: Order): string {
  return `Order #${order.id.id}${order.highPriority ? ' ❗' : ''}`;
}

/**
 * Formats the string name of an driver for display in the UI
 * @param driver The driver to find the name of
 * @returns A human-readable short name summary of the driver
 */
export function formatDriverName(driver: Driver): string {
  return `${driver.name} (Driver)`;
}
