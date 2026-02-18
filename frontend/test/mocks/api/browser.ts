import {setupWorker} from 'msw/browser';
import {handlers} from './handlers';
import {seedMockDatabaseForBrowser} from './seed';

seedMockDatabaseForBrowser();

export const worker = setupWorker(...handlers);
