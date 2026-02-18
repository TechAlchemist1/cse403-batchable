import * as json from '~/domain/json';
import {CrudApi} from '../crud';
import type {Batch, Driver} from '~/domain/objects';
import {fetchEndpoint, fetchJSON} from '../common';

class DriverApi extends CrudApi<Driver> {
  constructor() {
    super('/driver', json.driver);
  }
  async setOnShift({id}: Driver['id'], onShift: boolean) {
    try {
      await fetchEndpoint(
        'PUT',
        `${this.resource}/${id}/shift?onShift=${onShift}`,
      );
      return true;
    } catch (err) {
      console.error(`Failed to change driver shift; id=${id}`, err);
      return false;
    }
  }
  async getBatch({id}: Driver['id']) {
    try {
      const batch: json.JSONDomainObject<Batch> | null = await fetchJSON(
        'PUT',
        `${this.resource}/${id}/batch`,
      );
      return batch === null ? null : json.batch.parse(batch);
    } catch (err) {
      console.error(`Failed to get driver batch; id=${id}`, err);
      return null;
    }
  }
}

export const driverApi = new DriverApi();
