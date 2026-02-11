import '@testing-library/jest-dom/vitest';
import {cleanup} from '@testing-library/react';
import {afterEach} from 'vitest';
import './mocks/auth0';

// Automatically clean up the DOM after each test
afterEach(() => {
  cleanup();
});
