var	gSurvey,
	gQuestions,
	gCurrentProject,
	gCurrentLang = 0;	// Each language is in an array, this determines the array element that will be shown

$(document).ready(function() {
	
	// Get projects and surveys
	getMyProjects(-1, getSurveyList, false);
	
	// Add menu functions
	$('#m_get_survey').off().click(function() {	// Get a survey from Smap
		$('#smap').foundation('reveal', 'open');
	});
	
	// Add responses to events
	$('#project_name').change(function() {
		gCurrentProject = $('#project_name option:selected').val();
		saveCurrentProject(gCurrentProject);	// Save the current project id
		getSurveyList();
 	 });
	
	$('#get_survey').off().click(function() {
		getSurvey($('#survey_name option:selected').val());
		$('#smap').foundation('reveal', 'close');
 	 });
	
	
	$(document).foundation();		// Add foundation styling
	
});

function getSurveyList() {
	loadSurveys(gCurrentProject, undefined, false, false, undefined);
}


function getSurvey(sId) {

	var url="/surveyKPI/surveys/" + sId;
	
	addHourglass();
	$.ajax({
		url: url,
		dataType: 'json',
		cache: false,
		success: function(data) {
			removeHourglass();
			gSurvey = data;
			console.log("Survey");
			console.log(gSurvey);
			gQuestions = {questions: gSurvey.forms[0].questions};
			gCurrentLanguage = gSurvey.languages.indexOf(gSurvey.def_lang);	// Set the initial language to the default language
			refreshView();
		},
		error: function(xhr, textStatus, err) {
			removeHourglass();
			if(xhr.readyState == 0 || xhr.status == 0) {
	              return;  // Not an error
			} else {
				alert("Error: Failed to get survey: " + err);
			}
		}
	});	
}

function refreshView() {
	
	// Modify Template to reflect view parameters
	
	$('#questions').html(Mustache.to_html( $('#tpl').html(), gQuestions));
	$('#questions select').each(function(){
		$(this).val($(this).attr("data-sel"));
	});
	$('#questions').foundation();
}
