import type {HttpMethods} from 'msw';
import {apiFetch} from './fetch';

export type Resource = `/${string}`;

export async function fetchEndpoint(
  method: `${HttpMethods}`,
  path: Resource,
  body?: unknown,
) {
  return await apiFetch(path, {
    body: body !== undefined ? JSON.stringify(body) : undefined,
    method,
    headers: {
      'Content-Type': 'application/json',
    },
  });
}

export async function fetchJSON(
  method: `${HttpMethods}`,
  path: Resource,
  body?: unknown,
) {
  return await (await fetchEndpoint(method, path, body)).json();
}
