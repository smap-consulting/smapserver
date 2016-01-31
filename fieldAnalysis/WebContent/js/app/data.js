/*
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
*/

/*
 * Get a surveys meta data
 */
function getSurveyMetaSE(sId, view, getS, updateExport, updateDatePicker, currentDate, neo_model) {

	if(sId != -1) {
		var url = surveyMeta(sId);
	
		addHourglass();
	 	$.ajax({
			url: url,
			dataType: 'json',
			success: function(data) {
				removeHourglass();
				globals.gSelector.addSurvey(sId, data);
				if(getS) {
					 getSurveyDataSE(sId, view);
				}
				if(updateExport) {
					addFormPickList(data);
				}
				if(updateDatePicker) {
					addDatePickList(data, currentDate);
				}
				if(neo_model) {
					$('.showthingsat').show();
					
					// Set the form to the value stored in the model
					if(sMeta.model) {
						var graph = JSON.parse(sMeta.model);
						$('.osmform[value=' + graph.form + ']').prop("checked", "checked");
					}
					
					neo_model.init(sId, undefined, undefined, data.model);
					neo_model.showModel('#ta_model_show', sId, 200, 200);
				}

			},
			error: function(xhr, textStatus, err) {
				removeHourglass();
  				if(xhr.readyState == 0 || xhr.status == 0) {
		              return;  // Not an error
				} else {
					$('#status_msg_msg').empty().text("Error failed to get data for survey:" + sId);
					$("#status_msg").dialog("open");
				}
				refreshData(view, "survey");
			}
		});
	} {
		$("#settings_date_question").empty();
	}
}


 /*
  * Get a question's Meta Data
  * This meta data will identify the data that can be retrieved for this question
  */
  function getQuestionMeta(sId, qId, language, view) {

	gMetaInProgress++;
		
 	function getAsyncQuestionMeta(aUrl, asId, aqId, alanguage, view) {
 	 	$.ajax({
 			url: aUrl,
 			dataType: 'json',
 			success: function(data) {
 				gMetaInProgress--;
 				globals.gSelector.addQuestion(aqId, alanguage, data);	
 				if(view && view.type == "map") {
 					getGeometryQuestion(asId, data.f_id);
 				} 
 				if(view && view.fn) {
 					setQ1Functions(data.type, view.type, view.fn);
 				} else {
 					setQ1Functions(data.type, undefined, "percent");
 				}
 			},
 			error: function(data) {
 				gMetaInProgress--;
 				console.log("Error in " + aUrl);
 			}
 		});
 	}
 	
 	if(sId != -1 && qId > 0) {
 		var url = questionMetaURL(sId, language, qId);
 		getAsyncQuestionMeta(url, sId, qId, language, view);
 	} else {
  		console.log("Error: getQuestionMeta:" + sId +":" + qId);
 		gMetaInProgress--;
 	}
  }
 
  /*
   * Get a group's Meta Data
   * This meta data will identify the data that can be retrieved for this grouping question
   */
   function getGroupMeta(sId, qId, language) {
		
	   if(typeof qId === "undefined") {
		   return;
	   }
	   
	   gMetaInProgress++;

	  	function getAsyncGroupMeta(aUrl, asId, aqId, alanguage) {
	  	 	$.ajax({
	  			url: aUrl,
	  			dataType: 'json',
	  			success: function(data) {
	  				gMetaInProgress--;
	  				globals.gSelector.addQuestion(aqId, alanguage, data);	
	  			},
	  			error: function(data) {
	  				gMetaInProgress--;
	  				console.log("Error in " + aUrl);
	  			}
	  		});
	  	}
	  	
	  	if(sId != -1 && qId > 0) {
	  		var url = questionMetaURL(sId, language, qId);
	  		getAsyncGroupMeta(url, sId, qId, language);
	  	} else {
	  		console.log("Error: getGroupMeta:" + sId +":" + qId);
	  		gMetaInProgress--;
	  	}
   }
 
//Get data at the survey level
 function getSurveyDataSE(sId, view) {
 	
 	var formItems = globals.gSelector.getFormItems(sId);
 		//hasData = false,
 		//idx;
 	
	view.tableCount = 0;
	view.results = [];
 	
	survey = globals.gSelector.getSurvey(sId);
	view.tableCount = survey.forms.length;
	for(i = 0; i < survey.forms.length; i++) {
		processSurveyData(survey.forms[i].f_id, sId, view, survey.name, false, 0 );		// Get table data
	}

 }
 
 /*
  * Get the survey level data for a specific table
  */
