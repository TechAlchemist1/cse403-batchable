import {type RouteConfig, index, route} from '@react-router/dev/routes';

export default [
  index('routes/home.tsx'),
  route('restaurant', 'routes/restaurant.tsx'),
  route('route/:token', 'routes/driver.tsx'),
] satisfies RouteConfig;
