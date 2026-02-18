import {beforeEach, describe, expect, it, vi} from 'vitest';
import {
  screen,
  fireEvent,
  render,
  waitFor,
  within,
} from '@testing-library/react';
import {http, HttpResponse} from 'msw';
import {createRoutesStub} from 'react-router';
import * as json from '~/domain/json';
import type {Driver, MenuItem, Restaurant} from '~/domain/objects';
import RestaurantPage from '../../app/routes/restaurant';
import {RestaurantContext} from '../../app/components/RestaurantProvider';
import {db, endpoint} from '../mocks/api/common';
import {server} from '../mocks/api/server';

const restaurantId: Restaurant['id'] = {type: 'Restaurant', id: 1};

const testRestaurant: Restaurant = {
  id: restaurantId,
  name: 'Batchable Restaurant',
  location: {address: '123 Test Ave, Seattle, WA'},
};

const testDrivers: Driver[] = [
  {
    id: {type: 'Driver', id: 1},
    restaurant: restaurantId,
    name: 'Ben',
    phoneNumber: {compact: '2061112222'},
    onShift: true,
  },
];

const testMenuItems: MenuItem[] = [
  {
    id: {type: 'MenuItem', id: 1},
    restaurant: restaurantId,
    name: 'Mochi',
  },
];

const RestaurantStub = createRoutesStub([
  {
    path: '/',
    Component: () => (
      <RestaurantContext value={restaurantId}>
        <RestaurantPage />
      </RestaurantContext>
    ),
  },
]);

function getSectionByHeading(name: string) {
  const heading = screen.getByRole('heading', {name});
  const section = heading.closest('section');
  if (!section) throw new Error(`Section not found for heading: ${name}`);
  return section;
}

function seedRestaurantData() {
  db.restaurants.insert(json.restaurant.unparse(testRestaurant));
  testDrivers.forEach(driver => {
    db.drivers.insert(json.driver.unparse(driver));
  });
  testMenuItems.forEach(menuItem => {
    db.menuItems.insert(json.menuItem.unparse(menuItem));
  });
}

function getRestaurantFromDb() {
  const restaurant = db.restaurants.get(restaurantId.id);
  if (!restaurant) throw new Error('Restaurant not found in mock database');
  return json.restaurant.parse(restaurant);
}

function getDriversFromDb() {
  return db.drivers.findAll().map(json.driver.parse);
}

function getMenuItemsFromDb() {
  return db.menuItems.findAll().map(json.menuItem.parse);
}

async function renderLoadedRestaurantPage() {
  render(<RestaurantStub />);
  await screen.findByText('Ben');
}

