# NeonBee Kickstart Guide

This guide is very limited and focused on the absolute basics of *NeonBee*. This guide will teach you how to ...

* ... write *DataVerticles* and *EntityVerticles*.
* ... *require* data from another *DataVerticle*.
* ... expose data through the *RawEndpoint* and *ODataEndpoint*.
* ... test your written *DataVerticles* and *EntityVerticles*.

## Content

* [Prerequisite](#prerequisite)
* [Set up the project](#set-up-the-project)
* [Part 1: The DataVerticle](#part-1--the-dataverticle)
  * [Test the DataVerticle](#test-the-dataverticle)
  * [Request Data via RawEndpoint](#request-data-via-rawendpoint)
* [Part 2: Require data](#part-2--require-data)
  * [Test and mock DataVerticles](#test-and-mock-dataverticles)
* [Part 3: The EntityVerticle](#part-3--the-entityverticle)
  * [Add models subproject](#add-models-subproject)
  * [Define OData model](#define-odata-model)
  * [Write your first EntityVerticle](#write-your-first-entityverticle)
  * [Test EntityVerticle and mock DataVerticles](#test-entityverticle-and-mock-dataverticles)
  * [Request OData via ODataEndpoint](#request-odata-via-odataendpoint)
* [For Your Convenience](#for-your-convenience)

## Prerequisite

* OpenJDK 21
* Gradle 8+

## Set up the project

Before you can start, you need to create an empty Gradle project and apply the *Neonbee Application Plugin*:

1. Create a new empty Gradle Project `gradle init --type basic`
2. Apply the *Neonbee Application Plugin* by adding the following to your `build.gradle`

```groovy
plugins {
    id 'io.neonbee.gradle.kickstart.application' version '0.1.4'
}

neonbeeApplication {
    neonbeeVersion = '0.34.0'  // The NeonBee version
    workingDir = 'working_dir' // The working directory of NeonBee (Default: working_dir)
}

test {
    useJUnitPlatform()
}

repositories {
    mavenCentral()
}
```

## Part 1: The DataVerticle

Let's start and write your first *DataVerticle* called `BeehivesDataVerticle`. This is a very
simple *DataVerticle* that will return a static list of beehives.

```java
package org.example.neonbee;

import static io.vertx.core.Future.succeededFuture;

import com.google.common.annotations.VisibleForTesting;

import io.neonbee.NeonBeeDeployable;
import io.neonbee.data.DataContext;
import io.neonbee.data.DataMap;
import io.neonbee.data.DataQuery;
import io.neonbee.data.DataVerticle;
import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

// The NeonBeeDeployable annotation can only be added to DataVerticles, EntityVerticles or JobVerticles. The
// annotation is necessary because NeonBee will find all classes with this annotation and deploy it. All
// DataVerticles and EntityVerticles are connected via an event bus with each other and have their own
// address in the event bus. To avoid that there are address conflicts on the eventbus, every DataVerticle
// or EntityVerticle must have a namespace.
@NeonBeeDeployable(namespace = "example")
public class BeehivesDataVerticle extends DataVerticle<JsonArray> {

    @VisibleForTesting
    static final JsonObject BEEHIVE_1 = new JsonObject().put("id", 1).put("queen", "Daenerys");

    @VisibleForTesting
    static final JsonObject BEEHIVE_2 = new JsonObject().put("id", 2).put("queen", "Historia");

    // This is the internal name of the verticle. The name also controls whether DataVerticle is automatically
    // exposed through RawEndpoint. If the name begins with an underscore, the DataVerticle is not automatically
    // exposed. In this case it is automatically exposed.
    private static final String NAME = "Beehives";

    // The qualified name of the verticle defined normally through QUALIFIED_NAME constant is used to
    // address the verticle on the eventbus.
    public static final String QUALIFIED_NAME = createQualifiedName("example", NAME);

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public Future<JsonArray> retrieveData(DataQuery query, DataMap require, DataContext context) {
        // Return some hard-coded / static content
        return succeededFuture(new JsonArray().add(BEEHIVE_1).add(BEEHIVE_2));
    }
}
```

**The retrieveData method**

The `retrieveData` method is the centerpiece of this DataVerticle. This method creates the content
which will be returned from this DataVerticle. It is important to understand that this method is
returning a future because the result of this method could be generated in an asynchronous process step.
But in our simple example we return an already succeeded Future with our static data.

This method accepts 3 important parameters:

| Parameter | Details                                                                                                                                                                                                                                                                                                                                                                           |
|:---------:|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
|  require  | We skip require for now to reduce complexity. It will be explained in [Part 2: Require data](#part-2-require-data) where we create requests to already existing DataVerticles.                                                                                                                                                                                                    |
|   query   | The query parameter is of type DataQuery and contains all information about the incoming query, e.g. the type of the request (*READ*, *CREATE*, *UPDATE*, *DELETE*) or if the request comes in via the web endpoint, then it contains the path and (if they exist) query parameters, headers, etc.                                                                                |
|  context  | The context is a kind of "storage" which is available during the whole time of the complete E2E request flow and is passed to every DataVerticle. It contains the user principal, a correlation Id to correlate log output, or any data you put into the context. Be careful: Put as few data as possible into the context because it will be copied over the network every time. |

> **NOTE:** An E2E request flow normally means that a web request is coming into NeonBee via a web endpoint and then triggers several DataVerticles to fetch the required data and returns them back to the requestor.

### Test the DataVerticle

Now write a test to verify that the `BeehivesDataVerticle` works as expected. The `DataVerticleTestBase` offers a lot
of helper methods like `assertDataEquals` that makes testing your *DataVerticle* very comfortable.

> **NOTE:** An overview about this helper methods can be found in the *NeonBee Testing Strategy* document
> (TBD - will be published soon).

```java
package org.example.neonbee;

import static com.google.common.truth.Truth.assertThat;
import static org.example.neonbee.BeehivesDataVerticle.BEEHIVE_1;
import static org.example.neonbee.BeehivesDataVerticle.BEEHIVE_2;

import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.neonbee.data.DataRequest;
import io.neonbee.test.base.DataVerticleTestBase;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonArray;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxTestContext;

class BeehivesDataVerticleTest extends DataVerticleTestBase {

    @BeforeEach
    public void setUp(VertxTestContext testContext) {
        // The DataVerticleTestBase does not deploy any verticle (except some system verticles). This guarantees you a
        // clean system where you can exactly specify which verticles have been deployed and which not. Of course, if
        // you want to test your verticle, you have to deploy it. In this example, it is done in a setUp method because
        // both tests need this verticle.
        deployVerticle(new BeehivesDataVerticle()).onComplete(testContext.succeedingThenComplete());
    }

    @Test
    @Timeout(value = 2, timeUnit = TimeUnit.SECONDS)
    @DisplayName("should receive correct static content")
    void getBeehivesViaDataRequestAssertHelpers(VertxTestContext testContext) {
        JsonArray expected = new JsonArray().add(BEEHIVE_1).add(BEEHIVE_2);
        assertDataEquals(requestData(new DataRequest(BeehivesDataVerticle.QUALIFIED_NAME)), expected, testContext)
                .onComplete(testContext.succeedingThenComplete());
    }

    @Test
    @Timeout(value = 2, timeUnit = TimeUnit.SECONDS)
    @DisplayName("should be accessible via web endpoint")
    void getBeehivesViaWebEndpoint(VertxTestContext testContext) {
        createRequest(HttpMethod.GET, "/raw/" + BeehivesDataVerticle.QUALIFIED_NAME)
                .send(testContext.succeeding(resp -> {
                    testContext.verify(() -> {
                        assertThat(resp.statusCode()).isEqualTo(200);
                        assertThat(resp.bodyAsJsonArray()).containsExactly(BEEHIVE_1, BEEHIVE_2);
                    });
                    testContext.completeNow();
                }));
    }
}

```

### Request Data via RawEndpoint

Now check if you can retrieve the content from your first *DataVerticle*. Start NeonBee with `./gradlew run` and
call your *DataVerticle* through the *RawEndpoint*.

```bash
$ curl http://localhost:8080/raw/example/Beehives
[
  {
    "id":1,
    "queen":"Daenerys"
  },
  {
    "id":2,
    "queen":"Historia"
  }
]
```

## Part 2: Require data

One of the core functionalities of *NeonBee* is to require data from other *DataVerticles*. In this section
of the guide you will learn how to do this. Therefore, you will write a second *DataVerticle* called
`PopulationDataVerticle`, which requires data from the `BeehivesDataVerticle`. The purpose of the
`PopulationDataVerticle` is to calculate the population of a beehive just by analysing the queen name.

> **NOTE:** In this part, the source code does not contain any comments on places that were already explained in part 1.

```java
package org.example.neonbee;

import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;
import static java.util.stream.Collectors.toList;

import java.util.Collection;
import java.util.List;

import io.neonbee.NeonBeeDeployable;
import io.neonbee.data.DataContext;
import io.neonbee.data.DataMap;
import io.neonbee.data.DataQuery;
import io.neonbee.data.DataRequest;
import io.neonbee.data.DataVerticle;
import io.neonbee.logging.LoggingFacade;
import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

@NeonBeeDeployable(namespace = "example")
public class PopulationDataVerticle extends DataVerticle<JsonArray> {

    // This DataVerticle is not exposed through the RawEndpoint, because its name starts with an underscore.
    private static final String NAME = "_Population";

    public static final String QUALIFIED_NAME = createQualifiedName("example", NAME);

    // The LoggingFacade implements SLF4J Logger and offers the method "correlateWith", to correlate log messages
    // with the correlationId stored in the "DataContext".
    private static final LoggingFacade LOGGER = LoggingFacade.create();

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public Future<Collection<DataRequest>> requireData(DataQuery query, DataContext context) {
        // This is a DataRequest to retrieve beehives from the BeehivesDataVerticle. Now that a DataRequest is
        // returned the DataVerticle will request the specified data, stores them in a DataMap and only then does
        // call the "retrieveData" method.
        DataRequest beehivesRequest = new DataRequest(BeehivesDataVerticle.QUALIFIED_NAME);
        return succeededFuture(List.of(beehivesRequest));
    }

    @Override
    public Future<JsonArray> retrieveData(DataQuery query, DataMap require, DataContext context) {
        // Check if the required data was requested successfully.
        if (require.failed(BeehivesDataVerticle.QUALIFIED_NAME)) {
            Throwable cause = require.cause(BeehivesDataVerticle.QUALIFIED_NAME);
            // Log the error and correlate it
            LOGGER.correlateWith(context).error("Can't retrieve Beehives", cause);
            return failedFuture(cause);
        }

        JsonArray beehives = require.resultFor(BeehivesDataVerticle.QUALIFIED_NAME);
        List<JsonObject> populations =
                beehives.stream().map(JsonObject.class::cast).map(this::calculatePopulation).collect(toList());
        return succeededFuture(new JsonArray(populations));
    }

    @SuppressWarnings("checkstyle:magicnumber")
    private JsonObject calculatePopulation(JsonObject beehive) {
        String queenName = beehive.getString("queen");
        JsonObject population = new JsonObject();
        population.put("beehiveId", beehive.getInteger("id"));
        population.put("drones", (queenName.hashCode() * 1337) % 5_000);
        population.put("worker", (queenName.hashCode() * 1337) % 50_000);

        return population;
    }
}
```

### Test and mock DataVerticles

```java
package org.example.neonbee;

import static com.google.common.truth.Truth.assertThat;
import static org.example.neonbee.BeehivesDataVerticle.BEEHIVE_1;

import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.neonbee.data.DataRequest;
import io.neonbee.data.DataVerticle;
import io.neonbee.test.base.DataVerticleTestBase;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxTestContext;

class PopulationDataVerticleTest extends DataVerticleTestBase {

    @BeforeEach
    public void setUp(VertxTestContext testContext) {
        // The PopulationDataVerticle has a dependency on the BeehivesDataVerticle. In order to have full control
        // over the return value of the BeehivesDataVerticle we need to mock it.
      DataVerticle<JsonArray> dummyBeehives = createDummyDataVerticle(BeehivesDataVerticle.QUALIFIED_NAME)
              .withStaticResponse(new JsonArray().add(BEEHIVE_1));

        deployVerticle(new PopulationDataVerticle()).compose(v -> deployVerticle(dummyBeehives))
                .onComplete(testContext.succeedingThenComplete());
    }

    @Test
    @Timeout(value = 2, timeUnit = TimeUnit.SECONDS)
    @DisplayName("should receive correct populations")
    void getPopulationsViaDataRequestAssertHelpers(VertxTestContext testContext) {
        JsonObject expected = new JsonObject().put("beehiveId", 1).put("drones", 4_525).put("worker", 9_525);

        this.<JsonArray>assertData(requestData(new DataRequest(PopulationDataVerticle.QUALIFIED_NAME)), populations -> {
            assertThat(populations).containsExactly(expected);
            testContext.completeNow();
        }, testContext);
    }

    @Test
    @Timeout(value = 2, timeUnit = TimeUnit.SECONDS)
    @DisplayName("should not be accessible via web endpoint")
    void doNotGetPopulationsViaWebEndpoint(VertxTestContext testContext) {
        createRequest(HttpMethod.GET, "/raw/" + PopulationDataVerticle.QUALIFIED_NAME)
                .send(testContext.succeeding(resp -> {
                    testContext.verify(() -> assertThat(resp.statusCode()).isEqualTo(404));
                    testContext.completeNow();
                }));
    }
}
```

## Part 3: The EntityVerticle

In this section you will write your first *EntityVerticle* and expose its data through the structured OData endpoint.
But before you can start, you need to ...

1. ... add a models subproject to your gradle project.
2. ... define the OData model of your data in [Common Data Service (CDS)](https://cap.cloud.sap/docs/cds/).

and then you can write and test the *EntityVerticle*

> **NOTE:** In this part, the source code does not contain any comments on places that were already explained in
> the parts before.

### Add models subproject

To be able to compile CDS models, you need to execute the `initModels` task with `./gradlew initModels`. After that
you need to refresh your Gradle project.

### Define OData model

The `initModels` task has created a `models` directory in your project that contains a `ExampleService.cds`.
Rename it to `BeehiveService.cds` and replace its content with the following CDS description:

```cds
service BeehiveService {
   entity Beehives {
      key id : Integer;
      queen : String;
      drones : Integer;
      worker : Integer;
      population : Integer;
   }
}
```

A CDS model is a way of organizing and defining the data and relationships within a service. It specifies the
entities, their fields, and any relationships between them. In this case, the model consists of the
`BeehiveService` and the `Beehives` entity, and defines the structure and content of the data stored in the
service. The `Beehives` entity has a key field called `id`, which is an integer, and four other fields: `queen`,
`drones`, `worker` and `population`. These fields represent different aspects of a beehive, such as the type
of bees present or the size of the hive's population.

### Write your first EntityVerticle

The purpose of *EntityVerticles* is to process structured data. Now that you have defined a data structure in the CDS file,
you can start writing your first *EntityVerticle* called `BeehivesEntityVerticle`. This *EntityVerticle* will require
data from your two *DataVerticles* you have written before and merge their responses.

```java
package org.example.neonbee;

import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;
import static java.util.stream.Collectors.toList;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.apache.olingo.commons.api.data.Entity;
import org.apache.olingo.commons.api.data.Property;
import org.apache.olingo.commons.api.data.ValueType;
import org.apache.olingo.commons.api.edm.FullQualifiedName;

import com.google.common.annotations.VisibleForTesting;

import io.neonbee.NeonBeeDeployable;
import io.neonbee.data.DataContext;
import io.neonbee.data.DataMap;
import io.neonbee.data.DataQuery;
import io.neonbee.data.DataRequest;
import io.neonbee.entity.EntityVerticle;
import io.neonbee.entity.EntityWrapper;
import io.neonbee.logging.LoggingFacade;
import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

@NeonBeeDeployable(namespace = "example")
public class BeehivesEntityVerticle extends EntityVerticle {

    @VisibleForTesting
    static final FullQualifiedName ENTITY_NAME = new FullQualifiedName("BeehiveService", "Beehives");

    private static final LoggingFacade LOGGER = LoggingFacade.create();

    @Override
    public Future<Set<FullQualifiedName>> entityTypeNames() {
        // The entity verticle has to announce, which entity/ies it returns data for. It's the EntityVerticles version
        // of getName() in DataVerticle.
        return succeededFuture(Set.of(ENTITY_NAME));
    }

    @Override
    public Future<Collection<DataRequest>> requireData(DataQuery query, DataContext context) {
        return succeededFuture(List.of(new DataRequest(BeehivesDataVerticle.QUALIFIED_NAME),
                new DataRequest(PopulationDataVerticle.QUALIFIED_NAME)));
    }

    @Override
    public Future<EntityWrapper> retrieveData(DataQuery query, DataMap require, DataContext context) {
        if (require.failed()) {
            LOGGER.correlateWith(context).error("Can't retrieve required data to build beehives", require.cause());
            return failedFuture(require.cause());
        }

        List<JsonObject> beehives = require.<JsonArray>resultFor(BeehivesDataVerticle.QUALIFIED_NAME).stream()
                .map(JsonObject.class::cast).collect(toList());
        List<JsonObject> populations = require.<JsonArray>resultFor(PopulationDataVerticle.QUALIFIED_NAME).stream()
                .map(JsonObject.class::cast).collect(toList());

        // Create an EntityWrapper that contains the response
        return succeededFuture(new EntityWrapper(ENTITY_NAME,
                beehives.stream().map(beehive -> createBeehiveEntity(beehive, populations)).collect(toList())));
    }

    private static Entity createBeehiveEntity(JsonObject beehive, List<JsonObject> populations) {
        JsonObject population = populations.stream()
                .filter(p -> beehive.getInteger("id").equals(p.getInteger("beehiveId"))).findFirst().get();
        int drones = population.getInteger("drones");
        int worker = population.getInteger("worker");
        int populationCount = drones + worker + 1;

        Entity entity = new Entity();
        entity.addProperty(new Property(null, "id", ValueType.PRIMITIVE, beehive.getInteger("id")));
        entity.addProperty(new Property(null, "queen", ValueType.PRIMITIVE, beehive.getString("queen")));
        entity.addProperty(new Property(null, "drones", ValueType.PRIMITIVE, drones));
        entity.addProperty(new Property(null, "worker", ValueType.PRIMITIVE, worker));
        entity.addProperty(new Property(null, "population", ValueType.PRIMITIVE, populationCount));
        return entity;
    }
}
```

### Test EntityVerticle and mock DataVerticles

The following code uses the `ODataEndpointTestBase` and tests the *EntityVerticle* indirectly, because the
`ODataEndpoint` is calling related *EntityVerticle*. There is also an `EntityVerticleTestBase` in case you want to
test the *EntityVerticle* directly.

```java
package org.example.neonbee;

import static org.example.neonbee.BeehivesDataVerticle.BEEHIVE_1;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.neonbee.data.DataVerticle;
import io.neonbee.test.base.ODataEndpointTestBase;
import io.neonbee.test.base.ODataRequest;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.junit5.Checkpoint;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxTestContext;

// The ODataEndpoint test base provides some handy methods and initialization!
class BeehivesEntityVerticleTest extends ODataEndpointTestBase {

    // Declare model files that should be exposed via the test endpoint (the Gradle build tooling will take care
    // that the CSN models are actually compiled before the test code is executed)
    @Override
    @SuppressFBWarnings(value = "RCN_REDUNDANT_NULLCHECK_WOULD_HAVE_BEEN_A_NPE",
            justification = "Bug: https://github.com/spotbugs/spotbugs/issues/1694")
    public List<Path> provideEntityModels() {
        try (Stream<Path> csnFiles = Files.walk(Path.of("./models/dist")).filter(p -> p.toString().endsWith(".csn"))) {
            return csnFiles.collect(Collectors.toList());
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    @BeforeEach
    public void setUp(VertxTestContext testContext) {
        DataVerticle<JsonArray> dummyBeehives = createDummyDataVerticle(BeehivesDataVerticle.QUALIFIED_NAME)
                .withStaticResponse(new JsonArray().add(BEEHIVE_1));

        JsonObject population = new JsonObject().put("beehiveId", 1).put("drones", 4_525).put("worker", 9_525);
        DataVerticle<JsonArray> dummyPopulations = createDummyDataVerticle(PopulationDataVerticle.QUALIFIED_NAME)
                .withStaticResponse(new JsonArray().add(population));

        Checkpoint checkpoint = testContext.checkpoint(3);
        deployVerticle(dummyBeehives).onComplete(testContext.succeeding(result -> checkpoint.flag()));
        deployVerticle(dummyPopulations).onComplete(testContext.succeeding(result -> checkpoint.flag()));
        deployVerticle(new BeehivesEntityVerticle()).onComplete(testContext.succeeding(result -> checkpoint.flag()));
    }

    @Test
    @Timeout(value = 10, timeUnit = TimeUnit.SECONDS)
    @DisplayName("should receive correct birds via DataRequest")
    void getBirdsViaDataRequest(VertxTestContext testContext) {
        // Send a new OData request, the ODataEndpointTestBase helps us to send a valid OData request
        Future<HttpResponse<Buffer>> oDataResponse = requestOData(new ODataRequest(BeehivesEntityVerticle.ENTITY_NAME));

        JsonObject expected = BEEHIVE_1.copy().put("drones", 4_525).put("worker", 9_525).put("population", 14_051);

        // Validate OData response, we can use the assert methods of the test base here, this way we don't have
        // to parse any of the OData format and can simply deal with JSON data
        assertODataEntitySetContainsExactly(oDataResponse, List.of(expected), testContext)
                .onComplete(testContext.succeedingThenComplete());
    }
}
```

## Request OData via ODataEndpoint

Now check if you can retrieve the content from your first *EntityVerticle*. Start NeonBee with `./gradlew run` and
call your *EntityVerticle* through the *ODataEndpoint*.

```
$ curl http://localhost:8080/odata/BeehiveService/Beehives
{
  "@odata.context":"$metadata#Beehives/$entity",
  "@odata.metadataEtag":"\"b00cd9d5fd1af97ed5894007fecae641\"",
  "value":[
    {
      "id":1,
      "queen":"Daenerys",
      "drones":4525,
      "worker":9525,
      "population":14051
    },
    {
      "id":2,
      "queen":"Historia",
      "drones":2341,
      "worker":2341,
      "population":4683
    }
  ]
}
```

## For Your Convenience

NeonBee provides further simplifications when dealing with verticle development.

Especially in large-scale distributed systems, correlating log messages become crucial to reproduce what is actually
going on. Conveniently, NeonBee offers a simple `LoggingFacade` which masks the logging interface with:

```java
// alternatively you can use the masqueradeLogger method,
// to use the facade on an existing SF4J logger
LoggingFacade logger = LoggingFacade.create();

logger.correlateWith(context).info("Hello NeonBee");
```

The logger gets correlated with a correlated ID passed through the routing context. In the default implementation of
NeonBees logging facade, the correlation ID will be logged alongside the actual message as a so-called
[marker](https://www.slf4j.org/faq.html#marker_interface) and can easily be used to trace a certain log message,
even in distributed clusters. Note that the `correlateWith` method does not actually correlate the whole logging
facade, but only the next message logged. This means you have to invoke the `correlateWith` method once again when the
next message is logged.

Similar to Vert.x's shared instance, NeonBee provides its own shared instance holding some additional properties, such
as the NeonBee options and configuration objects, as well as general purpose local and cluster-wide shared map for you
to use. Each NeonBee instance has a one-to-one relation to a given Vert.x instance. To retrieve the NeonBee instance
from anywhere, just use the static `NeonBee.neonbee` method of the NeonBee main class:

```java
NeonBee neonbee = NeonBee.neonbee(vertx);

// get access to the NeonBee CLI options
NeonBeeOptions options = neonbee.getOptions();

// general purpose shared local / async (cluster-wide) maps
LocalMap<String, Object> sharedLocalMap = neonbee.getLocalMap();
AsyncMap<String, Object> sharedAsyncMap = neonbee.getAsyncMap();
```