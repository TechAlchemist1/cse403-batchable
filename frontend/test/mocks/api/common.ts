import * as json from '~/domain/json';
import {JSONTable} from './db';
import {http, HttpResponse} from 'msw';
import {StatusCodes} from 'http-status-codes';
import type {DomainObject} from '~/domain/objects';
import type {Resource} from '~/api/common';

export const db = {
  restaurants: new JSONTable(json.restaurant),
  orders: new JSONTable(json.order),
  drivers: new JSONTable(json.driver),
  menuItems: new JSONTable(json.menuItem),
  batches: new JSONTable(json.batch),
};

export const resetMockDatabase = () => {
  let key: keyof typeof db;
  for (key in db) {
    if (!Object.hasOwn(db, key)) continue;
    db[key].clear();
  }
};

export function endpoint(path: `/${string}`) {
  return `http://localhost:5173${path}`;
}

export function notFound(type: string) {
  return HttpResponse.text(`No such ${type}`, {status: StatusCodes.NOT_FOUND});
}

export function noContent() {
  return HttpResponse.json(undefined, {status: StatusCodes.NO_CONTENT});
}

export function asId<T extends DomainObject>(
  id: string | readonly string[] | undefined,
) {
  return Number(id) as json.JSONDomainObject<T>['id'];
}

export function makeCrudHandlers<T extends DomainObject>(
  resource: Resource,
  table: JSONTable<T>,
  operations = [
    'create' as const,
    'read' as const,
    'update' as const,
    'delete' as const,
  ],
) {
  const crud = {
    create: http.post(endpoint(resource), async req => {
      const id = table.insert(
        (await req.request.json()) as json.JSONDomainObject<T>,
      );
      return HttpResponse.json(id, {status: StatusCodes.CREATED});
    }),
    read: http.get(endpoint(`${resource}/:id`), async req => {
      const row = table.get(asId<T>(req.params.id));
      if (!row) {
        return notFound(resource);
      }
      return HttpResponse.json(row);
    }),
    update: http.put(endpoint(resource), async req => {
      if (
        !table.update((await req.request.json()) as json.JSONDomainObject<T>)
      ) {
        return notFound(resource);
      }
      return noContent();
    }),
    delete: http.delete(endpoint(`${resource}/:id`), async req => {
      if (!table.delete(asId<T>(req.params.id))) {
        return notFound(resource);
      }
      return noContent();
    }),
  };

  return operations.map(operation => crud[operation]);
}
