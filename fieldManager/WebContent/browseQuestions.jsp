<%@ page import="org.smap.server.managers.PersistenceContext"%>
<%@ page import="org.smap.server.managers.QuestionManager"%>
<%@ page import="org.smap.server.managers.TranslationManager"%>
<%@ page import="org.smap.server.managers.SurveyManager"%>
<%@ page import="org.smap.server.entities.Question"%>   
<%@ page import="org.smap.server.entities.Translation"%>   
<%@ page import="org.smap.server.entities.Survey"%> 
<%@ page import="java.util.*" %>
<%@ page language="java" contentType="text/html; charset=utf-8"
    pageEncoding="utf-8"%>
<!DOCTYPE html>

<!--
This file is part of SMAP.

SMAP is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

SMAP is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with SMAP.  If not, see <http://www.gnu.org/licenses/>.
-->

<html>
<head>
	<meta http-equiv="Content-Type" content="text/html; charset=utf-8">
	<meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no">
		
	<link rel=StyleSheet href="/css/smap.css" type="text/css">
	<link type="text/css" media="all" href="/css/Aristo/Aristo.css" rel="stylesheet" />
	<link rel="stylesheet" href="/css/responsivemobilemenu.css" type="text/css"/>
		
	<script data-main="js/browsequestions_main" src="/js/libs/require.js"></script>
	<script type="text/javascript"> if (!window.console) console = {log: function() {}}; </script>
	<title>Browse Questions</title>
</head>
<body>
	
	<div class="rmm noPrint" data-menu-style = "minimal">
		<ul>
			<li><a href="/index.html">Home</a></li>
			<li><a href="templateManagement.html">Template Management</a></li>
			<li><a href="/fieldAnalysis/index.html">Analysis</a></li>
			<li><a href="monitor.html">Monitoring</a></li>
			<li><a href="http://help.smap.com.au" target="_top">Help</a></li>
		</ul>
	</div>	
	<p style="clear: both;"></p>
	<div id="header">
		<div id="username"></div>
		<div id="banner">Questions</div>
	</div>
	
	<%
	PersistenceContext pc = new PersistenceContext("pgsql_jpa");
	QuestionManager qm = new QuestionManager(pc);
	TranslationManager tm = new TranslationManager(pc);
	SurveyManager sm = new SurveyManager(pc);
	String formName = request.getParameter("name");
	String lang = request.getParameter("lang");
	
	String sId = request.getParameter("sId");
	int surveyId = Integer.parseInt(sId); 
	
	String fId = request.getParameter("id");
	int id = Integer.parseInt(fId);

	Survey survey = sm.getById(surveyId);

	List <Question> questionList = qm.getByFormId(id);
	List <Translation> translationList = tm.getBySurveyAndLanguageAndType(survey, lang, "none");
	List <Translation> translationListManifest = tm.getManifestBySurvey(survey, lang);
	
	out.println("<h1>Questions in Form: " + formName + "</h1>");
	%>
	
	<table id="fl" class="tablesorter" border="1" width="100%">
	<thead>
		<tr>
			<th>Sequence</th>
			<th>Name</th>
			<th>Type</th>
			<th>Question</th>
			<th>Relevant</th>
			<th>Constraint</th>
			<th>Image</th>
			<th>Video</th>
			<th>Audio</th>
			<th>Media</th>
		</tr>
	</thead>
	<tbody>
		<% 
		boolean inMeta = false;	// Store whether the question is within the meta group as images arn't associated with meta questions
		for (int i = 0; i < questionList.size(); i++ ) {
			Question q = questionList.get(i);
			
			if(!inMeta && q.getName().equals("meta")) {
				inMeta = true;
			}
			if(inMeta && q.getName().equals("meta_groupEnd")) {
				inMeta = false;
			}
			String questionText = "";
			String imageName = null;
			String videoName = null;
			String audioName = null;
			for(int j = 0; j < translationList.size(); j++ ) {
				Translation t = translationList.get(j);
				if(t.getTextId().equals(q.getQTextId())) {
					questionText = t.getValue();
					break;
				}
			}
			
			// Get media links
			for(int j = 0; j < translationListManifest.size(); j++ ) {
				Translation t = translationListManifest.get(j);
				if(t.getTextId().equals(q.getQTextId())) {
					if(t.getType().equals("image")) {
						imageName = t.getValue();
					} else if(t.getType().equals("video")) {
						videoName = t.getValue();
					} else if(t.getType().equals("audio")) {
						audioName = t.getValue();
					}

				}
			}
			
			out.println("<tr>");
			out.println("<td>" + q.getSeq() + "</td>");
			out.println("<td>" + q.getName() + "</td>");
			String type = q.getType();
			if(type.toLowerCase().startsWith("select")) {
				out.println("<td>" + "<a href=browseOptions.jsp?id=" + q.getId() + "&name=" + q.getName() +
					"&lang=" + lang + "&sId=" + sId +
					">" + type + "</a></td>");
			} else if(type.equals("string")) {
				out.println("<td>text</td>");
			} else {
				out.println("<td>" + type + "</td>");
			}
			out.println("<td>" + questionText + "</td>");
			out.println("<td>" + q.getRelevant() + "</td>");
			out.println("<td>" + q.getConstraint() + "</td>");
			if(imageName != null) {
				out.println("<td><image width=\"150\" src=\"/media/" + imageName + "\" target=\"_blank\"></image></td>");
			} else {
				out.println("<td></td>");
			}
			if(videoName != null) {
				out.println("<td><a href=\"/media/" + videoName + "\" target=\"_blank\">video</a></td>");
			} else {
				out.println("<td></td>");
			}
			if(audioName != null) {
				out.println("<td><a href=\"/media/" + audioName + "\"  target=\"_blank\">audio</a></td>");
			} else {
				out.println("<td></td>");
			}
			String source = q.getSource();
			if(!inMeta && source != null && source.equals("user")) {
				out.println("<td><button value=\"" + sId + ":" + q.getId() + "\" class=\"media_btn_add\">+</button>");
				out.println("<button value=\"" + sId + ":" + q.getId() + "\" class=\"media_btn_rem\">-</button></td>");
			} else {
				out.println("<td></td>");
			}
			out.println("</tr>");
		}
		%>
	</tbody>
	</table>
	
	<div id="add_media" style="display:none;">
		<form id="media_controls" name="add_media_form" action="MediaUpload" enctype="multipart/form-data" method="POST" 
				class="ui-body ui-body-a ui-corner-all">
			<input id="media_keys" type="hidden" name="media_keys" value="" style="display:none;"/>	
			<input id="original_url" type="hidden" name="original_url" value="" style="display:none;"/>								
			<label for="file">File:</label> 
			<input type="file" name="tname" id="file" size="30"/><br/>

			<!-- <button	class="abutton" type="submit" name="submitFile" value="Upload File">Upload</button> -->
		</form>
	</div>
	
	<img id="hour_glass" src="/images/ajax-loader.gif" style="display:none;" height="60" width="60">
	
	<!-- 
	<script type="text/javascript" src="/js/libs/jquery-1.8.3.min.js"></script>
	<script src="/js/responsivemobilemenu.js"></script>
	<script src="/js/libs/jquery-ui-1.10.3.custom.min.js"></script> 
	<script src="js/tablesorter.js"></script>
	<script src="js/media.js"></script>
	<script src="/js/common.js"></script>
	<script>
  		$(document).ready(function(){
        	$("#fl").tablesorter({ widgets: ['zebra'] });
  		});
	</script>
	-->
</body>
</html>