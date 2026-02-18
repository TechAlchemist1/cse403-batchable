import {describe, it, expect} from 'vitest';
import {render, screen, fireEvent} from '@testing-library/react';
import {createRoutesStub} from 'react-router';
import Home from '../../app/routes/home';

describe('Home Dashboard', () => {
  const HomeStub = createRoutesStub([{path: '/', Component: Home}]);

  it('renders the dashboard and components', () => {
    render(<HomeStub />);
    expect(screen.getByText(/Batchable Dashboard/i)).toBeInTheDocument();
  });

  it('opens the modal on button click', () => {
    render(<HomeStub />);
    fireEvent.click(screen.getByRole('button', {name: /\+ Add New Order/i}));
    expect(screen.getByText(/Customer Address/i)).toBeInTheDocument();
    expect(screen.getByText(/Item Name\(s\)/i)).toBeInTheDocument();
    expect(screen.getByText(/Prep Time \(min\)/i)).toBeInTheDocument();
    expect(screen.getByText(/Delivery Time \(min\)/i)).toBeInTheDocument();
    expect(
      screen.getByRole('button', {name: /Create New Order/i}),
    ).toBeInTheDocument();
  });
});

// // Override global mock to simulate logged out behavior
// describe('Protected Route', () => {
//   it('redirects unauthenticated users away from dashboard', async () => {
//     vi.resetModules();

//     vi.doMock('@auth0/auth0-react', () => ({
//       useAuth0: () => ({
//         isAuthenticated: false,
//         isLoading: false,
//         loginWithRedirect: vi.fn(),
//       }),
//       Auth0Provider: ({children}: {children: React.ReactNode}) => children,
//     }));

//     const HomeStub = createRoutesStub([
//       {path: '/', Component: (await import('../../app/routes/home')).default},
//     ]);

//     render(<HomeStub />);

//     // expect(screen.queryByText(/Batchable Dashboard/i)).not.toBeInTheDocument();
//     expect(screen.getByText(/log in/i)).toBeInTheDocument();
//   });
// });
