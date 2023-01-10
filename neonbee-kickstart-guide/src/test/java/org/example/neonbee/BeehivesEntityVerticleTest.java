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
