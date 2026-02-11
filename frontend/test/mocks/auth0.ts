import {vi} from 'vitest';

vi.mock('@auth0/auth0-react', () => ({
  useAuth0: () => ({
    isAuthenticated: true,
    isLoading: false,
    user: {
      name: 'Test User',
      email: 'test@example.com',
    },
    loginWithRedirect: vi.fn(),
    logout: vi.fn(),
    getAccessTokenSilently: vi.fn(),
  }),
  Auth0Provider: ({children}: {children: React.ReactNode}) => children,
}));
