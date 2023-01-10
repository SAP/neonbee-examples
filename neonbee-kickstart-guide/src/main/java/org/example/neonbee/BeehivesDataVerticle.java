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
