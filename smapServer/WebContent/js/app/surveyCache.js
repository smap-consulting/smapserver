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
 * Functions for providing access to a list of surveys in a project
 */

"use strict";

define([
         'jquery',
         'modernizr',
         'app/localise',
         'app/globals'], 
		function($, modernizr, lang, globals) {

	var gSurveys = {};
	
	return {	
		
		get: get,
		clear: clear,
		getName: getName
	};
	
	/*
	 * Get the surveys for a project
	 */
	function get(projectId, callback) {
		
		if(gSurveys[projectId]) {
			callback(gSurveys[projectId]);
		} else {
			
			addHourglass();
			
			$.ajax({
				url: "/surveyKPI/surveys?projectId=" + projectId + "&blocked=true",
				dataType: 'json',
				cache: false,
				success: function(data) {
					removeHourglass();
					gSurveys[projectId] = data;
					callback(data);
				},
				error: function(xhr, textStatus, err) {
					
					removeHourglass();
					if(xhr.readyState == 0 || xhr.status == 0) {
			              return;  // Not an error
					} else {
						alert(localise.set["msg_err_get_s"] + ": " + err);
					}
				}
			});
		}
		return;
	}
	
	/*
	 * Clear the cache
	 */
	function clear() {
		
		gSurveys = {};

	}
	
	/*
	 * Get the name of a form given its id
	 */
	function getName(projectId, surveyId) {
		var i,
			surveyList = gSurveys[projectId],
			name;
		
		if(surveyList) {
			for(i = 0; i < surveyList.length; i++) {
				if(surveyList[i].id = surveyId) {
					name = surveyList[i].displayName;
				}
			}
		}
		return name;
		
	}
	
});
