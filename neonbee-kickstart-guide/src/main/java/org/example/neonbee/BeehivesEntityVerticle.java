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

        // POI 4: Create an EntityWrapper that contains the response
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
