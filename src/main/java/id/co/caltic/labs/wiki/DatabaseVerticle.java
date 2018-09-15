package id.co.caltic.labs.wiki;

import io.reactiverse.pgclient.*;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class DatabaseVerticle extends AbstractVerticle {
  private static final Logger LOGGER = LoggerFactory.getLogger(DatabaseVerticle.class);

  private static final String WIKIDB_QUEUE = "wikidb.queue";
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

  @Override
  public void start(Future<Void> startFuture) throws Exception {
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
        startFuture.fail(ar.cause());
      } else {
        PgConnection conn = ar.result();
        conn.query(SQL_CREATE_PAGES_TABLE, create -> {
          conn.close();
          if (create.failed()) {
            LOGGER.error("Database preparation error", create.cause());
            startFuture.fail(create.cause());
          } else {
            vertx.eventBus().consumer(WIKIDB_QUEUE, this::onMessage);
            startFuture.complete();
          }
        });
      }
    });
  }

  private void onMessage(Message<JsonObject> message) {
    if (!message.headers().contains("action")) {
      LOGGER.error("No action header specified for message with headers {} and body {}",
          message.headers(), message.body().encodePrettily());
      message.fail(ErrorCodes.NO_ACTION_SPECIFIED.ordinal(), "No action header specified");
      return;
    }
    String action = message.headers().get("action");

    switch (action) {
      case "all-pages":
        fetchAllPages(message);
        break;
      case "get-page":
        fetchPage(message);
        break;
      case "create-page":
        createPage(message);
        break;
      case "save-page":
        savePage(message);
        break;
      case "delete-page":
        deletePage(message);
        break;
      default:
        message.fail(ErrorCodes.BAD_ACTION.ordinal(), "Bad action: " + action);
    }
  }

  private void reportQueryError(Message<JsonObject> message, Throwable cause) {
    LOGGER.error("Database query error", cause);
    message.fail(ErrorCodes.DB_ERROR.ordinal(), cause.getMessage());
  }

  private void fetchAllPages(Message<JsonObject> message) {
    LOGGER.info("fetchAllPages");
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
            message.reply(new JsonObject().put("pages", new JsonArray(pages)));
          } else {
            reportQueryError(message, res.cause());
          }
        });
      } else {
        reportQueryError(message, car.cause());
      }
    });
  }

  private void fetchPage(Message<JsonObject> message) {
    LOGGER.info("fetchPage");
    client.getConnection(car -> {
      if (car.succeeded()) {
        PgConnection conn = car.result();
        conn.preparedQuery(SQL_GET_PAGE, Tuple.of(message.body().getString("page")), fetch -> {
          conn.close();
          if (fetch.succeeded()) {
            JsonObject response = new JsonObject();
            JsonArray jsonRow = new JsonArray();
            if (fetch.result().size() > 0) {
              fetch.result().forEach(new Consumer<Row>() {
                @Override
                public void accept(Row row) {
                  jsonRow.add(row.getInteger("id")).add(row.getString("content"));
                }
              });
              response.put("found", true);
              response.put("id", jsonRow.getInteger(0));
              response.put("rawContent", jsonRow.getString(1));
            } else {
              response.put("found", false);
            }
            message.reply(response);
          } else {
            reportQueryError(message, fetch.cause());
          }
        });
      } else {
        reportQueryError(message, car.cause());
      }
    });
  }

  private void createPage(Message<JsonObject> message) {
    LOGGER.info("createPage");
    Tuple params = Tuple.of(message.body().getString("title"),
        message.body().getString("markdown"));

    paramsHandler(message, SQL_CREATE_PAGE, params);
  }

  private void savePage(Message<JsonObject> message) {
    LOGGER.info("savePage");
    Tuple params = Tuple.of(message.body().getString("markdown"),
        Integer.valueOf(message.body().getString("id")));

    paramsHandler(message, SQL_SAVE_PAGE, params);
  }

  private void deletePage(Message<JsonObject> message) {
    LOGGER.info("deletePage");
    Tuple params = Tuple.of(Integer.valueOf(message.body().getString("id")));

    paramsHandler(message, SQL_DELETE_PAGE, params);
  }

  private void paramsHandler(Message<JsonObject> message, String sql, Tuple params) {
    LOGGER.info("paramsHandler");
    client.getConnection(car -> {
      if (car.succeeded()) {
        PgConnection conn = car.result();
        conn.preparedQuery(sql, params, res -> {
          conn.close();
          if (res.succeeded()) {
            message.reply("ok");
          } else {
            reportQueryError(message, res.cause());
          }
        });
      } else {
        reportQueryError(message, car.cause());
      }
    });
  }

}
