package id.co.caltic.labs.wiki;

import io.vertx.core.AbstractVerticle;

public class MainVerticle extends AbstractVerticle {

  @Override
  public void start() {
  	String port = System.getenv("PORT");
    vertx.createHttpServer()
        .requestHandler(req -> req.response().end("Hello Vert.x!"))
        .listen((port == null || port.isEmpty()) ? 9000 : Integer.valueOf(port));
  }

}
