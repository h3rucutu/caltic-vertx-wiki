package id.co.caltic.labs.wiki;

import com.github.rjeschke.txtmark.Processor;
import io.reactiverse.pgclient.*;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.templ.FreeMarkerTemplateEngine;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.function.Consumer;

public class MainVerticle extends AbstractVerticle {
  private static final Logger LOGGER = LoggerFactory.getLogger(MainVerticle.class);

  private static final String EMPTY_PAGE_MARKDOWN = "# A new page\n\nFeel-free to write in Markdown!\n";
  private static final String SQL_CREATE_PAGES_TABLE = "CREATE TABLE IF NOT EXISTS pages (" +
      "id serial PRIMARY KEY, " +
      "name VARCHAR(255) UNIQUE NOT NULL, " +
      "content TEXT)";
  private static final String SQL_GET_PAGE = "SELECT id, content FROM pages WHERE name = $1";
  private static final String SQL_CREATE_PAGE = "INSERT INTO pages (name, content) VALUES ($1, $2)";
  private static final String SQL_SAVE_PAGE = "UPDATE pages SET content = $1 WHERE id = $2";
  private static final String SQL_ALL_PAGES = "SELECT name FROM pages";
  private static final String SQL_DELETE_PAGE = "DELETE FROM pages WHERE id = $1";

  private PgPool client;
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

    String connUri = System.getenv("DATABASE_URL");
    String dbUser = System.getenv("DATABASE_USER");
    String dbPwd = System.getenv("DATABASE_PASSWORD");
    String dbPool = System.getenv("DATABASE_POOL");
    client = PgClient.pool(vertx, PgPoolOptions
        .fromUri((connUri == null || connUri.isEmpty()) ?
            String.format("postgresql://%s:%s@localhost:5432/caltic_wiki", dbUser, dbPwd) : connUri)
        .setMaxSize((dbPool == null || dbPool.isEmpty()) ? 10 : Integer.valueOf(dbPool)));

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
    LOGGER.info("indexHandler");
    client.getConnection(car -> {
      if (car.succeeded()) {
        PgConnection conn = car.result();
        conn.query(SQL_ALL_PAGES, res -> {
          conn.close();
          if (res.succeeded()) {
            List<String> pages = new ArrayList<>();
            res.result().forEach(new Consumer<Row>() {
              @Override
              public void accept(Row row) {
                pages.add(row.getString("name"));
              }
            });
            context.put("title", "Wiki Home");
            context.put("pages", pages);
            templateEngine.render(context, "templates", "/index.ftl", ar -> {
              if (ar.succeeded()) {
                context.response().putHeader("Content-Type", "text/html");
                context.response().end(ar.result());
              } else {
                context.fail(ar.cause());
              }
            });
          } else {
            context.fail(res.cause());
          }
        });
      } else {
        context.fail(car.cause());
      }
    });
  }

  private void pageRenderingHandler(RoutingContext context) {
    LOGGER.info("pageRenderingHandler");
    String page = context.request().getParam("page");
    LOGGER.info(String.format("page: %s", page));
    client.getConnection(car -> {
      if (car.succeeded()) {
        PgConnection conn = car.result();
        conn.preparedQuery(SQL_GET_PAGE, Tuple.of(page), fetch -> {
          conn.close();
          if (fetch.succeeded()) {
            JsonArray jsonRow = new JsonArray();
            LOGGER.info(String.format("row size: %s", fetch.result().size()));
            if (fetch.result().size() > 0) {
              fetch.result().forEach(new Consumer<Row>() {
                @Override
                public void accept(Row row) {
                  jsonRow.add(row.getInteger("id")).add(row.getString("content"));
                }
              });
            } else {
              jsonRow.add(-1).add(EMPTY_PAGE_MARKDOWN);
            }
            context.put("title", page);
            context.put("id", jsonRow.getInteger(0));
            context.put("newPage", jsonRow.getInteger(0) == -1 ? "yes" : "no");
            context.put("rawContent", jsonRow.getString(1));
            context.put("content", Processor.process(jsonRow.getString(1)));
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
            context.fail(fetch.cause());
          }
        });
      } else {
        context.fail(car.cause());
      }
    });
  }

  private void pageUpdateHandler(RoutingContext context) {
    LOGGER.info("pageUpdateHandler");
    String id = context.request().getParam("id");
    String title = context.request().getParam("title");
    String markdown = context.request().getParam("markdown");
    boolean newPage = "yes".equals(context.request().getParam("newPage"));

    client.getConnection(car -> {
      if (car.succeeded()) {
        PgConnection conn = car.result();
        String sql = newPage ? SQL_CREATE_PAGE : SQL_SAVE_PAGE;
        List<Tuple> tuples = new ArrayList<>();
        if (newPage) {
          tuples.add(Tuple.of(title, markdown));
        } else {
          tuples.add(Tuple.of(markdown, Integer.valueOf(id)));
        }
        conn.preparedQuery(sql, tuples.get(0), res -> {
          conn.close();
          if (res.succeeded()) {
            context.response().setStatusCode(303);
            context.response().putHeader("Location", "/wiki/" + title);
            context.response().end();
          } else {
            context.fail(res.cause());
          }
        });
      } else {
        context.fail(car.cause());
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
    client.getConnection(car -> {
      if (car.succeeded()) {
        PgConnection conn = car.result();
        conn.preparedQuery(SQL_DELETE_PAGE, Tuple.of(Integer.valueOf(id)), res -> {
          conn.close();
          if (res.succeeded()) {
            context.response().setStatusCode(303);
            context.response().putHeader("Location", "/");
            context.response().end();
          } else {
            context.fail(res.cause());
          }
        });
      } else {
        context.fail(car.cause());
      }
    });
  }

}