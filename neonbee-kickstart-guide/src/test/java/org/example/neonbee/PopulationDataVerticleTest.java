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
