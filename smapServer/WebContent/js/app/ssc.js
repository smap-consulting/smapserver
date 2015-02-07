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
"use strict";

define([
         'jquery',
         'modernizr',
         'app/localise',
         'app/globals'], 
		function($, modernizr, lang, globals) {

	return {
	
		init: init,
		setHtml: setHtml,
		populateFormList: populateFormList
	};
	



	function init() {
		
		var i;
		
		
		
		/*
		 * Add options for units
		 */
		$('#ssc_function').change(function () {
			var fn = $(this).val();
			$('#ssc_name').val(fn);		// Default name to name of function
			$("#ssc_form").empty();
			if(fn !== "") {
				populateFormList(fn);
				if(fn === "area") {
					$('#ssc_units').html(
							'<option value="hectares">Hectares</option>' +
							'<option value="square meters">Square Meters</option>'
							);
				} else if(fn === "length") {
					$('#ssc_units').html(
							'<option value="km">Kilometers</option>' +
							'<option value="meters">Meters</option>'
							);
				}
			} else {
				$('#ssc_units').html('');
			}
		});
			
		$('#add_ssc_modal').on('show.bs.modal', function (event) {
			  $(this).find("form")[0].reset();
			  
		});
	}


	function setHtml(selector, sscList) {
		var h =[],
			idx = -1,
			i;
		
		if(sscList) {
			for(i = 0; i < sscList.length; i++) {
				h[++idx] = '<tr>';
					h[++idx] = '<td>';
					h[++idx] = sscList[i].name;
					h[++idx] = '</td>';	
					h[++idx] = '<td>';
					h[++idx] = sscList[i].form;
					h[++idx] = '</td>';	
					h[++idx] = '<td>';
					h[++idx] = sscList[i].fn;
					h[++idx] = '</td>';	
					h[++idx] = '<td>';
					h[++idx] = sscList[i].units;
					h[++idx] = '</td>';	
					h[++idx] = '<td>';
					h[++idx] = sscList[i].parameters;
					h[++idx] = '</td>';	
					h[++idx] = '<td><button value="';
					h[++idx] = i;
					h[++idx] = '" class="btn btn-danger ssc_btn_rem"><span class="glyphicon glyphicon-trash edit_icon"></span></button></td>';
					
				h[++idx] = '</tr>';
			}
	
			$(selector).html(h.join(''));
		}
		
	}


	function populateFormList(fn) {
		var sId = $('#s_id').val(),
			i,
			h = [],
			idx = -1;
		
		addHourglass();
	 	$.ajax({
			url: "/surveyKPI/ssc/" + globals.gCurrentSurvey + "/" + fn + "/forms",
			dataType: 'json',
			success: function(data) {
				removeHourglass();
				console.log("Forms");
				console.log(data);
				if(data.length === 0) {
					$('#ssc_alert').show().text("No forms found that can be used for function " + fn);
					setTimeout(function() {
						$('#ssc_function').focus();
						}, 0);		
				} else {
					
					for(i = 0; i < data.length; i++) {
						h[++idx] = '<option value="';
						h[++idx] = data[i].fId;
						h[++idx] = '">';
						h[++idx] = data[i].name;
						h[++idx] = '</option>';
					}
					$("#ssc_form").html(h.join(''));
				}
			},
			error: function(xhr, textStatus, err) {
				removeHourglass();
				if(xhr.readyState == 0 || xhr.status == 0) {
		              return;  // Not an error
				} else {
					$('#ssc_alert').show().text("Error: Failed to get list of forms: " + err);

				}
			}
		});
	}
	


});