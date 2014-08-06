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
         'foundation.min', 
         'modernizr',
         'app/localise',
         'app/globals'], 
		function($, foundation, modernizr, lang, globals) {

	return {
	
		init: init,
		setHtml: setHtml,
		populateFormList: populateFormList
	};
	



	function init() {
		
		var i;
		
		$('#add_ssc_done').off().click(function(){
	
			var fn = $('#ssc_function').val(),
				name = $('#ssc_name').val(),
				formId = $('#ssc_form option:selected').val(),
				form = $('#ssc_form option:selected').text();
			
			if(fn === "") {
				alert("You must select a function");
				return false;
			}
			if(name === "") {
				alert("You must specify a name");
				return false;
			}
			if(typeof form === "undefined" || form === "") {
				alert("You must specify a form");
				return false;
			}
			for(i = 0; i < globals.model.survey.sscList.length; i++) {
				if(globals.model.survey.sscList[i].name === name) {
					alert("There is an existing calculation with that name");
					return false;
				}
			}
			
			// Add the new ssc to the list
			var newSSC = {
					name: name,
					fn: fn,
					units: $('#ssc_units').val(),
					form: form,
					formId: formId
			}
			globals.model.survey.sscList.push(newSSC);
			setHtml('#sscList', globals.model.survey.sscList);
			globals.model.settingsChange();
			$('#add_ssc_popup').foundation('reveal', 'close');
		
		});
		
		$('#add_ssc_cancel').off().click(function(){
			$('#add_ssc_popup').foundation('reveal', 'close');
		});
		
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
			
		$('#add_ssc').off().click(function() {	
			$('#ssc_name').val($('#ssc_function').val());		// Default name to name of function
			$('#add_ssc_popup').foundation('reveal', 'open');
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
					h[++idx] = '" class="ssc_btn_rem">-</button></td>';
					
				h[++idx] = '</tr>';
			}
	
			$(selector).html(h.join(''));
			
			$('.ssc_btn_rem').click(function () {
				var id = $(this).val();
				globals.model.survey.sscList.splice(id,1);
				globals.model.settingsChange();
				setHtml('#sscList', globals.model.survey.sscList);
			});
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
					alert("No forms found that can be used for function " + fn);
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
					alert("Error: Failed to get list of forms: " + err);
					$('#add_ssc_popup').foundation('reveal', 'close');
				}
			}
		});
	}
	
	/*
	function saveSSC() {
		
		var name = $('#ssc_name').val(),
			form = $('#ssc_form option:selected').val(),
			fn = $('#ssc_function').val(),
			units = $('#ssc_units').val();
		
		addHourglass();
		$.ajax({
			  type: "POST",
			  contentType: "application/json",
			  dataType: "json",
			  url: "/surveyKPI/ssc/" + globals.gCurrentSurvey + "/" + fn + "/add",
			  data: { 
				  form: form,
				  name: name,
				  units: units
				  },
			  success: function(data, status) {
				  removeHourglass();
			  }, error: function(xhr, textStatus, err) {
				  removeHourglass();
				  console.log(xhr);
				  alert("Failed to add server side calculation: " + xhr.responseText); 
			  }
		});
	}
	*/

});