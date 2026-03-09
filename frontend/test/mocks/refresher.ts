import {act} from '@testing-library/react';
import type {RefreshMonitor} from '~/components/OrderRefreshProvider';

/**
 * Represents a mock version of a RefreshMonitor, for use in an <OrderRefreshContext>.
 * Simply call `.refresh()` to simulate the SSE event.
 */
export class MockOrderRefresher extends EventTarget {
  refresh() {
    act(() => {
      this.dispatchEvent(new Event('orderUpdate'));
    });
  }
  close() {}
  static create() {
    // safe since it's only missing a private property which isn't cross-referenced
    return new MockOrderRefresher() as unknown as RefreshMonitor &
      MockOrderRefresher;
  }
}
