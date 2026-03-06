import * as json from '~/domain/json';
import {CrudApi} from '../crud';
import type {Order} from '~/domain/objects';
import {fetchEndpoint} from '../common';

class OrderApi extends CrudApi<Order> {
  constructor() {
    super('/order', json.order);
  }
  async markDelivered({id}: Order['id'], driverToken: string) {
    try {
      await fetchEndpoint(
        'PUT',
        `${this.resource}/${id}/delivered/${driverToken}`,
      );
      return true;
    } catch (err) {
      this.error(`Failed to mark order as delivered; id=${id}`, err);
      return false;
    }
  }
  async advanceState({id}: Order['id']) {
    try {
      await fetchEndpoint('PUT', `${this.resource}/${id}/advance`);
      return true;
    } catch (err) {
      this.error(`Failed to advance order state; id=${id}`, err);
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
      this.error(`Failed to update order cooked time; id=${id}`, err);
      return false;
    }
  }
  async remake({id}: Order['id']) {
    try {
      await fetchEndpoint('PUT', `${this.resource}/${id}/remake`);
      return true;
    } catch (err) {
      this.error(`Failed to remake order; id=${id}`, err);
      return false;
    }
  }
}

export const orderApi = new OrderApi();