describe('Restaurant page', () => {
  beforeEach(() => {
    vi.restoreAllMocks();

    vi.spyOn(console, 'error').mockImplementation(() => {});
    vi.spyOn(window, 'alert').mockImplementation(() => {});
    seedRestaurantData();
  });

  it('loads backend restaurant data and clears loading state', async () => {
    render(<RestaurantStub />);

    expect(screen.getByText('Loading restaurant data...')).toBeInTheDocument();

    await screen.findByText('Ben');
    await waitFor(() =>
      expect(
        screen.queryByText('Loading restaurant data...'),
      ).not.toBeInTheDocument(),
    );
    expect(
      screen.getByDisplayValue('Batchable Restaurant'),
    ).toBeInTheDocument();
    expect(screen.getByText('Mochi')).toBeInTheDocument();
  });

  it('shows load error when backend data is missing', async () => {
    server.use(
      http.get(endpoint('/restaurant/:id/drivers'), () => {
        return HttpResponse.json(null);
      }),
    );

    render(<RestaurantStub />);

    await screen.findByText('Could not load restaurant data from the backend.');
    expect(
      screen.queryByRole('heading', {name: 'Restaurant Details'}),
    ).not.toBeInTheDocument();
  });

  it('keeps drivers and menu edit modes mutually exclusive', async () => {
    await renderLoadedRestaurantPage();

    const driversSection = getSectionByHeading('Drivers');
    const menuSection = getSectionByHeading('Menu Items');

    fireEvent.click(
      within(driversSection).getByRole('button', {name: 'Edit Drivers'}),
    );
    expect(
      within(driversSection).getByRole('button', {name: 'Done Editing'}),
    ).toBeInTheDocument();

    fireEvent.click(
      within(menuSection).getByRole('button', {name: 'Edit Menu'}),
    );

    expect(
      within(driversSection).getByRole('button', {name: 'Edit Drivers'}),
    ).toBeInTheDocument();
    expect(
      within(menuSection).getByRole('button', {name: 'Done Editing'}),
    ).toBeInTheDocument();
  });

  it('updates restaurant details when finishing edit mode', async () => {
    await renderLoadedRestaurantPage();

    const detailsSection = getSectionByHeading('Restaurant Details');

    fireEvent.click(
      within(detailsSection).getByRole('button', {name: 'Edit Restaurant'}),
    );

    fireEvent.change(screen.getByDisplayValue('Batchable Restaurant'), {
      target: {value: 'Updated Batchable Restaurant'},
    });
    fireEvent.change(screen.getByDisplayValue('123 Test Ave, Seattle, WA'), {
      target: {value: '123 Updated St, Seattle, WA'},
    });

    fireEvent.click(
      within(detailsSection).getByRole('button', {name: 'Done Editing'}),
    );

    await waitFor(() =>
      expect(getRestaurantFromDb()).toMatchObject({
        name: 'Updated Batchable Restaurant',
        location: {address: '123 Updated St, Seattle, WA'},
      }),
    );
  });

  it('creates a new driver from the add driver modal and refreshes data', async () => {
    await renderLoadedRestaurantPage();

    const driversSection = getSectionByHeading('Drivers');
    fireEvent.click(
      within(driversSection).getByRole('button', {name: /\+ add driver/i}),
    );

    fireEvent.change(screen.getByLabelText('Driver Name'), {
      target: {value: 'Nina'},
    });
    fireEvent.change(screen.getByLabelText('Phone Number (digits only)'), {
      target: {value: '2063334444'},
    });
    fireEvent.click(screen.getByRole('button', {name: 'Add Driver'}));

    await screen.findByText('Nina');
    await waitFor(() => expect(getDriversFromDb()).toHaveLength(2));
    expect(getDriversFromDb()).toEqual(
      expect.arrayContaining([
        expect.objectContaining({
          restaurant: restaurantId,
          name: 'Nina',
          phoneNumber: {compact: '2063334444'},
          onShift: false,
        }),
      ]),
    );
  });

  it('alerts when creating a driver fails', async () => {
    server.use(
      http.post(endpoint('/driver'), () => {
        return HttpResponse.text('create failed', {status: 500});
      }),
    );

    await renderLoadedRestaurantPage();

    const driversSection = getSectionByHeading('Drivers');
    fireEvent.click(
      within(driversSection).getByRole('button', {name: /\+ add driver/i}),
    );

    fireEvent.change(screen.getByLabelText('Driver Name'), {
      target: {value: 'Failed Driver'},
    });
    fireEvent.change(screen.getByLabelText('Phone Number (digits only)'), {
      target: {value: '2065556666'},
    });
    fireEvent.click(screen.getByRole('button', {name: 'Add Driver'}));

    await waitFor(() =>
      expect(window.alert).toHaveBeenCalledWith('Failed to create driver.'),
    );
    expect(getDriversFromDb()).toHaveLength(1);
  });

  it('creates a new menu item from the add menu item modal', async () => {
    await renderLoadedRestaurantPage();

    const menuSection = getSectionByHeading('Menu Items');
    fireEvent.click(
      within(menuSection).getByRole('button', {name: /\+ add menu item/i}),
    );

    fireEvent.change(screen.getByLabelText('Item Name'), {
      target: {value: 'Udon'},
    });
    fireEvent.click(screen.getByRole('button', {name: 'Add Item'}));

    await screen.findByText('Udon');
    await waitFor(() => expect(getMenuItemsFromDb()).toHaveLength(2));
    expect(getMenuItemsFromDb()).toEqual(
      expect.arrayContaining([
        expect.objectContaining({
          restaurant: restaurantId,
          name: 'Udon',
        }),
      ]),
    );
  });

  it('alerts when creating a menu item fails', async () => {
    server.use(
      http.post(endpoint('/menu'), () => {
        return HttpResponse.text('create failed', {status: 500});
      }),
    );

    await renderLoadedRestaurantPage();

    const menuSection = getSectionByHeading('Menu Items');
    fireEvent.click(
      within(menuSection).getByRole('button', {name: /\+ add menu item/i}),
    );

    fireEvent.change(screen.getByLabelText('Item Name'), {
      target: {value: 'Failed Item'},
    });
    fireEvent.click(screen.getByRole('button', {name: 'Add Item'}));

    await waitFor(() =>
      expect(window.alert).toHaveBeenCalledWith('Failed to create menu item.'),
    );
    expect(getMenuItemsFromDb()).toHaveLength(1);
  });

  it('updates and deletes menu items in edit mode', async () => {
    await renderLoadedRestaurantPage();

    const menuSection = getSectionByHeading('Menu Items');
    fireEvent.click(
      within(menuSection).getByRole('button', {name: 'Edit Menu'}),
    );
    fireEvent.click(within(menuSection).getByRole('button', {name: 'Edit'}));

    fireEvent.change(within(menuSection).getByDisplayValue('Mochi'), {
      target: {value: 'Updated Mochi'},
    });
    fireEvent.click(within(menuSection).getByRole('button', {name: 'Done'}));

    await waitFor(() =>
      expect(getMenuItemsFromDb()).toEqual(
        expect.arrayContaining([
          expect.objectContaining({name: 'Updated Mochi'}),
        ]),
      ),
    );

    fireEvent.click(within(menuSection).getByRole('button', {name: 'Delete'}));
    await waitFor(() => expect(getMenuItemsFromDb()).toHaveLength(0));
  });
});
