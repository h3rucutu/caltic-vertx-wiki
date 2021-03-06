package id.caltic.labs.wiki.http;

import com.github.rjeschke.txtmark.Processor;
import id.caltic.labs.wiki.database.WikiService;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.User;
import io.vertx.ext.auth.jdbc.JDBCAuth;
import io.vertx.ext.jdbc.JDBCClient;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.Session;
import io.vertx.ext.web.handler.*;
import io.vertx.ext.web.sstore.LocalSessionStore;
import io.vertx.ext.web.templ.FreeMarkerTemplateEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.Optional;

public class HttpServerVerticle extends AbstractVerticle {
  private static final Logger LOGGER = LoggerFactory.getLogger(HttpServerVerticle.class);

  private static final String WIKIDB_QUEUE = "wikidb.queue";
  private static final String EMPTY_PAGE_MARKDOWN = "# A new page\n\nFeel-free to write in Markdown!\n";

  private static final String AUTHENTICATE_QUERY = "SELECT password, password_salt FROM \"user\" WHERE username = ?";

  private final FreeMarkerTemplateEngine templateEngine = FreeMarkerTemplateEngine.create();
  private JDBCAuth auth;
  private WikiService wikiService;

  @Override
  public void start(Future<Void> startFuture) throws Exception {

    String dbUser = System.getenv("DATABASE_USER");
    String dbPwd = System.getenv("DATABASE_PASSWORD");

    String connUri = Optional.ofNullable(System.getenv("JDBC_DATABASE_URL"))
        .orElse("jdbc:postgresql://localhost:5432/caltic_wiki");
    Integer dbPoolAuth = Integer.valueOf(Optional.ofNullable(System.getenv("DATABASE_POOL_AUTH"))
        .orElse("3"));
    int port = Integer.valueOf(Optional.ofNullable(System.getenv("PORT"))
        .orElse("9000"));

    wikiService = WikiService.createProxy(vertx, WIKIDB_QUEUE);
    HttpServer server = vertx.createHttpServer();

    JDBCClient client = JDBCClient.createShared(vertx, new JsonObject()
        .put("url", connUri)
        .put("driver_class", "org.postgresql.Driver")
        .put("user", dbUser)
        .put("password", dbPwd)
        .put("max_pool_size", dbPoolAuth));

    auth = JDBCAuth.create(vertx, client)
        .setAuthenticationQuery(AUTHENTICATE_QUERY);
    Router router = Router.router(vertx);

    router.route().handler(CookieHandler.create());
    router.route().handler(BodyHandler.create());
    router.route().handler(SessionHandler.create(LocalSessionStore.create(vertx)));
    router.route().handler(UserSessionHandler.create(auth));

    AuthHandler authHandler = RedirectAuthHandler.create(auth, "/login");
    router.route("/").handler(authHandler);
    router.route("/wiki/*").handler(authHandler);
    router.route("/action/*").handler(authHandler);

    router.get("/").handler(this::indexHandler);
    router.get("/wiki/:page").handler(this::pageRenderingHandler);
    router.post("/action/save").handler(this::pageUpdateHandler);
    router.post("/action/create").handler(this::pageCreateHandler);
    router.post("/action/delete").handler(this::pageDeletionHandler);

    router.get("/login").handler(this::loginHandler);
    router.post("/login").handler(this::postLoginHander);
    router.get("/logout").handler(context -> {
      context.clearUser();
      context.response()
          .setStatusCode(302)
          .putHeader("Location", "/")
          .end();
    });

    server.requestHandler(router::accept)
        .listen(port, ar -> {
          if (ar.succeeded()) {
            LOGGER.info("HTTP server running on port " + port);
            startFuture.complete();
          } else {
            LOGGER.error("Could not start a HTTP server", ar.cause());
            startFuture.fail(ar.cause());
          }
        });
  }

