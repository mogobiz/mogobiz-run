## Mogobiz-run Project

Project based on
* _spray-can_, Scala 2.10 + Akka 2.2 + spray 1.2 (the `on_spray-can_1.2` branch)

Follow these steps to get started:

1. Git-clone this repository.

        $ git clone git://... mogobiz-run

2. Change directory into your clone:

        $ cd mogobiz-run

3. Launch SBT:

        $ sbt

4. Compile everything and run all tests:

        > test

5. Start the application:

        > re-start

6. Browse to http://localhost:8082/
ie : http://localhost:8082/store/mogobiz/products?currency=EUR&country=FR&lang=FR

7. Stop the application:

        > re-stop

8. Check out the SOAPui Project in the soapui directory