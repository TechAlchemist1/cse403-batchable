import type {HttpMethods} from 'msw';
import {apiFetch} from './fetch';

export type Resource = `/${string}`;

export async function fetchEndpoint(
  method: `${HttpMethods}`,
  path: Resource,
  body?: unknown,
) {
  const response = await apiFetch(path, {
    body: body !== undefined ? JSON.stringify(body) : undefined,
    method,
    headers: {
      'Content-Type': 'application/json',
    },
  });
  if (!response.ok) {
    const bodyText = await response.text();
    throw new Error(
      `[${method} ${path}] ${response.status} (${response.statusText}): ${bodyText}`,
    );
  }
  return response;
}

export async function fetchJSON(
  method: `${HttpMethods}`,
  path: Resource,
  body?: unknown,
) {
  return await (await fetchEndpoint(method, path, body)).json();
}
