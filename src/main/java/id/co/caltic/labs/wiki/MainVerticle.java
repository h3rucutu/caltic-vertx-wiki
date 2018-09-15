package id.co.caltic.labs.wiki;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public class MainVerticle extends AbstractVerticle {
  private static final Logger LOGGER = LoggerFactory.getLogger(MainVerticle.class);

  public void start(Future<Void> startFuture) throws Exception {
    Future<String> dbVerticleDeployment = Future.future();

    String dbinstance = System.getenv("VERTICLE_DATABASE_INSTANCE");
    String httpInstance = System.getenv("VERTICLE_HTTP_INSTANCE");

    DeploymentOptions dbOptions = new DeploymentOptions()
        .setInstances((dbinstance == null || dbinstance.isEmpty()) ? 1 : Integer.valueOf(dbinstance));
    DeploymentOptions httpOptions = new DeploymentOptions()
        .setInstances((httpInstance == null || httpInstance.isEmpty()) ? 1 : Integer.valueOf(httpInstance));

    vertx.deployVerticle(
        "id.co.caltic.labs.wiki.DatabaseVerticle",
        dbOptions, dbVerticleDeployment.completer());

    dbVerticleDeployment.compose(id -> {
      Future<String> httpVerticleDeployment = Future.future();
      vertx.deployVerticle(
          "id.co.caltic.labs.wiki.HttpServerVerticle",
          httpOptions, httpVerticleDeployment.completer());

      return httpVerticleDeployment;
    }).setHandler(ar -> {
      if (ar.succeeded()) {
        startFuture.complete();
      } else {
        startFuture.fail(ar.cause());
      }
    });
  }

}