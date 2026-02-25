# Batchable - Developer Documentation

## 1. How to obtain the source code
To obtain the Batchable source code, clone the main repository from GitHub using git clone. All system components, including the backend, frontend, documentation, and configuration files, are contained within this single repository.Developers should then create a new feature branch for changes they would like to make before beginning development.

If environment variables are required such as API keys, developers should create a local .env file. Sensitive values should never be committed to version control.

## 2. The layout of the directory structure
The repository is organized into three primary components: infra, frontend, and backend. Each component has a clearly defined responsibility within the system architecture.

The infra directory contains the infrastructure and database configuration for the system. This section handles the creation, initialization, and maintenance of the PostgreSQL database using SQL files. It defines schema setup, table creation, and any required database configuration necessary for the backend to operate correctly.

The frontend directory contains the client-side application and its associated tests. The main application code is located under frontend/app/. Within this directory, the application is structured by concern: api/ contains frontend API wrappers that communicate with the backend, components/ contains reusable UI components, domain/ defines shared types and domain-level logic, routes/ contains route-level pages and navigation logic, and util/ stores general utility functions used throughout the frontend. Static assets are located under frontend/public/. Frontend tests are located under frontend/test/ and mirror the structure of the application layer.

The backend directory contains the server-side application implemented in Java. The primary source code resides in backend/src/main/java/. This layer contains the core application components, including the controllers that define HTTP endpoints, the services layer that implements business logic, and the database manager responsible for interacting with the persistence layer. -- fill in the requistie parts for the folders of the backend


## 3. How to build the software
Batchable uses Maven as its build system. To build the software, ensure that Java version 17 and Maven are installed on your system. Ensure Docker is running and execute docker compose up to start PostgreSQL.

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
All automated tests for the backend are located in src/test/java. All automated tests for the frontend are located in frontend/test. To execute the full test suite, run mvn test from the root directory. This will execute all tests.

Test coverage reports can be generated using the Maven build lifecycle and reviewed in the generated target/site directory. Developers should ensure that all tests pass before committing changes or opening a pull request.


## 5. How to add new tests
New backend tests must be added under backend/src/test/java/ and should mirror the package structure of the code being tested. Test classes should follow the naming convention ClassNameTest. Backend tests should validate service-layer logic, controller behavior, batching algorithm functionality, database interactions, and external integrations such as Twilio where applicable. Mocking should be used when isolating external dependencies. All new backend features or bug fixes must include corresponding test coverage before being merged into main.

New frontend tests must be placed under frontend/test/ and follow the same structure as frontend/app/. Domain logic tests belong in frontend/test/domain/, route tests in frontend/test/routes/, and utility tests in frontend/test/util/. Shared test mocks should be placed in frontend/test/mocks/. Test files must follow the existing naming convention (e.g., *.test.ts) to ensure automatic discovery by the test runner. All new UI components and client-side logic must include appropriate tests.


## 6. How to build a release of the software
Releases must be built from the main branch after all tests pass. Before packaging, update the version number in pom.xml and any relevant documentation. To build the backend, run mvn clean package to generate the deployable artifact. To build the frontend, navigate to the frontend/ directory and run npm install. followed by npm run build to produce the production bundle.

