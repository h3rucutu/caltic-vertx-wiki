package id.caltic.labs.wiki;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MainVerticle extends AbstractVerticle {
  private static final Logger LOGGER = LoggerFactory.getLogger(MainVerticle.class);

  public void start(Future<Void> startFuture) throws Exception {
    Future<String> dbVerticleDeployment = Future.future();

    String httpInstance = System.getenv("VERTICLE_HTTP_INSTANCE");

    DeploymentOptions httpOptions = new DeploymentOptions()
        .setInstances((httpInstance == null || httpInstance.isEmpty()) ? 1 : Integer.valueOf(httpInstance));

    vertx.deployVerticle(
        new DatabaseVerticle(), dbVerticleDeployment.completer());

    dbVerticleDeployment.compose(id -> {
      Future<String> httpVerticleDeployment = Future.future();
      vertx.deployVerticle(
          "HttpServerVerticle",
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