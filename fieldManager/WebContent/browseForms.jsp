<%@ page import="org.smap.server.managers.PersistenceContext"%>
<%@ page import="org.smap.server.managers.TranslationManager"%>
<%@ page import="org.smap.server.entities.Translation"%>
<%@ page import="org.smap.server.managers.FormManager"%>
<%@ page import="org.smap.server.managers.SurveyManager"%>
<%@ page import="org.smap.server.entities.Form"%> 
<%@ page import="org.smap.server.entities.Survey"%> 
<%@ page import="org.smap.sdal.Utilities.SDDataSource"%> 
<%@ page import="org.smap.sdal.model.ServerSideCalculate"%> 
<%@ page import="java.sql.*" %>  
<%@ page import="java.util.*" %>
<%@ page import="java.net.URLDecoder"%>
<%@ page language="java" contentType="text/html; charset=UTF-8"
    pageEncoding="UTF-8"%>
<!DOCTYPE html>

<html>
<head>
	<meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
	<meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no">
	<link rel=StyleSheet href="/css/smap.css" type="text/css">
	<link rel="stylesheet" href="/js/libs/OpenLayers/theme/default/style.tidy.css" type="text/css">
	<link type="text/css" media="all" href="/css/Aristo/Aristo.css" rel="stylesheet" />
	<link rel="stylesheet" href="/css/responsivemobilemenu.css" type="text/css"/>
	
	<script data-main="js/browseforms_main" src="/js/libs/require.js"></script>
	<script type="text/javascript"> if (!window.console) console = {log: function() {}}; </script>
	<title>Browse the Survey</title>
