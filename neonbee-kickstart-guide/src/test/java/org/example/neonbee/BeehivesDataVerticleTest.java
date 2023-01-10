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
