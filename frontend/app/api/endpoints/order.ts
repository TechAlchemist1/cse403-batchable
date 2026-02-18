import * as json from '~/domain/json';
import {CrudApi} from '../crud';
import type {Order} from '~/domain/objects';
import {fetchEndpoint} from '../common';

class OrderApi extends CrudApi<Order> {
  constructor() {
    super('/order', json.order);
  }
  async advanceState({id}: Order['id']) {
    try {
      await fetchEndpoint('PUT', `${this.resource}/${id}/advance`);
      return true;
    } catch (err) {
      console.error(`Failed to advance order state; id=${id}`, err);
      return false;
    }
  }
  async updateCookedTime({id}: Order['id'], cookedTime: Date) {
    try {
      await fetchEndpoint(
        'PUT',
        `${this.resource}/${id}/cookedTime`,
        cookedTime.toISOString(),
      );
      return true;
    } catch (err) {
      console.error(`Failed to update order cooked time; id=${id}`, err);
      return false;
    }
  }
  async remake({id}: Order['id']) {
    try {
      await fetchEndpoint('PUT', `${this.resource}/${id}/remake`);
      return true;
    } catch (err) {
      console.error(`Failed to remake order; id=${id}`, err);
      return false;
    }
  }
}

export const orderApi = new OrderApi();
