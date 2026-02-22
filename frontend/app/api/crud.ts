import type {DomainObject} from '~/domain/objects';
import {fetchEndpoint, fetchJSON, type Resource} from './common';
import * as json from '~/domain/json';

/**
 * Represents a base class for performing CRUD operations on domain objects of a given type.
 * Thanks to MSW, this works both in production and development!
 */
export class CrudApi<T extends DomainObject> {
  private static DELAY = 0;

  constructor(
    protected resource: Resource,
    protected parserPair: json.JSONParserPair<T>,
  ) {}

  delay(): Promise<void> {
    return new Promise(resolve => setTimeout(resolve, CrudApi.DELAY));
  }

  async create(domainObject: T) {
    try {
      await this.delay();
      const id = await fetchJSON(
        'POST',
        this.resource,
        this.parserPair.unparse(domainObject),
      );
      return this.parserPair.field('id').parse(id);
    } catch (err) {
      console.error(`Failed to create ${this.resource}`, domainObject, err);
      return null;
    }
  }

  async read({id}: T['id']) {
    try {
      await this.delay();
      return this.parserPair.parse(
        await fetchJSON('GET', `${this.resource}/${id}`),
      );
    } catch (err) {
      console.error(`Failed to read ${this.resource}; id=${id}`, err);
      return null;
    }
  }

  async exists({id}: T['id']) {
    try {
      await this.delay();
      await fetchJSON('GET', `${this.resource}/${id}`);
      return true;
    } catch {
      return false;
    }
  }

  async update(domainObject: T) {
    try {
      await this.delay();
      await fetchEndpoint(
        'PUT',
        `${this.resource}/${domainObject.id.id}`,
        this.parserPair.unparse(domainObject),
      );
      return true;
    } catch (err) {
      console.error(
        `Failed to update ${this.resource}; id=${domainObject.id.id}`,
        err,
      );
      return false;
    }
  }

  async delete({id}: T['id']) {
    try {
      await this.delay();
      await fetchEndpoint('DELETE', `${this.resource}/${id}`);
      return true;
    } catch (err) {
      console.error(`Failed to delete ${this.resource}; id=${id}`, err);
      return false;
    }
  }
}
