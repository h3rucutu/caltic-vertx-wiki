package id.caltic.labs.wiki.database;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.jdbc.JDBCClient;
import io.vertx.serviceproxy.ServiceBinder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

public class WikiDatabaseVerticle extends AbstractVerticle {
  private static final Logger LOGGER = LoggerFactory.getLogger(WikiDatabaseVerticle.class);

  private static final String WIKIDB_QUEUE = "wikidb.queue";

  public void start(Future<Void> startFuture) throws Exception {

    String dbUser = System.getenv("DATABASE_USER");
    String dbPwd = System.getenv("DATABASE_PASSWORD");

    String connUri = Optional.ofNullable(System.getenv("JDBC_DATABASE_URL"))
        .orElse("jdbc:postgresql://localhost:5432/caltic_wiki");
    Integer dbPool = Integer.valueOf(Optional.ofNullable(System.getenv("DATABASE_POOL"))
        .orElse("10"));

    JDBCClient jdbcClient = JDBCClient.createShared(vertx, new JsonObject()
        .put("url", connUri)
        .put("driver_class", "org.postgresql.Driver")
        .put("user", dbUser)
        .put("password", dbPwd)
        .put("max_pool_size", dbPool));

    WikiService.create(jdbcClient, ready -> {
      if (ready.succeeded()) {
        ServiceBinder binder = new ServiceBinder(vertx);
        binder.setAddress(WIKIDB_QUEUE).register(WikiService.class, ready.result());
        startFuture.complete();
      } else {
        startFuture.fail(ready.cause());
      }
    });
  }
}
