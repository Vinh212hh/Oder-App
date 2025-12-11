# Oder-App
# PROJECT REPORT

ASYNCHRONOUS ORDER PROCESSING SYSTEM

USING RABBITMQ

Course: Startup / Distributed Systems (fill in actual course name)

# Group: 4

Student(s): Trịnh Quang Vinh-Phùng Văn Nghĩa

# TABLE OF CONTENTS

(Page numbers can be adjusted after printing/export)

1.	Introduction
2.	Project overview and motivation
3.	Problem analysis and system requirements
4.	System design
5.	Overall architecture: client - server - RabbitMQ
6.  Use Case diagram
7.  Activity diagram
8.  Sequence diagram
9.	Technologies, languages and tools
10.	Detailed design and source code structure
11.	Deployment and environment configuration
12.	User guide
13.	Evaluation
14.	Conclusion and future work
 
# INTRODUCTION

In the context of rapidly growing e-commerce, building order-processing systems that can handle a large number of users, respond quickly, and scale horizontally is a very practical need. This project implements 

a mini asynchronous order processing system following a client-server model, with RabbitMQ used as the central message broker.

The main goal is to provide a smooth user experience: when the customer clicks “Place order”, the UI responds almost immediately, while heavy tasks such as stock validation, inventory update, logging, and 

notifications are executed in the background. The system also supports multiple client machines connecting to a single server and RabbitMQ instance over LAN or VPN.

# 1. PROJECT OVERVIEW AND MOTIVATION

1.1. Project description

The project implements an asynchronous order management system using RabbitMQ as message broker. There are two main applications:

• SERVER (OrderGuiApp): admin application for managing products, processing orders, storing data and exporting reports.

• CLIENT (CustomerGuiApp): customer application for viewing products, placing orders and checking order status in real time.

1.2. Motivation

# Reasons for choosing this topic include:

• It models a realistic e-commerce scenario while still being small enough for a course project.

• It provides hands-on experience with RabbitMQ, a widely used message broker in microservices.

• It can be extended with various features such as real email sending, dashboards, analytics, etc.

• It fits the “startup-style mini system” requirement: a small company but designed with scalability in mind from the beginning.

2. PROBLEM ANALYSIS AND SYSTEM REQUIREMENTS

2.1. Functional requirements

From the assignment statement, the system must satisfy the following functional requirements:

• Provide a fast response when placing an order (within about 1 second).

• Ensure no orders are lost even when the system is under heavy load.

• After an order is stored, background tasks should be triggered: updating inventory, logging, and sending confirmation notifications.

• Support multiple clients connecting to the same server.

Main functions for the Customer (client application):

• View the list of products (id, name, price, stock).

• Place an order: select product, enter quantity, confirm.

• Receive a notification about the processing result (success or failure due to insufficient stock).

• Check order status by OrderId.

Main functions for the Admin (server application):

• Create / update / delete products.

• View current product inventory.

• View processed orders.

• Monitor processing logs.

• Export order report to a text file.

2.2. Non-functional requirements

• Performance: UI should remain responsive even when many orders are being processed in the background.

• Reliability: messages should not be lost during transmission.

• Scalability: it should be easy to add more clients without changing server logic.

• Portability: implemented in Java, the system can run on any machine with a proper JDK.

• Observability: logging and RabbitMQ management UI help with debugging and demonstration.

3. SYSTEM DESIGN

3.1. Overall architecture: client – server – RabbitMQ

The system architecture consists of three logical layers:

• Client (CustomerGuiApp): Swing desktop application run by the end-user.

• Server (OrderGuiApp + OrderWorker): Swing admin application plus a background worker that processes orders.

• RabbitMQ: message broker that decouples client and server through queues.

The following queues are used:

• ORDER_QUEUE: client publishes Order messages here.

• ORDER_STATUS_QUEUE: server publishes OrderStatusEvent messages here.

• PRODUCT_SYNC_REQUEST_QUEUE: client sends sync requests.

• PRODUCT_SYNC_QUEUE: server responds with the full list of products.

• PRODUCT_UPDATE_QUEUE: server sends incremental product updates.

• PRODUCT_DELETE_QUEUE: server notifies clients about product deletions.

In summary, the client never talks to the server directly; instead, all important operations go through RabbitMQ, which improves decoupling and scalability.

3.2. Use Case Diagram (description)

Actors:

• Customer – interacts with the client GUI.

• Admin – interacts with the server GUI.

Use cases for Customer:

• ViewProducts – view the list of products.

• PlaceOrder – place a new order.

• CheckOrderStatus – check the status of an existing order.

Use cases for Admin:

• ManageProducts – create/update/delete products.

• ViewOrders – see all processed orders.

• ExportReport – export orders to a text report file.

• MonitorLogs – monitor the logs of background processing.

In the printed report, a proper UML Use Case diagram can be drawn using tools such as StarUML or Draw.io based on this description.

3.3. Activity Diagram – order placement flow

The main activity flow for a successful order placement is:

