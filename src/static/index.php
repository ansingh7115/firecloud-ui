<html>
  <head>
    <meta charset="utf-8">
    <meta http-equiv="Content-type" content="text/html;charset=utf-8">
    <meta name="viewport" content="initial-scale=1,width=device-width">
    <meta name="google-signin-scope" content="profile email">
    <meta name="google-signin-client_id" content="806222273987-2ntvt4hnfsikqmhhc18l64vheh4cj34q.apps.googleusercontent.com">
    <title>FireCloud | Broad Institute</title>
    <link href='http://fonts.googleapis.com/css?family=Roboto:400,500,700' rel='stylesheet' type='text/css'>
    <style>
      body {
        margin: 0;
        font-family: 'Roboto', sans-serif;
        -webkit-font-smoothing: antialiased;
        -moz-font-smoothing: antialiased;
        -o-font-smoothing: antialiased;
      }
    </style>
    <script src="https://apis.google.com/js/platform.js" async defer></script>
    <script src="build/goog/base.js"></script>
  </head>
  <body>
    <div id="contentRoot"></div>
    <script src="compiled.js"></script>
    <script>goog.require('org.broadinstitute.firecloud_ui.main');</script>
    <script>
      var app = org.broadinstitute.firecloud_ui.main.render(document.getElementById('contentRoot'));
      function onSignIn(googleUser) {
        app.handleSignIn(googleUser);
      }
    </script>
  </body>
</html>
