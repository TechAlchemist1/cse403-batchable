# Welcome to the Batchable Front-end!
We are using the following tools for this client side portion of the codebase, so here are some quick links to relevant sites:
* [Vite](https://vite.dev/)
* [React Router](https://reactrouter.com/)
* [React](https://react.dev/)
* [TypeScript](https://www.typescriptlang.org/)
* [Tailwind CSS](https://tailwindcss.com/)

In a less code-related sense, we also depend on [gts](https://github.com/google/gts), which automatically enforces the [Google TypeScript Style Guide](https://google.github.io/styleguide/tsguide.html). More on how to use this in the next section.

## Common Automated Operations
`npm` scripts are the primary way for setting up automated tasks in this project, of which there are only a few:
* `npm run dev`, which will start the front-end in development mode (with HMR!), without the server. This is likely to only work for the first week, right up until we need to depend on the back-end APIs.
* `npm run lint`, which will run the linter and formatter without changing any files, and produce a list of all the violations it found.
* `npm run fix`, which is just like `lint` except it also tries to fix as much as it can. Please run this before committing code.

## Project Setup: So you've just pulled...
Just `cd frontend`, `npm install`, and you're ready to go!

## Editor Setup

### VSCode
Installing the extensions for [ESLint](https://marketplace.visualstudio.com/items?itemName=dbaeumer.vscode-eslint) and [Prettier](https://marketplace.visualstudio.com/items?itemName=esbenp.prettier-vscode) will make the linter much more friendly. Getting Prettier to work properly may require adding a line in `.vscode/settings.json`:
```json
"[javascript][typescript][typescriptreact]": {
  "editor.defaultFormatter": "esbenp.prettier-vscode"
}
```

The command "Format Document" (Alt + Shift + F) will run the formatting portion of `npm run fix`.

### IntelliJ
*Others, please add subsections for your optimal editor setups, I only know one*
