<#include "header.ftl">

<div class="row">
  <div class="col-md-12 mt-1">
    <#if context.notLogged>
      <form action="/login" method="POST">
        <div class="form-group">
          <input type="text" name="username" placeholder="login">
          <input type="password" name="password" placeholder="password">
          <input type="hidden" name="return_url" value="/">
          <button type="submit" class="btn btn-primary">Login</button>
        </div>
      </form>
    <#else>
      <h4 class="display-4">${context.notif}</h4>
      <div class="float-xs-right">
        <a class="btn btn-outline-primary" href="/" role="button" aria-pressed="true">Home</a>
        <a class="btn btn-outline-danger" href="/logout" role="button"
           aria-pressed="true">Logout (${context.username})</a>
      </div>
    </#if>
  </div>
</div>

<#include "footer.ftl">