package id.co.caltic.labs.wiki;

import io.reactiverse.pgclient.PgClient;
import io.reactiverse.pgclient.PgConnection;
import io.reactiverse.pgclient.PgPool;
import io.reactiverse.pgclient.PgPoolOptions;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.http.HttpServer;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.templ.FreeMarkerTemplateEngine;

public class MainVerticle extends AbstractVerticle {
  private static final Logger LOGGER = LoggerFactory.getLogger(MainVerticle.class);

  private static final String SQL_CREATE_PAGES_TABLE = "CREATE TABLE IF NOT EXISTS pages (" +
      "id serial PRIMARY KEY, " +
      "name VARCHAR(255) UNIQUE NOT NULL, " +
      "content TEXT)";
  private static final String SQL_GET_PAGE = "SELECT id, content FROM pages WHERE name = ?";
  private static final String SQL_CREATE_PAGE = "INSERT INTO pages (name, content) VALUES (?, ?)";
  private static final String SQL_SAVE_PAGE = "UPDATE pages SET content = ? WHERE id = ?";
  private static final String SQL_ALL_PAGES = "SELECT name FROM pages";
  private static final String SQL_DELETE_PAGE = "DELETE FROM pages WHERE id = ?";

  private final FreeMarkerTemplateEngine templateEngine = FreeMarkerTemplateEngine.create();

  @Override
  public void start(Future<Void> startFuture) throws Exception {
    Future<Void> steps = prepareDatabase().compose(v -> startHttpServer());
    steps.setHandler(ar -> {
      if (ar.succeeded()) {
        startFuture.complete();
      } else {
        startFuture.fail(ar.cause());
      }
    });
  }

  private Future<Void> prepareDatabase() {
    Future<Void> future = Future.future();

    String connUri = System.getenv("JDBC_DATABASE_URL");
    String dbUser = System.getenv("DATABASE_USER");
    String dbPwd = System.getenv("DATABASE_PASSWORD");
    PgPool client = PgClient.pool(vertx, PgPoolOptions
        .fromUri((connUri == null || connUri.isEmpty()) ?
            String.format("postgresql://%s:%s@localhost:5432/caltic_wiki", dbUser, dbPwd) : connUri)
        .setMaxSize(10));

    client.getConnection(ar -> {
      if (ar.failed()) {
        LOGGER.error("Could not open a database connection", ar.cause());
        future.fail(ar.cause());
      } else {
        PgConnection conn = ar.result();
        conn.query(SQL_CREATE_PAGES_TABLE, create -> {
          conn.close();
          if (create.failed()) {
            LOGGER.error("Database preparation error", create.cause());
            future.fail(create.cause());
          } else {
            future.complete();
          }
        });
      }
    });
    return future;
  }

  private Future<Void> startHttpServer() {
    Future<Void> future = Future.future();

    String port = System.getenv("PORT");
    HttpServer server = vertx.createHttpServer();

    Router router = Router.router(vertx);
    router.get("/").handler(this::indexHandler);
    router.get("/wiki/:page").handler(this::pageRenderingHandler);
    router.post().handler(BodyHandler.create());
    router.post("/save").handler(this::pageUpdateHandler);
    router.post("/create").handler(this::pageCreateHandler);
    router.post("/delete").handler(this::pageDeletionHandler);

    server.requestHandler(router::accept)
        .listen((port == null || port.isEmpty()) ? 9000 : Integer.valueOf(port), ar -> {
          if (ar.succeeded()) {
            LOGGER.info("HTTP server running on port 9000");
            future.complete();
          } else {
            LOGGER.error("Could not start a HTTP server", ar.cause());
            future.fail(ar.cause());
          }
        });
    return future;
  }

  private void indexHandler(RoutingContext context) {
    // TODO: Add implementation logic here!
  }

  private void pageRenderingHandler(RoutingContext context) {
    // TODO: Add implementation logic here!
  }

  private void pageUpdateHandler(RoutingContext context) {
    // TODO: Add implementation logic here!
  }

  private void pageCreateHandler(RoutingContext context) {
    // TODO: Add implementation logic here!
  }

  private void pageDeletionHandler(RoutingContext context) {
    // TODO: Add implementation logic here!
  }

}
