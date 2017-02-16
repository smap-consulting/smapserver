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

	var gForms = {};
	
	return {	
		
		get: get,
		clear: clear
	};
	
	/*
	 * Get the forms in a survey
	 */
	function get(surveyId, callback) {
		
		if(gForms[surveyId]) {
			callback(gForms[surveyId]);
		} else {
			
			addHourglass();
			
			$.ajax({
				url: "/surveyKPI/survey/" + surveyId + "/getMeta",
				dataType: 'json',
				cache: false,
				success: function(data) {
					removeHourglass();
					gForms[surveyId] = data.forms;
					callback(data.forms);
				},
				error: function(xhr, textStatus, err) {
					
					removeHourglass();
					if(xhr.readyState == 0 || xhr.status == 0) {
			              return;  // Not an error
					} else {
						alert(localise.set["msg_err_get_f"] + ": " + err);
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
	
});
