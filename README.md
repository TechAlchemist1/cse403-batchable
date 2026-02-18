# cse403-batchable

Our product is Batchable, a real-time food delivery batching system. It works as a web application that interfaces with medium-sized restaurants, constantly checking if orders should go out as singles, doubles, or triples, using live updates about new orders, readiness, and remakes. Typically, dispatchers have to balance delivery times, food freshness, and how drivers are used–all while handling new orders, remakes, and cancellations. For medium-sized restaurants, this is inevitably done by hand, which leads to inconsistent results and avoidable delivery problems. That’s where Batchable comes in. We will handle this delicate balance for businesses, leading to simpler workflows, fewer headaches, and better resource utilization.

A link to our requirements and plan can be found here: [Batchable](https://docs.google.com/document/d/1lBQPrSYdc8PdP-THlGFKEYEQGw-icpVO-P4352XmHsA/edit?usp=sharing)

### Prerequisites

Install node.js via the [node.js](https://nodejs.org/en)
Download JDK 17 [here](https://www.oracle.com/java/technologies/javase/jdk17-archive-downloads.html)\
Download Docker Desktop [here](https://www.docker.com/products/docker-desktop/)

## Database (Local Development)

Make sure you have Docker Desktop installed and running, and at the bottom left it says engine is running

# Running the App!

Put the env file sent in Ed into vars.env in the root of the project.
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

**We currently only support the creation of orders**

Follow [this link](https://console.twilio.com/us1/develop/sms/virtual-phone) to take you to the virtual phone where you will recieve the text confirmation\
You'll need to log in using the provided credentials:\
User Email: (see Ed)\
Password: (see Ed)

Select try another method rather than sending 6-digit recovery code\
Select recovery code as verification method when logging in:

From there you will see a drop down on the right side with the text " Choose a sender number" click on it and select the only number available.
**Order creations will text the order id to this phone**

## Code Coverage (front-end and back-end)

Ensure Docker Desktop is running\
`$ docker` should return docker commands

### Front-end code coverage
From the `frontend` directory:
```bash
npm test
```

### Back-end code coverage
From the `backend` directory:
```bash
./mvnw verify
```

Look for coverage results in `backend/target/site/jacoco/index.html`

## Editor/Git Configuration

Please install an [EditorConfig](https://editorconfig.org/) extension/plug-in for your editor so as to use our global .editorconfig settings and not mess up spacing, indentation, or line endings. If you're on Windows, please also use Git's `autocrlf=false` option, which is likely already enabled. If it's not, you can use:

```sh
$ git config --global autocrlf false
```
