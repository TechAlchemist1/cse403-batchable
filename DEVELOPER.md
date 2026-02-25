# Batchable - Developer Documentation

## 1. How to obtain the source code
To obtain the Batchable source code, clone the main repository from GitHub using git clone. All system components, including the backend, frontend, documentation, and configuration files, are contained within this single repository.Developers should then create a new feature branch for changes they would like to make before beginning development.

If environment variables are required such as API keys, developers should create a local .env file. These values should never be committed to version control.

## 2. The layout of the directory structure
The repository is organized into three primary components: infra, frontend, and backend. Each component has a clearly defined responsibility within the system architecture.

The infra directory contains the infrastructure and database configuration for the system. This section handles the creation, initialization, and maintenance of the PostgreSQL database using SQL files. It defines schema setup, table creation, and any required database configuration necessary for the backend to operate correctly.

The frontend directory contains the client-side application and its associated tests. The main application code is located under frontend/app/. Within this directory, the application is structured as follows: api/ contains frontend API wrappers that communicate with the backend, components/ contains reusable UI components, domain/ defines shared types and domain-level logic, routes/ contains route-level pages and navigation logic, and util/ stores general utility functions used throughout the frontend. Static assets are located under frontend/public/. Frontend tests are located under frontend/test/ and follow the structure of the application layer.

The backend directory contains the server-side application implemented in Java. The primary source code resides in backend/src/main/java/. This layer contains the core application components, including the controllers that define HTTP endpoints, the services layer that implements business logic, and the database manager responsible for interacting with the persistence layer, the twilio manager which interacts with the twilio api, the websocket which handles communication with the frontend, client and models which handle the google API, and the batching alogrithim and manager which handles orchestration of batching.


## 3. How to build the software
Batchable uses Maven as its build system. To build the software, ensure that Java version 17 and Maven are installed on your system. Ensure Docker is running.

Put the add your .env file in the root of the project with your Google, Twilio API keys, and Database URL.
Then, while still in the root, execute the following in a sh-compatible terminal:

```bash
# in project root
chmod +x ./vars.env
chmod +x ./run.sh
chmod +x ./build.sh
./build.sh
./run.sh
```
and go to: http://localhost:5173

## 4. How to test the software
To run tests locally, after running `./build.sh` in the root of the project, run `npm test` from within the `frontend` folder. This will run all tests and, if successful, print a code coverage report after completion.

To get a prettier view of the testing infrastructure, run `npm test -- --ui` instead, which will provide a link (with a 5-ish digit port number) to view live test results. Very detailed code coverage information can be found via an icon in the upper right of the left panel of the Vitest Web UI.

To run the backend tests, navigate to the backend directory. and run `./mvnw clean test`. This will run all tests. To view the code coverage on the backend. From the
```bash
./mvnw clean verify
```
Look for coverage results in `backend/target/site/jacoco/index.html`

To see test results (and code coverage information) in the GitHub CI, look into the CI run details for the "Frontend CI / Build", and look under the step "Run Tests". This will show the code coverage report, or test failure reasons. PRs with under 70% branch and statement coverage for the front end should not be merged (unless those branches can be reasonably be shown to be impossible but required by the style guide), and new features need to adhere to these guidelines.


## 5. How to add new tests
For front-end:
To add a new test to the front-end, simply add a new file within frontend/test, ending in .spec.ts for normal unit tests or .spec.tsx for component and UI tests. If the feature you are testing is related to a specific file within frontend/app, place your testing file in the same place relative to frontend/test (e.g. tests for frontend/app/components/Modal.tsx should be in frontend/test/components/Modal.spec.tsx).

Almost every new test will need imports from our testing frameworks: Vitest and React Testing Library.
import {describe, it, expect} from 'vitest';
import {render, screen, /* etc */} from '@testing-library/react';

We follow the typical patterns for these tools, whose documentation websites can be found here for Vitest and here for RTL. Just like the rest of the front-end, please follow the Google TypeScript Style Guide.

Miscellaneous Advice: re-usable mock domain objects (Orders, Drivers, etc.) can be found in frontend/test/mocks/domain_objects.ts, and due to our mocking system, back-end API calls can be made using the normal interfaces (restaurantApi, orderApi, etc.). Thank you in advance for adding tests for your features!

For the backend:
To add a new test to the back-end, create a new test file under backend/src/test/java/ that mirrors the package structure of the file being tested.

Test classes should follow the naming convention:
-ClassNameTest.java for unit tests
-ClassNameIT.java for integration tests and have them not trigger the CI
-ClassNameIT_CI.java for integration tests and to have them add to the CI for github

Backend tests are written using JUnit and Mockito. We follow standard JUnit testing patterns. Unit tests should isolate business logic in: Services, The batching algorithm, The batching manager, The database manager 

Controller tests should verify HTTP behavior and response correctness. When testing components that depend on external services such as Twilio, those services should be mocked rather than invoked directly.

If a test requires database interaction, ensure the database is running before executing integration tests. All new backend features must include corresponding tests before being merged into main. Please follow existing test structure and naming conventions for consistency.


## 6. How to build a release of the software
Releases must be built from the main branch after all changes have been merged and validated. Before building a release, developers must ensure that all backend and frontend tests pass and that the application runs successfully in a local development environment.

Prior to packaging the release, update the version in the documentation.