  private void postLoginHander(RoutingContext context) {
    LOGGER.info("postLoginHander");
    String username = context.request().formAttributes().get("username");
    String password = context.request().formAttributes().get("password");
    String returnUrl = context.request().formAttributes().get("return_url");
    JsonObject authInfo = new JsonObject().put("username", username).put("password", password);
    Session session = context.session();

    auth.authenticate(authInfo, res -> {
      if (res.succeeded()) {
        User user = res.result();
        context.setUser(user);
        if (session != null) {
          session.regenerateId();
          session.remove(returnUrl);

          context.response().setStatusCode(303);
          context.response().putHeader("Location", returnUrl);
          context.response().end();
        }
      } else {
        LOGGER.info("Unauthorized: " + res.cause());
        context.fail(403);
      }
    });
  }

  private void loginHandler(RoutingContext context) {
    LOGGER.info("loginHandler");
    boolean notLogged = context.user() == null;
    context.put("title", notLogged ? "Login" : "Logged");
    context.put("notLogged", notLogged);
    if (!notLogged) {
      context.put("notif", "Hello you're already logged!");
      context.put("username", context.user().principal().getString("username"));
    }
    templateEngine.render(context, "templates", "/login.ftl", ar -> {
      if (ar.succeeded()) {
        context.response().putHeader("Content-Type", "text/html");
        context.response().end(ar.result());
      } else {
        context.fail(ar.cause());
      }
    });
  }

  private void indexHandler(RoutingContext context) {
    LOGGER.info("indexHandler");
    context.user().isAuthorized("create", res -> {
      boolean canCreatePage = res.succeeded() && res.result();

      wikiService.fetchAllPages(reply -> {
        if (reply.succeeded()) {
          context.put("title", "Wiki Home");
          context.put("pages", reply.result().getList());
          context.put("canCreatePage", canCreatePage);
          context.put("username", context.user().principal().getString("username"));
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
    });
  }

  private void pageRenderingHandler(RoutingContext context) {
    LOGGER.info("pageRenderingHandler");
    String page = context.request().getParam("page");
    LOGGER.info(String.format("page: %s", page));

    context.user().isAuthorized("delete", res -> {
      if (res.succeeded()) {
        wikiService.fetchPage(page, reply -> {
          if (reply.succeeded()) {
            JsonObject body = reply.result();
            boolean found = body.getBoolean("found");
            String rawContent = body.getString("rawContent", EMPTY_PAGE_MARKDOWN);

            context.put("title", page);
            context.put("id", body.getInteger("id", -1));
            context.put("newPage", found ? "no" : "yes");
            LOGGER.info(String.format("newPage: %s", !found));
            context.put("canDelete", res.result());
            LOGGER.info(String.format("canDelete: %s", res.result()));
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
      } else {
        context.response().setStatusCode(403).end();
      }
    });
  }

  private void pageUpdateHandler(RoutingContext context) {
    LOGGER.info("pageUpdateHandler");
    String title = context.request().getParam("title");
    LOGGER.info(String.format("newPage: %s", context.request().getParam("newPage")));

    Handler<AsyncResult<Void>> handler = reply -> {
      if (reply.succeeded()) {
        context.response().setStatusCode(303);
        context.response().putHeader("Location", "/wiki/" + title);
        context.response().end();
      } else {
        context.fail(reply.cause());
      }
    };

    String markdown = context.request().getParam("markdown");
    if ("yes".equals(context.request().getParam("newPage"))) {
      wikiService.createPage(title, markdown, handler);
    } else {
      wikiService.savePage(Integer.valueOf(context.request().getParam("id")), markdown, handler);
    }
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
    context.user().isAuthorized("delete", res -> {
      if (res.succeeded() && res.result()) {
        String id = context.request().getParam("id");
        LOGGER.info(String.format("id: %s", id));

        wikiService.deletePage(Integer.valueOf(id), reply -> {
          if (reply.succeeded()) {
            context.response().setStatusCode(303);
            context.response().putHeader("Location", "/");
            context.response().end();
          } else {
            context.fail(reply.cause());
          }
        });
      } else {
        context.response().setStatusCode(403).end();
      }
    });
  }

}
