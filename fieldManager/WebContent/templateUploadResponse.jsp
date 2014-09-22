<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<!DOCTYPE html>

<html>
<head>
	<meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
	<meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no">
	<link rel=StyleSheet href="/css/smap.css" type="text/css">
	<link type="text/css" media="all" href="/css/Aristo/Aristo.css" rel="stylesheet" />
	
	<!--  
	<script src="/js/libs/jquery-1.8.3.min.js"></script>
	<script type="text/javascript" src="/js/libs/jquery-ui-1.10.3.custom.min.js"></script>
	<script src="/js/libs/mustache.js"></script>
	-->
		
	<script id="tpl" type="text/template">
	<h1>Error</h1>
	<div>
		<p>{{mesg}}</p>
	</div>
	<div>
		<ol>
  			{{#hints}}
  				<li>{{.}}</li>
  			{{/hints}}
		</ol>
	</div>
	<h1>What was submitted</h1>
	<table>
		<body>
			<tr><td>Host</td><td>{{host}}</td></tr>
			<tr><td>Project</td><td>{{project}}</td></tr>
			<tr><td>Survey</td><td>{{survey}}</td></tr>
			<tr><td>File</td><td>{{fileName}}</td></tr>
		</body>
	</table>
	</script>
	<script>
	var msg = ${message};
	var administrator = "${administrator}";
	</script>
	
	<script data-main="js/templateupload_main" src="/js/libs/require.js"></script>
	<script type="text/javascript"> if (!window.console) console = {log: function() {}}; </script>
	<title>Template Upload Results</title>
	
</head>
<body>
	<div id="msg_locn"></div>
	<button class="abutton" type="button" onclick="history.back()">Back</button>
	<a id="email_button" class="abutton" href="#">Get Help</a>
</body>
</html>