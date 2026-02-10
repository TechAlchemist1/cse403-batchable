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
    expect(screen.getByText(/Create New Order/i)).toBeInTheDocument();
  });
});
