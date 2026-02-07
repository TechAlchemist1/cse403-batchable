import {defineConfig} from 'eslint/config';
import {includeIgnoreFile} from '@eslint/compat';
import gts from 'gts';
import path from 'node:path';

export default defineConfig([
  ...gts,
  {
    // gts expects sourceType: commonjs
    files: ['**/.*.js', '**/*.js'],
    languageOptions: {
      sourceType: 'module',
    },
  },
  includeIgnoreFile(path.resolve(import.meta.dirname, '.gitignore')),
]);