</head>
<body>

	<div class="rmm noPrint" data-menu-style = "minimal">
		<ul>
			<li><a href="/index.html">Home</a></li>
			<li><a href="templateManagement.html">Form Management</a></li>
			<li><a href="/fieldAnalysis/index.html">Analysis</a></li>
			<li><a href="monitor.html">Monitoring</a></li>
			<li><a href="http://help.smap.com.au" target="_top">Help</a></li>
		</ul>
	</div>
	<p style="clear: both;"></p>
	<div id="header">
		<div id="username"></div>
		<div id="banner">Forms</div>
	</div>
	
	<%
	String serverName = request.getServerName();
	String encName = null;
	String name = null;
	String query = request.getQueryString();	// getParameter uses ISO-8559-1 encoding so don't use it
	String [] params = query.split("&");
	for(int i = 0; i < params.length; i++) {
		String [] param = params[i].split("=");
		if(param.length > 0) {
			if(param[0].equals("name")) {
				encName = param[1];
				break;
			}
		}
	}
	System.out.println("Query: " + query);
	System.out.println("Name is: " + encName);
	try {
		name = URLDecoder.decode(encName, "UTF-8");
	} catch (Exception e) {
		 e.printStackTrace();
	}
	String sId = request.getParameter("id");
	int id = Integer.parseInt(sId);
	String def_lang = null;
	
	//Get the available languages
	try {
	    Class.forName("org.postgresql.Driver");	 
	} catch (ClassNotFoundException e) {
	    System.out.println("Error: Can't find PostgreSQL JDBC Driver");
	    e.printStackTrace();
	}

	Connection connection = SDDataSource.getConnection("fieldManager-browseForms");
	PreparedStatement pstmt = null;
	//PreparedStatement pstmt2 = null;
	//PreparedStatement pstmt3 = null;
	Vector<String> lang = new Vector<String>();
	ArrayList<ServerSideCalculate> sscList = new ArrayList<ServerSideCalculate>();
	List <Translation> translationListManifest = null;
	try {
		String sql1 = "SELECT DISTINCT language FROM translation " +
				"WHERE s_id = ? " +
				"ORDER BY language;";
		pstmt = connection.prepareStatement(sql1);
		pstmt.setInt(1, id);
		ResultSet rs = pstmt.executeQuery();
	
		int j = 0;
		while(rs.next()) {
			lang.add(j, rs.getString(1));
			j++;
		}

		/*
		String sql2 = "SELECT def_lang FROM survey " +
				"WHERE s_id = ? ";
		pstmt2 = connection.prepareStatement(sql2);
		pstmt2.setInt(1, id);
		
		ResultSet rs2 = pstmt2.executeQuery();

		if(rs2.next()) {
			def_lang = rs2.getString(1);
		}
		
		String sql3 = "SELECT id, name, function, parameters, units FROM ssc " +
				"WHERE s_id = ? " +
				"ORDER BY id";
		pstmt3 = connection.prepareStatement(sql3);
		pstmt3.setInt(1, id);
		
		ResultSet rs3 = pstmt3.executeQuery();

		while(rs3.next()) {
			ServerSideCalculate ssc = new ServerSideCalculate();
			ssc.setId(rs3.getInt(1));
			ssc.setName(rs3.getString(2));
			ssc.setFunction(rs3.getString(3));
			ssc.setUnits(rs3.getString(5));
			sscList.add(ssc);
		}
		*/
		
		// Get the list of Manifest files for this survey
		//PersistenceContext pc = new PersistenceContext("pgsql_jpa");
		//SurveyManager sm = new SurveyManager(pc);
		//Survey survey = sm.getById(id);
		//TranslationManager tm = new TranslationManager(pc);
		//translationListManifest = tm.getManifestBySurvey(survey, lang.get(0));	// Use the first language currently manifest files are identical for every language

		
	} catch (Exception e) {
		e.printStackTrace();
	} finally {
		try{if (pstmt != null) {pstmt.close();}	} catch (SQLException e) {}
		//try{if (pstmt2 != null) {pstmt2.close();}} catch (SQLException e) {}
		//try{if (pstmt3 != null) {pstmt3.close();}} catch (SQLException e) {}
		
		try {
			if (connection != null) {
				connection.close();
				connection = null;
			}
		} catch (SQLException e) {
			System.out.println("Failed to close connection");
		    e.printStackTrace();
		}
	}
	
	//Get the forms
	PersistenceContext pc = new PersistenceContext("pgsql_jpa");
	FormManager fm = new FormManager(pc);
	List <Form> formList = null;
	formList = fm.getBySurveyId(id);
	
	out.println("<h1>Forms in Survey: " + name + "</h1>");
	%>
	
	<table id="fl" class="tablesorter" border="1" width="100%">
	<thead>
		<tr>
			<th>Parent Form</th>
			<th>Form Id</th>
			<th>Form Name</th>
			<th>Table Name</th>
			<%
			for (int i = 0; i < lang.size(); i++ ) {
				out.println("<th>" + lang.elementAt(i) + "</th>");
			}
			%>
		</tr>
	</thead>
	<tbody>
		<% 
		for (int i = 0; i < formList.size(); i++ ) {
			Form f = formList.get(i);
			String f_id = String.valueOf(f.getId());
			Form pf = f.getParentForm();
			String parentName = "";
			if(pf != null) {
				parentName = pf.getName();
			}
				
			out.println("<tr>");
			out.println("<td>" + parentName + "</td>");
			out.println("<td>" + f_id + "</td>");
			out.println("<td>" + f.getName() + "</td>");
			out.println("<td>" + f.getTableName() + "</td>");
			for (int k = 0; k < lang.size(); k++ ) {
				out.println("<td><a href=browseQuestions.jsp?id=" + f_id + "&name=" + f.getName() + 
						"&lang=" + lang.elementAt(k) + "&sId=" + sId +
						">" + f.getName() + "</a></td>");
			}
			
			out.println("</tr>");
		}
		%>
		
	</tbody>
	</table>
	<br/>


	
<!-- JavaScript 	
	<script src="/js/libs/jquery-1.8.3.min.js"></script>
	<script src="/js/responsivemobilemenu.js"></script>
	<script src="js/tablesorter.js"></script>
	<script src="js/media.js"></script>
	<script type="text/javascript" src="/js/libs/jquery-ui-1.10.3.custom.min.js"></script> 
	<script src="js/surveyEditing.js" type="text/javascript"></script>
	<script src="/js/common.js"></script> 
	-->
</body>
</html>