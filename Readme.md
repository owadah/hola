# hola
hola microservice using Java EE (JAX-RS) on WildFly Swarm

The detailed instructions to run *Red Hat Helloworld MSA* demo, can be found at the following repository: <https://github.com/redhat-helloworld-msa/helloworld-msa>


Build and Deploy hola locally
-----------------------------

1. Open a command prompt and navigate to the root directory of this microservice.
2. Type this command to build and execute the application:

        mvn wildfly-swarm:run

3. This will create a uber jar at  `target/hola-swarm.jar` and execute it.
4. The application will be running at the following URL: <http://localhost:8080/api/hola>

Deploy the application in Openshift
-----------------------------------

1. Make sure to be connected to the Docker Daemon
2. Execute

		mvn clean package docker:build fabric8:json fabric8:apply


mvn clean package && ./fix-dependencies.sh && java -jar target/hola-swarm.jar -Dswarm.http.port=8181 -Dswarm.logging=DEBUG
java -jar target/hola-swarm.jar -Dswarm.logging=TRACE -Dswarm.http.port=8181 -Dlra.http.port=8180

To debug:
java -agentlib:jdwp=transport=dt_socket,address=8787,server=y,suspend=n -jar target/hola-swarm.jar -Dswarm.http.port=8181

-Dlra.http.host=localhost  ...changes the host where lra coordinator resides
-Dlra.http.port=8080  ...changes the port to say where lra coordinator resides
-Dswarm.logging=TRACE  ...change what swarm is logging
-Dswarm.http.port=8181  ...port of undertow is sitting at
-Dswarm.port.offset=100  ...port offset for the swarm instance
-Dswarm.bind.address  ...what interface the swarm bind to (0.0.0.0 is default)



