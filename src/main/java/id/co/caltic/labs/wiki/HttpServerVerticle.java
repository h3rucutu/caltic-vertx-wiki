package id.co.caltic.labs.wiki;

import com.github.rjeschke.txtmark.Processor;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.templ.FreeMarkerTemplateEngine;

import java.util.Date;

public class HttpServerVerticle extends AbstractVerticle {
  private static final Logger LOGGER = LoggerFactory.getLogger(HttpServerVerticle.class);

  private static final String WIKIDB_QUEUE = "wikidb.queue";
  private static final String EMPTY_PAGE_MARKDOWN = "# A new page\n\nFeel-free to write in Markdown!\n";

  private final FreeMarkerTemplateEngine templateEngine = FreeMarkerTemplateEngine.create();

  @Override
  public void start(Future<Void> startFuture) throws Exception {
    String port = System.getenv("PORT");
    HttpServer server = vertx.createHttpServer();

    Router router = Router.router(vertx);
    router.get("/").handler(this::indexHandler);
    router.get("/wiki/:page").handler(this::pageRenderingHandler);
    router.post().handler(BodyHandler.create());
    router.post("/save").handler(this::pageUpdateHandler);
    router.post("/create").handler(this::pageCreateHandler);
    router.post("/delete").handler(this::pageDeletionHandler);

    int portNumber = (port == null || port.isEmpty() ? 9000 : Integer.valueOf(port));
    server.requestHandler(router::accept)
        .listen(portNumber, ar -> {
          if (ar.succeeded()) {
            LOGGER.info("HTTP server running on port " + portNumber);
            startFuture.complete();
          } else {
            LOGGER.error("Could not start a HTTP server", ar.cause());
            startFuture.fail(ar.cause());
          }
        });
  }

  private void indexHandler(RoutingContext context) {
    LOGGER.info("indexHandler");
    DeliveryOptions options = new DeliveryOptions().addHeader("action", "all-pages");

    vertx.eventBus().send(WIKIDB_QUEUE, new JsonObject(), options, reply -> {
      if (reply.succeeded()) {
        JsonObject body = (JsonObject) reply.result().body();

        context.put("title", "Wiki Home");
        context.put("pages", body.getJsonArray("pages").getList());
        templateEngine.render(context, "templates", "/index.ftl", ar -> {
          if (ar.succeeded()) {
            context.response().putHeader("Content-Type", "text/html");
            context.response().end(ar.result());
          } else {
            context.fail(ar.cause());
          }
        });
      } else {
        context.fail(reply.cause());
      }
    });
  }

  private void pageRenderingHandler(RoutingContext context) {
    LOGGER.info("pageRenderingHandler");
    String page = context.request().getParam("page");
    LOGGER.info(String.format("page: %s", page));
    JsonObject request = new JsonObject().put("page", page);
    DeliveryOptions options = new DeliveryOptions().addHeader("action", "get-page");

    vertx.eventBus().send(WIKIDB_QUEUE, request, options, reply -> {
      if (reply.succeeded()) {
        JsonObject body = (JsonObject) reply.result().body();
        boolean found = body.getBoolean("found");
        String rawContent = body.getString("rawContent", EMPTY_PAGE_MARKDOWN);

        context.put("title", page);
        context.put("id", body.getInteger("id", -1));
        context.put("newPage", !found ? "yes" : "no");
        LOGGER.info(String.format("newPage: %s", found));
        context.put("rawContent", rawContent);
        context.put("content", Processor.process(rawContent));
        context.put("timestamp", new Date().toString());
        templateEngine.render(context, "templates", "/page.ftl", ar -> {
          if (ar.succeeded()) {
            context.response().putHeader("Content-Type", "text/html");
            context.response().end(ar.result());
          } else {
            context.fail(ar.cause());
          }
        });
      } else {
        context.fail(reply.cause());
      }
    });
  }

  private void pageUpdateHandler(RoutingContext context) {
    LOGGER.info("pageUpdateHandler");
    String title = context.request().getParam("title");
    LOGGER.info(String.format("newPage: %s", context.request().getParam("newPage")));
    JsonObject request = new JsonObject()
        .put("id", context.request().getParam("id"))
        .put("title", title)
        .put("markdown", context.request().getParam("markdown"));

    DeliveryOptions options = new DeliveryOptions()
        .addHeader("action", "yes".equals(context.request().getParam("newPage")) ? "create-page" : "save-page");
    vertx.eventBus().send(WIKIDB_QUEUE, request, options, reply -> {
      if (reply.succeeded()) {
        context.response().setStatusCode(303);
        context.response().putHeader("Location", "/wiki/" + title);
        context.response().end();
      } else {
        context.fail(reply.cause());
      }
    });
  }

  private void pageCreateHandler(RoutingContext context) {
    LOGGER.info("pageCreateHandler");
    String pageName = context.request().getParam("name");
    String location = "/wiki/" + pageName;
    if (pageName == null || pageName.isEmpty()) {
      location = "/";
    }
    LOGGER.info(String.format("page: %s", pageName));
    LOGGER.info(String.format("location: %s", location));
    context.response().setStatusCode(303);
    context.response().putHeader("Location", location);
    context.response().end();
  }

  private void pageDeletionHandler(RoutingContext context) {
    LOGGER.info("pageDeletionHandler");
    String id = context.request().getParam("id");
    LOGGER.info(String.format("id: %s", id));
    JsonObject request = new JsonObject().put("id", id);
    DeliveryOptions options = new DeliveryOptions().addHeader("action", "delete-page");

    vertx.eventBus().send(WIKIDB_QUEUE, request, options, reply -> {
      if (reply.succeeded()) {
        context.response().setStatusCode(303);
        context.response().putHeader("Location", "/");
        context.response().end();
      } else {
        context.fail(reply.cause());
      }
    });
  }

}
