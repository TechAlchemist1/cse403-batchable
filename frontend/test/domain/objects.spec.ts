import {describe, it, expect} from 'vitest';
import {fakeId, isStateBefore, nextStateAfter} from '~/domain/objects';

describe('fakeId', () => {
  it('produces an id matching the argument', () => {
    const id = fakeId('MyType');
    expect(id.id).toBeTypeOf('number');
    expect(id.id < 0).toBe(true);
    expect(id.type).toBe('MyType');
  });
});

describe('isStateBefore', () => {
  it('knows delivered is last', () => {
    expect(isStateBefore('cooking', 'delivered')).toBe(true);
    expect(isStateBefore('cooked', 'delivered')).toBe(true);
    expect(isStateBefore('driving', 'delivered')).toBe(true);
    expect(isStateBefore('delivered', 'delivered')).toBe(false);
  });

  it('knows cooking is first', () => {
    expect(isStateBefore('cooking', 'cooking')).toBe(false);
    expect(isStateBefore('cooked', 'cooking')).toBe(false);
    expect(isStateBefore('driving', 'cooking')).toBe(false);
    expect(isStateBefore('delivered', 'cooking')).toBe(false);
  });
});

describe('nextStateAfter', () => {
  it('has the order correct', () => {
    expect(nextStateAfter('cooking')).toBe('cooked');
    expect(nextStateAfter('cooked')).toBe('driving');
    expect(nextStateAfter('driving')).toBe('delivered');
  });
});
