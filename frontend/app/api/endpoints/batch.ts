import * as json from '~/domain/json';
import {CrudApi} from '../crud';
import type {Batch, Order} from '~/domain/objects';
import {fetchJSON} from '../common';

class BatchApi extends CrudApi<Batch> {
  constructor() {
    super('/order/batch', json.batch);
  }
  async getOrders({id}: Batch['id']) {
    try {
      const orders: json.JSONDomainObject<Order>[] = await fetchJSON(
        'GET',
        `${this.resource}/${id}/orders`,
      );
      return orders.map(json.order.parse);
    } catch (err) {
      this.error(`Cannot get orders for batch; id=${id}`, err);
      return null;
    }
  }
}

export const batchApi = new BatchApi();
