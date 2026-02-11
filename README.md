# cse403-batchable

Our product is Batchable, a real-time food delivery batching system. It works as a web application that interfaces with medium-sized restaurants, constantly checking if orders should go out as singles, doubles, or triples, using live updates about new orders, readiness, and remakes. Typically, dispatchers have to balance delivery times, food freshness, and how drivers are used–all while handling new orders, remakes, and cancellations. For medium-sized restaurants, this is inevitably done by hand, which leads to inconsistent results and avoidable delivery problems. That’s where Batchable comes in. We will handle this delicate balance for businesses, leading to simpler workflows, fewer headaches, and better resource utilization.

The directory so far has only a section for our weekly status reports. We will update this file as we add more sections. 

A link to our requirements and plan can be found here: https://docs.google.com/document/d/1lBQPrSYdc8PdP-THlGFKEYEQGw-icpVO-P4352XmHsA/edit?usp=sharing

## Editor/Git Configuration
Please install an [EditorConfig](https://editorconfig.org/) extension/plug-in for your editor so as to use our global .editorconfig settings and not mess up spacing, indentation, or line endings. If you're on Windows, please also use Git's `autocrlf=true` option, which is likely already enabled. If it's not, you can use:
```sh
$ git config --global autocrlf true
```
Thank you!

## Database (Local Development)

We use Postgres running in Docker for local development. The database data persist via Docker and Schema/Migrations will live under infra/postgres

### Prerequisites
- Docker Desktop
- Node.js (for npm scripts)

### Start the database
```bash
docker compose up -d db
```

### Connect to Postgres
``` bash
npm run db:psql
```


