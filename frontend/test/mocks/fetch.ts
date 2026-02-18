import {vi} from 'vitest';
import {endpoint} from './api/common';
vi.mock('~/api/fetch', () => ({
  apiFetch: (url: `/${string}`, init: RequestInit) =>
    fetch(endpoint(url), init),
}));
