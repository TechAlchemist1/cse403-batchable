import {describe, it, expect} from 'vitest';
import type {Order} from '~/domain/objects';
import {
  formatDateTime,
  formatDriverName,
  formatOrderName,
  formatPhoneNumber,
  formatTimeInterval,
} from '~/util/format';
import {MS_PER_MINUTE} from '~/util/time';

describe('time interval formatting', () => {
  it('makes 15 minutes contain 15', () => {
    expect(formatTimeInterval(15 * MS_PER_MINUTE)).toMatch(/15/);
  });
  it('makes 72 minutes contain 72', () => {
    expect(formatTimeInterval(72 * MS_PER_MINUTE)).toMatch(/72/);
  });
});

describe('date time formatting', () => {
  it('follows the toString() format', () => {
    const date = new Date('9:25 pm, 1/23/25 PST');
    expect(formatDateTime(date)).toBe(date.toString());
  });
});

describe('order name formatting', () => {
  const ORDER: Order = {
    id: {
      type: 'Order',
      id: 52,
    },
    cookedTime: new Date(),
    initialTime: new Date(),
    deliveryTime: new Date(),
    currentBatch: null,
    destination: {address: 'Seattle, WA'},
    highPriority: false,
    itemNames: ['Hello', 'World'],
    restaurant: {
      type: 'Restaurant',
      id: 1978,
    },
    state: 'cooking',
  };
  it('shows the order id', () => {
    expect(formatOrderName(ORDER)).toBe('Order #52');
  });
  it('shows high priority', () => {
    expect(
      formatOrderName({
        ...ORDER,
        highPriority: true,
      }),
    ).toBe('Order #52 ❗');
  });
});

describe('driver name formatting', () => {
  it('shows the driver name', () => {
    expect(
      formatDriverName({
        id: {
          type: 'Driver',
          id: 5,
        },
        name: 'Ben',
        onShift: true,
        phoneNumber: {compact: '9817238716'},
        restaurant: {
          type: 'Restaurant',
          id: 123,
        },
      }),
    ).toBe('Ben (Driver)');
  });
});

describe('phone number formatting', () => {
  it('makes 10-digit numbers pretty', () => {
    expect(formatPhoneNumber({compact: '8173468170'})).toBe('(817) 346-8170');
  });
  it('leaves other phone numbers unchanged', () => {
    expect(formatPhoneNumber({compact: ''})).toBe('');
    expect(formatPhoneNumber({compact: '198274187623'})).toBe('198274187623');
    expect(formatPhoneNumber({compact: '912'})).toBe('912');
  });
});
