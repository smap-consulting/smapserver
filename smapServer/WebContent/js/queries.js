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

along with SMAP.  If not, see <http://www.gnu.org/licenses/>.

*/

var gUserLocale = navigator.language;
if (Modernizr.localstorage) {
	gUserLocale = localStorage.getItem('user_locale') || navigator.language;
} 

"use strict";
require.config({
    baseUrl: 'js/libs',
    waitSeconds: 0,
    locale: gUserLocale,
    paths: {
    	app: '../app',
    	jquery: 'jquery-2.1.1',
    	bootbox: 'bootbox.min',
    	toggle: 'bootstrap-toggle.min',
    	moment: 'moment-with-locales.min',
    	lang_location: '..'

    },
    shim: {
    	'app/common': ['jquery'],
        'bootstrap.min': ['jquery'],
        'jquery.autosize.min': ['jquery'],
        'bootstrap.file-input': ['bootstrap.min'],
    	'bootbox': ['bootstrap.min'],
       	'toggle': ['bootstrap.min']
        
    }
});

require([
         'jquery',
         'app/common', 
         'bootstrap.min', 
         'app/localise',
         'app/globals',
         'jquery.autosize.min',
         'bootstrap.file-input',
         'bootbox',
         'toggle',
         'moment'], 
		function(
				$, 
				common, 
				bootstrap, 
				lang, 
				globals, 
				jqas, 
				bsfi, 
				bootbox,
				toggle,
				moment) {



window.moment = moment;

var gQueries,
	gCurrentQuery;

'use strict';

$(document).ready(function() {
 
	localise.setlang();		// Localise HTML
	
	loadQueries();
	
	/*
	 * Add a new query
	 */
	$('#addQuery').click(function(){
		
		$('.panel_msg').hide();
		
		gCurrentQuery = {
				id: -1,				// New query
				name: undefined
		};
		
		$('#queryEditForm')[0].reset();
		$('.querycontent,.querytoolbar').toggle();
	});
	
	/*
	 * Save a query
	 */
	$('#saveQuery').click(function(){

		$('.panel_msg').hide();
		
		// Get the values
		gCurrentQuery.name = $('#query_name').val();
		
		// Validate
		if(!gCurrentQuery.name || gCurrentQuery.name.trim().length == 0) {
			$('.panel_msg').show().addClass('alert-danger').removeClass('alert-success').html(localise.set["msg_val_nm"]);
			return;
		}
		
		var queryString = JSON.stringify(gCurrentQuery);
		addHourglass();
		$.ajax({
			  type: "POST",
			  url: "/surveyKPI/query",
			  dataType: 'json',
			  cache: false,
			  data: { query: queryString },
				success: function(data) {
					removeHourglass();
					$('.panel_msg').show().addClass('alert-success').removeClass('alert-danger').html(localise.set["c_success"]);
					gQueries = data;
					showQueries();
					$('.querycontent,.querytoolbar').toggle();
				},
				error: function(xhr, textStatus, err) {
					removeHourglass();
					if(xhr.readyState == 0 || xhr.status == 0) {
			              return;  // Not an error
					} else {
						$('.panel_msg').show().addClass('alert-danger').removeClass('alert-success').html(localise.set["msg_err_save"]);
					}
				}
		});
	
	});
	
	$('#cancelQuery').click(function(){
		$('.querycontent,.querytoolbar').toggle();
		$('.panel_msg').hide();
	});
	
	
	
	/*
	 * Add a form to a query
	 */
	$('#addForm').click(function(){
		$('#add_form_popup').modal("show");
	});

	/*
	 * Change the update existing checkbox
	 */
	
	$('#select_existing').change(function(){
		$('.new_survey_only').toggle();
	});
	
});

/*
 * Get queries and update the query lists on this page
 */	
function loadQueries() {
 	
 	var url="/surveyKPI/query";

 	addHourglass();

	$.ajax({
		url: url,
		dataType: 'json',
		cache: false,
		success: function(data) {
 				
			removeHourglass();
			gQueries = data;
			showQueries();

		}, error: function(xhr, textStatus, err) {
 				
 				removeHourglass();
 				if(xhr.readyState == 0 || xhr.status == 0) {
 		              return;  // Not an error
 				} else {
 					alert("Error: Failed to get list of queriess: " + err);
 				}
 			}
 		});	 
 }

function showQueries() {
	var i,
		item,
		h = [],
		idx = -1,
		$elem = $('#queryListTable');
	
	h[++idx] = '<table class="table">';
	h[++idx] = '<thead>';
	h[++idx] = '<tr>';
	h[++idx] = '<th>' + localise.set["c_name"], + '</th>';
	h[++idx] = '<th>' + localise.set["c_desc"] + '</th>';
	h[++idx] = '<th></th>';
	h[++idx] = '</tr>';
	h[++idx] = '</thead>';
	h[++idx] = '<tbody class="table-striped">';
	
	if(gQueries.length > 0) {
		for(i = 0; i < gQueries.length; i++) {
			item = gQueries[i];

			h[++idx] = '<tr>';
				h[++idx] = '<td>';
				h[++idx] = item.name;
				h[++idx] = '</td>';
				h[++idx] = '<td>';
				h[++idx] = item.query;	// TODO replace with a human readable escription
				h[++idx] = '</td>';
				
				// actions
 				h[++idx] = '<td>';
 				
 				h[++idx] = '<button type="button" data-idx="';
 				h[++idx] = i;
 				h[++idx] = '" class="btn btn-default btn-sm edit_link btn-info">';
 				h[++idx] = '<span class="glyphicon glyphicon-edit" aria-hidden="true"></span></button>';
 				
 				h[++idx] = '</td>';
 				// end actions
			h[++idx] = '</tr>';
			
		}
	} else {
		$('.panel_msg').show().removeClass('alert-danger').addClass('alert-success').html(localise.set["msg_nq"]);
	}

	h[++idx] = '</tbody>';
	h[++idx] = '</table>';
			

	$elem.empty().html(h.join(''));
	
	// Add response to clicking on the edit button
	$(".edit_link", $elem).click(function(){
		var idx = $(this).data("idx");
		gCurrentQuery = jQuery.extend(true, {}, gQueries[idx]);

		$('#queryEditForm')[0].reset();
		$('#query_name').val(gCurrentQuery.name);
		$('.querycontent,.querytoolbar').toggle();
	});

		
}

});
