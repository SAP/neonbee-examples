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
