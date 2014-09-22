<%@ page import="org.smap.server.managers.PersistenceContext"%>
<%@ page import="org.smap.server.managers.OptionManager"%>
<%@ page import="org.smap.server.managers.TranslationManager"%>
<%@ page import="org.smap.server.managers.SurveyManager"%>
<%@ page import="org.smap.server.entities.Option"%>   
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
	
	<script data-main="js/browseoptions_main" src="/js/libs/require.js"></script>
	<script type="text/javascript"> if (!window.console) console = {log: function() {}}; </script>
	<title>Browse Options</title>
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
		<div id="banner">Forms</div>
	</div>
	
	<%
				PersistenceContext pc = new PersistenceContext("pgsql_jpa");
				TranslationManager tm = new TranslationManager(pc);
				SurveyManager sm = new SurveyManager(pc);
				OptionManager om = new OptionManager(pc);
				String questionName = request.getParameter("name");
				String qId = request.getParameter("id");
				int id = Integer.parseInt(qId);
				String lang = request.getParameter("lang");			
				String sId = request.getParameter("sId");
				int surveyId = Integer.parseInt(sId); 
				
				Survey survey = sm.getById(surveyId);

				List <Translation> translationList = tm.getBySurveyAndLanguageAndType(survey, lang, "none");
				List <Translation> translationListManifest = tm.getManifestBySurvey(survey, lang);
				List <Option> optionList = om.getByQuestionId(id);
				
				out.println("<h1>Options for Question: " + questionName + "</h1>");
			%>
	
	<table id="fl" class="tablesorter" border="1" width="100%">
	<thead>
		<tr>
			<th>Sequence</th>
			<th>Label</th>
			<th>Value</th>
			<th>Image</th>
			<th>Video</th>
			<th>Audio</th>
			<th>Media</th>
		</tr>
	</thead>
	<tbody>
		<% 
		for (int i = 0; i < optionList.size(); i++ ) {
			Option o = optionList.get(i);
			
			String optionText = null;
			String imageName = null;
			String videoName = null;
			String audioName = null;
			
			for(int j = 0; j < translationList.size(); j++ ) {
				Translation t = translationList.get(j);
				if(t.getTextId().equals(o.getLabelId())) {
					optionText = t.getValue();
					break;
				}
			}
			
			// Get media links
			for(int j = 0; j < translationListManifest.size(); j++ ) {
				Translation t = translationListManifest.get(j);
				if(t.getTextId().equals(o.getLabelId())) {
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
			out.println("<td>" + o.getSeq() + "</td>");
			out.println("<td>" + optionText + "</td>");
			out.println("<td>" + o.getValue() + "</td>");
			if(imageName != null) {
				out.println("<td><image width=\"150\" src=\"/media/" + imageName + "\"></image></td>");
			} else {
				out.println("<td></td>");
			}
			if(videoName != null) {
				out.println("<td><a href=\"/media/" + videoName + "\">video</a></td>");
			} else {
				out.println("<td></td>");
			}
			if(audioName != null) {
				out.println("<td><a href=\"/media/" + audioName + "\">audio</a></td>");
			} else {
				out.println("<td></td>");
			}
			out.println("<td><button value=\"" + sId + ":" + qId + ":" + o.getId() + "\" class=\"media_btn_add\">+</button>");
			out.println("<button value=\"" + sId + ":" + qId + ":" + o.getId() + "\" class=\"media_btn_rem\">-</button></td>");
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