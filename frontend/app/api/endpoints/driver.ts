import * as json from '~/domain/json';
import {CrudApi} from '../crud';
import type {Batch, Driver, Order} from '~/domain/objects';
import {fetchEndpoint, fetchJSON} from '../common';

class DriverApi extends CrudApi<Driver> {
  constructor() {
    super('/driver', json.driver);
  }
  async getRouteInfo(token: string) {
    try {
      const routeInfo: {
        driver: json.JSONDomainObject<Driver>;
        mapLink: string | null;
        orders: json.JSONDomainObject<Order>[] | null;
      } = await fetchJSON('GET', `${this.resource}/route/${token}`);

      return {
        driver: json.driver.parse(routeInfo.driver),
        mapLink: routeInfo.mapLink,
        orders: routeInfo.orders?.map(json.order.parse) ?? null,
      };
    } catch (err) {
      this.error(`Failed to read driver by token; token=${token}`, err);
      return null;
    }
  }
  async markReturned(token: string) {
    try {
      await fetchEndpoint('PUT', `${this.resource}/returned/${token}`);
      return true;
    } catch (err) {
      this.error(`Failed to mark driver as returned; token=${token}`, err);
      return false;
    }
  }
  async setOnShift({id}: Driver['id'], onShift: boolean) {
    try {
      await fetchEndpoint(
        'PUT',
        `${this.resource}/${id}/shift?onShift=${onShift}`,
      );
      return true;
    } catch (err) {
      this.error(`Failed to change driver shift; id=${id}`, err);
      return false;
    }
  }
  async getBatch({id}: Driver['id']) {
    try {
      const batch: json.JSONDomainObject<Batch> | null = await fetchJSON(
        'GET',
        `${this.resource}/${id}/batch`,
      );
      return batch === null ? null : json.batch.parse(batch);
    } catch (err) {
      this.error(`Failed to get driver batch; id=${id}`, err);
      return null;
    }
  }
}

export const driverApi = new DriverApi();
