import {db, makeCrudHandlers} from '../common';
export const menuHandlers = [
  ...makeCrudHandlers('/menu', db.menuItems, [
    'create',
    'read',
    'update',
    'delete',
  ]),
];
