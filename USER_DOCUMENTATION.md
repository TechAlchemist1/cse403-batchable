# Batchable User Documentatoin

## 1. Description
Batchable is a real-time food delivery batching system designed for
medium-sized restaurants. It helps restaurant staff determine whether
orders should be dispatched individually or grouped into double or
triple batches.

Restaurant dispatching often requires balancing:
-Delivery times
-Food freshness
-Driver availability
-New incoming orders
-Remakes and cancellations

Batchable simplifies this process by automating order handling the batching process.

## 2. How to run the software
To run Batchable locally, you must install:

**Node.js**
https://nodejs.org/en

**JDK 17**
https://www.oracle.com/java/technologies/javase/jdk17-archive-downloads.html

**Docker Desktop**
https://www.docker.com/products/docker-desktop/

After installing Docker Desktop, ensure the bottom-left corner displays:
**"Engine Running."**

Installation -
1.  Clone the repository.
2.  Place the provided `vars.env` file in the root directory of the
    project.


## 3. How to run the software
From the project root, run:

``` bash
chmod +x ./vars.env
chmod +x ./build.sh
chmod +x ./run.sh

./build.sh
./run.sh
```

After the scripts complete successfully, open: http://localhost:5173
You should now be able to interact with Batchable!!


## 4. How to use the software

### Adding Drivers
To add drivers, navigate to the Manage Restaurant tab. In this section, you can add new drivers by entering their name and phone number. You can also set their status as either On Shift or Off Shift. Drivers marked as On Shift and not currently out for delivery will be assined to batches, while those marked Off Shift will not be assigned to batches. 

### Adding Menu Items
Under the Manage Restaurant tab, you can also add menu items that your restaurant offers. Just enter the name of the menu item and save it. You can also edit or remove menu items as needed to keep your menu up to date.

### Modifying Restaurant Information
Within the Manage Restaurant section, you can update your restaurant’s name and location. Editing this information ensures that delivery routes and dispatch calculations are based on the correct starting location.

### Creating an Order
To create a new order, click the Add New Order button. Enter the delivery address for the accordingly. Once submitted, the system will process the order and determine how it should be dispatched, assigning it to an available driver. 



## 5. How to report a bug

If you encounter an issue, please open an issue in the GitHub
repository: https://github.com/zgodil/cse403-batchable/issues

A good bug report should include:
Clear title, Operating system, Browser and version. Steps to reproduce, Expected behavior, Actual behavior, Screenshots, Error messages 

For more information about bug reporting guidelines please visit: https://bugzilla.mozilla.org/page.cgi?id=bug-writing.html

Providing detailed information helps the development team resolve issues
more quickly.

## Known bugs
-Driver can be taken off shift despite on a delivery
-Restaurant ID defaults to 1 due to authentication bug
-Drivers stay assigned to batches even after deleting all the batched orders

