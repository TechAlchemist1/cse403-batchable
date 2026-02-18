import {startTransition, StrictMode} from 'react';
import {hydrateRoot} from 'react-dom/client';
import {HydratedRouter} from 'react-router/dom';

async function enableApiMocking() {
  if (process.env.NODE_ENV !== 'development') {
    return;
  }

  const {worker} = await import('~/../test/mocks/api/browser');

  return worker.start();
}

void enableApiMocking().then(() => {
  startTransition(() => {
    hydrateRoot(
      document,
      <StrictMode>
        <HydratedRouter />
      </StrictMode>,
    );
  });
});
