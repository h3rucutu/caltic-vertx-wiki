package id.caltic.labs.wiki.database.impl;

import id.caltic.labs.wiki.database.WikiService;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.jdbc.JDBCClient;
import io.vertx.ext.sql.ResultSet;
import io.vertx.ext.sql.SQLConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.stream.Collectors;

public class WikiServiceImpl implements WikiService {
  private static final Logger LOGGER = LoggerFactory.getLogger(WikiServiceImpl.class);

  private static final String SQL_CREATE_PAGES_TABLE = "CREATE TABLE IF NOT EXISTS pages (" +
      "id serial PRIMARY KEY, " +
      "name VARCHAR(255) UNIQUE NOT NULL, " +
      "content TEXT)";
  private static final String SQL_GET_PAGE = "SELECT id, content FROM pages WHERE name = ?";
  private static final String SQL_CREATE_PAGE = "INSERT INTO pages (name, content) VALUES (?, ?)";
  private static final String SQL_SAVE_PAGE = "UPDATE pages SET content = ? WHERE id = ?";
  private static final String SQL_ALL_PAGES = "SELECT name FROM pages";
  private static final String SQL_DELETE_PAGE = "DELETE FROM pages WHERE id = ?";

  private final JDBCClient jdbcClient;

  public WikiServiceImpl(JDBCClient jdbcClient, Handler<AsyncResult<WikiService>> resultHandler) {
    this.jdbcClient = jdbcClient;

    jdbcClient.getConnection(ar -> {
      if (ar.failed()) {
        LOGGER.error("Couldn't open database connection", ar.cause());
        resultHandler.handle(Future.failedFuture(ar.cause()));
      } else {
        SQLConnection conn = ar.result();
        conn.execute(SQL_CREATE_PAGES_TABLE, create -> {
          conn.close();
          if (create.failed()) {
            LOGGER.error("Database initialisation error", create.cause());
            resultHandler.handle(Future.failedFuture(create.cause()));
          } else {
            resultHandler.handle(Future.succeededFuture(this));
          }
        });
      }
    });
  }

  @Override
  public WikiService fetchAllPages(Handler<AsyncResult<JsonArray>> resultHandler) {
    jdbcClient.query(SQL_ALL_PAGES, res -> {
      if (res.succeeded()) {
        JsonArray pages = new JsonArray(res.result()
            .getResults().parallelStream()
            .map(json -> json.getString(0)).sorted()
            .collect(Collectors.toList()));

        resultHandler.handle(Future.succeededFuture(pages));
      } else {
        LOGGER.error("Database query error", res.cause());
        resultHandler.handle(Future.failedFuture(res.cause()));
      }
    });
    return this;
  }

  @Override
  public WikiService fetchPage(String name, Handler<AsyncResult<JsonObject>> resultHandler) {
    jdbcClient.queryWithParams(SQL_GET_PAGE, new JsonArray().add(name), fetch -> {
      if (fetch.succeeded()) {
        JsonObject response = new JsonObject();
        ResultSet resultSet = fetch.result();
        if (resultSet.getNumRows() == 0) {
          response.put("found", false);
        } else {
          response.put("found", true);
          JsonArray row = resultSet.getResults().get(0);
          response.put("id", row.getInteger(0));
          response.put("rawContent", row.getString(1));
        }
        resultHandler.handle(Future.succeededFuture(response));
      } else {
        LOGGER.error("Database query error", fetch.cause());
        resultHandler.handle(Future.failedFuture(fetch.cause()));
      }
    });
    return this;
  }

  @Override
  public WikiService createPage(String title, String markdown, Handler<AsyncResult<Void>> resultHandler) {
    JsonArray params = new JsonArray().add(title).add(markdown);
    jdbcClient.updateWithParams(SQL_CREATE_PAGE, params, res -> {
      if (res.succeeded()) {
        resultHandler.handle(Future.succeededFuture());
      } else {
        LOGGER.error("Database query error", res.cause());
        resultHandler.handle(Future.failedFuture(res.cause()));
      }
    });
    return this;
  }

  @Override
  public WikiService savePage(int id, String markdown, Handler<AsyncResult<Void>> resultHandler) {
    JsonArray params = new JsonArray().add(markdown).add(id);
    jdbcClient.updateWithParams(SQL_SAVE_PAGE, params, res -> {
      if (res.succeeded()) {
        resultHandler.handle(Future.succeededFuture());
      } else {
        LOGGER.error("Database query error", res.cause());
        resultHandler.handle(Future.failedFuture(res.cause()));
      }
    });
    return this;
  }

  @Override
  public WikiService deletePage(int id, Handler<AsyncResult<Void>> resultHandler) {
    JsonArray params = new JsonArray().add(id);
    jdbcClient.updateWithParams(SQL_DELETE_PAGE, params, res -> {
      if (res.succeeded()) {
        resultHandler.handle(Future.succeededFuture());
      } else {
        LOGGER.error("Database query error", res.cause());
        resultHandler.handle(Future.failedFuture(res.cause()));
      }
    });
    return this;
  }
}
