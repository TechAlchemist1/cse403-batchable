import './mocks/fetch';
import '@testing-library/jest-dom/vitest';
import {cleanup} from '@testing-library/react';
import {afterAll, afterEach, beforeAll, vi} from 'vitest';
import './mocks/auth0';
import {server} from './mocks/api/server';
import {resetMockDatabase} from './mocks/api/common';

beforeAll(() => {
  HTMLDialogElement.prototype.showModal = vi.fn(function mock(
    this: HTMLDialogElement,
  ) {
    this.open = true;
  });
  HTMLDialogElement.prototype.close = vi.fn(function mock(
    this: HTMLDialogElement,
  ) {
    this.open = false;
  });
  server.listen();
});

// Automatically clean up the DOM after each test
afterEach(() => {
  cleanup();
  server.resetHandlers();
  resetMockDatabase();
});

afterAll(() => {
  server.close();
});
