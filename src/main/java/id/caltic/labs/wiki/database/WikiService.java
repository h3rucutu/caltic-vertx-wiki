package id.caltic.labs.wiki.database;

import id.caltic.labs.wiki.database.impl.WikiServiceImpl;
import io.vertx.codegen.annotations.Fluent;
import io.vertx.codegen.annotations.ProxyGen;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.jdbc.JDBCClient;

import java.util.List;

@ProxyGen
public interface WikiService {

  static WikiService create(JDBCClient jdbcClient, Handler<AsyncResult<WikiService>> resultHandler) {
    return new WikiServiceImpl(jdbcClient, resultHandler);
  }

  static WikiService createProxy(Vertx vertx, String address) {
    return new WikiServiceVertxEBProxy(vertx, address);
  }

  @Fluent
  WikiService fetchAllPages(Handler<AsyncResult<JsonArray>> resultHandler);

  @Fluent
  WikiService fetchAllDataPages(Handler<AsyncResult<List<JsonObject>>> resultHandler);

  @Fluent
  WikiService fetchPage(String name, Handler<AsyncResult<JsonObject>> resultHandler);

  @Fluent
  WikiService createPage(String title, String markdown, Handler<AsyncResult<Void>> resultHandler);

  @Fluent
  WikiService savePage(int id, String markdown, Handler<AsyncResult<Void>> resultHandler);

  @Fluent
  WikiService deletePage(int id, Handler<AsyncResult<Void>> resultHandler);

}
