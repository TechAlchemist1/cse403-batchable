## High Level Goal
Develop frontend-to-backend production-ready software for the Beta Release, demonstrating efficient delivery routing.

## Original Goals for the Week
- Add test automation and CI to the development plan: We will split into front-end and back-end teams to write this. Time estimate: 1-2hrs for each team.
- Set up test infrastructure and chosen CI services: we will again split into our front-end and back-end teams. Time estimate: 1-2hrs for each team.
- Begin working on implementing our architectural design indivually on seperate components. Time estimate: 1+ hrs per person (there should at least be something to test).
- Set up our database to store business credentials, and implement login authentication with Auth0 to work with the APIs we are using to have code to actually test. Time estimate: 4-8 hrs.

## Progress and Issues
- We were able to achieve last week's goals without running into significant roadblocks! Got our CI running and testing infrastructure set up.

## Questions for the Product Owner
- What does it mean for a major use case to touch all major components of the system? For example with the Twilio Manager, is it okay for it to just send a message to a driver, but not receive an order delivery confirmation from the driver within the same use case?

## Goals for Next Week
We have noticed that a lot of our work that we have is dependent on our backend components, so we'd like to split some backend work to some of our frontend engineers
- Emily: Skeleton for Backend Webserver & Batching manager: 2-3 hrs, Basic batching algorithm and integrating it with google api: 3-4 hrs, keep adding to these components: Unknown
- Qalid: Fill in the skeleton for the Twilio Manager. 3 hours
- Zane: Fill in skeleton for batching manager, and finish off CI tests for DB Manager. 3 hours
- Ben: Dashboard: Add Order, View Orders, View Routes, View Drivers. Adding UI for Restaurant Page. Time estimate: Sunday night.
- Delano: Fill in the skeleton for the Backend Web Server. Time estimate: 3 hours
- H: Restaurant Page (View/Edit Restaurant, View/Edit Drivers, View/Edit Menu Items) based on Ben's design. Time estimate: 5-6 hours 
