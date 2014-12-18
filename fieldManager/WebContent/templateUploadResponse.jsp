<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<!DOCTYPE html>

<html>
<head>
	<meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
	<meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no">
	
	<link rel="shortcut icon" href="favicon.ico" />
	<link rel="stylesheet" href="/css/normalize.css" />
	<link rel="stylesheet" href="/css/bootstrap.min.css" /> 
	<link rel="stylesheet" href="/css/smap-bs.css" type="text/css">

   	<!--[if lt IE 9]>
		<script src="//html5shim.googlecode.com/svn/trunk/html5.js"></script>
	<![endif]-->
		
	<script id="tpl" type="text/template">
	<div class="container">
		<div class="row">
			<div class="col-lg-8 col-lg-offset-2">
				<div class="section-heading">
					<div class="alert alert-danger" role="alert">
						<span class="glyphicon glyphicon-exclamation-sign" aria-hidden="true"></span>
						<span class="sr-only">Error:</span>
						{{mesg}}
					<div>
					<ol>
  						{{#hints}}
  							<li>{{.}}</li>
  						{{/hints}}
					</ol>
				</div>
			</div>
		</div>
	</div>
	</script>
	<script>
	var msg = ${message};
	var administrator = "${administrator}";
	</script>
	
	<script type="text/javascript"> if (!window.console) console = {log: function() {}}; </script>
	<script src="/js/libs/modernizr.js"></script>
	<script data-main="js/templateupload_main" src="/js/libs/require.js"></script>

	<title>Form Error</title>
	
</head>
<body>
	<header class="navbar navbar-default navbar-static-top" role="banner">
	  <div class="container">
	    <div class="navbar-header">
	      <a href="#" class="navbar-brand">
	      	<img src="/images/logo.png">
	     	 <span class="lang" data-lang="e_u_err">Form Error</span>
	      </a>
	    </div>
		
	  </div>
	</header>
	
	<div id="msg_locn"></div><br/>
	<div class="container">
		<div class="row">
			<div class="col-lg-8 col-lg-offset-2">

				<button class="btn btn-primary lang" data-lang="c_back" type="button" onclick="history.back()">Back</button>			
				<a id="email_button" href="#" class="btn btn-primary" type="button" onclick="history.back()">
					<span class="glyphicon glyphicon-envelope" aria-hidden="true"></span>
					<span class="lang" data-lang="m_help">Get Help</span>
				</a>
			</div>
		</div>
	</div>
</body>
</html>