requirejs.config({
    baseUrl: 'js/libs',
    paths: {
    	app: '../app',
    	jquery: 'jquery-1.8.3.min',
    	jquery_ui: 'jquery-ui-1.10.3.custom.min'
    }
});

require(['jquery', 'app/common', 'jquery_ui', 'responsivemobilemenu'], function($, common, jquery_ui, responsivemobilemenu) {

var	gSurveys;

$(document).ready(function() {
	
	addHourglass();
	
	$.ajax({
		url: "/surveyKPI/user",
		dataType: 'json',
		success: function(data) {
			removeHourglass();			
			globals.gCurrentProject = data.current_project_id;
			getMyProjects(globals.gCurrentProject, getSurveysForList);	// Get projects and call getSurveys when the current project is known

		},
		error: function(xhr, textStatus, err) {
			removeHourglass();
			if(xhr.readyState == 0 || xhr.status == 0) {
	              return;  // Not an error
			} else {
				console.log("Error: Failed to get list of users: " + err);
				alert("Error: Failed to get list of users: " + err);
			}
		}
	});	
	
	// Set change function on projects
	$('#project_name').change(function() {
		globals.gCurrentProject = $('#project_name option:selected').val();
		$('#projectId').val(globals.gCurrentProject);		// Set the project value for the hidden field in template upload
		getSurveysForList(globals.gCurrentProject);			// Get surveys
		saveCurrentProject(globals.gCurrentProject);		// Save the current project id
 	 });
	
	removeHourglass();
});



function getSurveysForList(projectId) {

	var url="/surveyKPI/surveys?projectId=" + projectId + "&blocked=false&deleted=false";
	
	addHourglass();
	$.ajax({
		url: url,
		dataType: 'json',
		cache: false,
		success: function(data) {
			gSurveys = data;
			completeSurveyList();
			removeHourglass();
		},
		error: function(xhr, textStatus, err) {
			removeHourglass();
			if(xhr.readyState == 0 || xhr.status == 0) {
	              return;  // Not an error
			} else {
				console.log("Error: Failed to get list of surveys: " + err);
			}
		}
	});	
}

/*
 * Fill in the survey list
 */
function completeSurveyList() {
	
	$('#forms').empty();

	for(i = 0; i < gSurveys.length; i++) {

		var $link = $('<a />').attr("class", "formLink")
        	.text(gSurveys[i].displayName);
     
		$link.attr("href", "/webforms/formXML.php"+"?key=" + gSurveys[i].id)
			.attr("target", "_blank")
			.addClass("online");
		
		$link.button();
		$('#forms').append($link).append('<br/>');	


	
	}	
}

});