1. Customer opens the client application; it requests product synchronization from the server.

2. Customer selects a product, enters quantity and clicks “Place order”.

3. Client validates the local stock value; if quantity is greater than stock, it shows an error.

4. If validation passes, client builds an Order object and publishes it to ORDER_QUEUE.

5. On the server side, OrderWorker consumes the Order message from ORDER_QUEUE.


6. OrderWorker sets status to PROCESSING, persists the order, and calls InventoryService to decrease stock.

7. If inventory is insufficient, the order is marked as FAILED and an OrderStatusEvent is sent back.

8. Otherwise, stock is updated, flags emailSent/inventoryUpdated/logWritten are set, status is updated to COMPLETED and stored.

9. An OrderStatusEvent is published to ORDER_STATUS_QUEUE.

10. Client receives the event, updates UI and shows a popup message to the user.

3.4. Sequence Diagram – successful order flow

The sequence diagram involves the following participants:

• Customer

• CustomerGuiApp

• RabbitMQ (ORDER_QUEUE, ORDER_STATUS_QUEUE)

• OrderWorker

• InventoryService

• OrderRepository

• CustomerGuiApp (status listener)

The message sequence is as described in the activity diagram: the client sends an Order to ORDER_QUEUE, the worker processes it and sends an OrderStatusEvent back to ORDER_STATUS_QUEUE, which the client consumes.

4. TECHNOLOGIES, LANGUAGES AND TOOLS

• Programming language: Java SE 17+.

• UI framework: Java Swing.

• Message broker: RabbitMQ.

• Libraries: RabbitMQ Java Client, Jackson (core, databind, jsr310 module).

• Build tool: Maven.

• Network: LAN or Radmin VPN for multiple machines.

• IDE: IntelliJ IDEA.

• UML tools: StarUML, Draw.io (recommended for formal diagrams).

5. DETAILED DESIGN AND SOURCE CODE STRUCTURE

5.1. Data model classes

• Product.java – represents a product with id, name, price and stock.

• Order.java – represents an order with id, customerName, productId, quantity, totalPrice, createdAt and several status flags.

• OrderStatus.java – enumeration for order states: CREATED, PROCESSING, COMPLETED, FAILED.

• OrderStatusEvent.java – data structure for status messages sent from server to client.

5.2. Business logic classes

• InventoryService.java – manages the list of products and persists them to products.json.

• OrderRepository.java – stores orders in memory and in orders.json.

• OrderWorker.java – background worker that consumes Order messages and produces OrderStatusEvents.

• RabbitMQConfig.java – central configuration for host, username, password and queue names.

• OrderProducer.java – helper on the client side for publishing orders to ORDER_QUEUE.

5.3. User interface classes

• OrderGuiApp.java – admin/server GUI showing product table, orders table and logs, and providing buttons for CRUD operations and report export.

• CustomerGuiApp.java – client GUI showing product table and logs, and providing buttons for placing orders, refreshing products and checking order status.

6. DEPLOYMENT AND ENVIRONMENT CONFIGURATION

To deploy and run the system across multiple machines:

1. Install RabbitMQ on the server machine and enable the management plugin.

2. Create a user (e.g., nhom4 / 1) and grant full permissions.

3. Open ports 5672 and 15672 on the server's firewall.

4. Configure RabbitMQConfig.HOST on all clients to point to the server's IP (LAN or Radmin VPN).

5. Ensure that clients can ping the server.

6. Run OrderGuiApp on the server, then run CustomerGuiApp on each client machine.

7. Optionally, package the client application as a runnable JAR or EXE.

7. USER GUIDE

Step-by-step usage:

1. Start RabbitMQ on the server.

2. Launch the server application (OrderGuiApp).

3. Add some sample products if the product list is empty.

4. Launch the client application (CustomerGuiApp) on one or more client machines.

5. Click “Refresh products” on the client to synchronize data.

6. Select a product, enter customer name and quantity, then click “Place order”.

7. Wait for the popup from the server indicating whether the order is accepted or rejected.

8. Use the “Check order status” feature to retrieve the latest state of an order.

9. On the server, use the “Export report” feature to generate a summary text report.

8. EVALUATION

Strengths:

• Clear separation between UI and background processing.

• Asynchronous communication via RabbitMQ improves scalability and responsiveness.

• File-based persistence allows state to survive restarts.

• Multi-client scenario can be demonstrated easily in class.

Limitations:

• No relational database; JSON files are used for simplicity.

• Swing UI is basic and suitable mainly for demonstration purposes.

• No authentication or authorization model for users.

• Error handling and retry mechanisms are still simple.

9. CONCLUSION AND FUTURE WORK

The project successfully demonstrates an asynchronous order processing system using RabbitMQ and Java Swing. It fulfills the core requirements: fast UI response, background processing, persistence of orders and 

products, and support for multiple clients over LAN/VPN.

Future improvements may include integrating a relational database, building a web-based dashboard, implementing real email notifications, and splitting the system into microservices with stronger security and robustness.