function processSurveyData(fId, f_sId, f_view, survey, replace, start_rec) {
	
	// For table all of survey views. page the results and include "bad records"
	if(f_view.type === "table") {
		rec_limit = 200;
		bBad = true;
	} else {
		rec_limit = 0;
		bBad = false;
	}
	
	if(typeof start_rec === "undefined") {
		start_rec = 0;
	}
	
	var survey,
		i,
		url = formItemsURL(fId, "yes", "no", start_rec, rec_limit, bBad, f_view.filter,
				f_view.dateQuestionId, f_view.fromDate, f_view.toDate);	// Get all records with all features
	
	addHourglass();
 	$.ajax({
		url: url,
		dataType: 'json',
		cache: false,
		success: function(data) {
			// Add meta data to results
			removeHourglass();
			data.source = "survey";
			data.sId = f_sId;
			data.table = true;
			data.fId = fId;
			data.survey = survey;
			
			// Record starting records for each table so that "less" will work
			if(typeof f_view.start_recs === "undefined") {
				f_view.start_recs = {};
			}
			if(typeof f_view.start_recs[fId] === "undefined") {
				f_view.start_recs[fId] = [];
			}
			f_view.start_recs[fId].push(start_rec);
			
	 		globals.gSelector.addDataItem(url, data);
	 		f_view.tableCount--;
	 		
	 		if(replace) {
	 			for(i = 0; i < f_view.results.length; i++) {
	 				if(f_view.results[i].fId === fId) {
	 					f_view.results[i] = data;
	 					break;
	 				}
	 			}
	 		} else {
	 			f_view.results[f_view.tableCount] = data;
	 		}
			if(f_view.tableCount === 0) {
				refreshData(f_view, "survey");	// Update the views with the new survey data after all services have returned
			}	

		},
		error: function(data) {
			removeHourglass();
		}
	});
 		
}
	
 /*
  * Get the data for the specified question
  * The current view is updated with the results
  */
 function getResults(view) {
	 
	 	function getAsyncResults(view, sId, data, dateId, groupId, groupType, groupRegion, fn, lang, timeGroup, 
	 			fromDate, toDate, qId_is_calc) {
			
			var url = resultsURL (sId, data, dateId, groupId, groupType, groupRegion, fn, lang, timeGroup, 
					fromDate, toDate, qId_is_calc, view.filter );
			
			addHourglass();

			$.ajax( {
				url: url,
				cache: false,
	  			dataType: 'json',
	  			success: function(results) {	
	  				globals.gSelector.addDataItem(url, results);				// Cache the raw data
	  				view.results = results;						// Save the raw data against the panel
	  				refreshData(view, "question");
	  				removeHourglass();
	  			},
	  			error: function(xhr, textStatus, err) {
	  				removeHourglass();
	  				if(xhr.readyState == 0 || xhr.status == 0) {
	  		              return;  // Not an error
	  				} else {
	  					alert("Error: Failed to get results: " + err);
	  				}
	  			}
			});
		} 
	
	// Call ajax to get the results
	getAsyncResults(view,
			view.sId, 
			view.qId, 
			view.dateQuestionId, 
			view.groupQuestionId,
			view.groupType,
			view.region,
			view.fn,
			view.lang,
			view.timeGroup,
			view.fromDate,
			view.toDate,
			view.qId_is_calc);	
 }
 
 function lookup(key, array) {
	 var i;
	 
	 for(i = 0; i < array.length; i++) {
		 if(key === array[i]) {
			 return i;
		 }
	 }
	 return -1;
 }
 
//Refresh the data in the panel
 function refreshData(view, surveyLevel) {
 		
 	var views,
 		i,
 		outputView,
 		secondaryLayer = false;		// Set true if this is map data displayed on a different panel
 	
 	outputView = view;
 	// Check to see if we need to show these results on a different view panel
 	if(view.layerId > 0) {
 		views = globals.gSelector.getViews();
 		for(i = 0; i < views.length; i++) {
 			if(view.layerId === views[i].id) {
 				outputView = views[i];  
 				secondaryLayer = true;
 				break;
 			}
 		}
 	}
 	
 	if(surveyLevel === "survey") {
 		
 		switch(outputView.type) {
 		case "map":
 			
 			setMap(outputView, secondaryLayer);
 			break;
 		case "table":
 			setTableSurvey(outputView);
 			break;
 		case "media":
 			setMediaSurvey(outputView);
 			break;
 		case "graph":
 			setGraphSurvey(outputView);
 			break;
 		}
 	} else {
 		
 		switch(outputView.type) {
 		case "map":
 			setMap(outputView, secondaryLayer);
 			break;
 		case "table":
 			setTableQuestion(outputView);
 			break;
 		case "media":
 			setMediaQuestion(outputView);
 			break;
 		case "graph":
 			newSetGraphQuestion(outputView);	
 			break;
 		}	
 	}

 }
