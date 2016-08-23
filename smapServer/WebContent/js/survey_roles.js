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
    	lang_location: '..'
    },
    shim: {
    	'app/common': ['jquery'],
        'bootstrap.min': ['jquery'],
        'jquery.autosize.min': ['jquery']
    }
});

require([
         'jquery',
         'app/common', 
         'bootstrap.min', 
         'modernizr',
         'app/localise',
         'app/ssc',
         'app/globals',
         'jquery.autosize.min'], 
		function($, common, bootstrap, modernizr, lang, ssc, globals) {


var	gRoles,
	gIdx;

$(document).ready(function() {
	
	var i,
		params,
		pArray = [],
		param = [];
	
	localise.setlang();		// Localise HTML

	// Get the user details
	globals.gIsAdministrator = false;
	getLoggedInUser(getSurveyRoles, false, true, undefined, false, false);
	getRoles
	
	// Save a row filter
	$('#saveRowFilter').click(function(){
		gRoles[gIdx].row_filter = $('#filter_row_content').text();
		updateRole(gIdx);
	});
	
	enableUserProfileBS();
	
});

function getSurveyRoles() {
	
	if(gRoles) {
		refreshView();
	} else {
		addHourglass();
		$.ajax({
			url: "/surveyKPI/role/survey/" + globals.gCurrentSurvey,
			dataType: 'json',
			cache: false,
			success: function(data) {
				removeHourglass();
				gRoles = data;
				refreshView();
			},
			error: function(xhr, textStatus, err) {
				removeHourglass();
				if(xhr.readyState == 0 || xhr.status == 0) {
		              return;  // Not an error
				} else {
					alert(localise.set["msg_err_get_r"] + ": " + err);
				}
			}
		});	
	}
}




/*
 * Convert change log JSON to html
 */
function refreshView() {
	
	var h =[],
		idx = -1,
		i,
		$element = $('#role_table'),
		hasEnabledRole = false;
	
	// write the table headings
	h[++idx] = '<table class="table">';
	h[++idx] = '<thead>';
		h[++idx] = '<tr>';
			h[++idx] = '<th>';
				h[++idx] = localise.set["c_role"];
			h[++idx] = '</th>';
		h[++idx] = '<th>';
			h[++idx] = localise.set["c_enabled"];
		h[++idx] = '</th>';
		h[++idx] = '<th>';
			h[++idx] = localise.set["ro_fr"];
		h[++idx] = '</th>';
		h[++idx] = '</tr>';
	h[++idx] = '</thead>';
	
	// Write the table body
	h[++idx] = '<body>';
	for(i = 0; i < gRoles.length; i++) {
		
		h[++idx] = '<tr>';
			h[++idx] = '<td>';
				h[++idx] = gRoles[i].name;
			h[++idx] = '</td>';	
			h[++idx] = '<td>';
				h[++idx] = '<div class="btn-group btn-toggle" data-idx="';
				h[++idx] = i;
				h[++idx] = '">';
				h[++idx] = '<button class="btn btn-xs norole ';
				if(!gRoles[i].enabled) {
					h[++idx] = 'btn-danger active"';
				} else {
					h[++idx] = 'btn-default"';
				}
				h[++idx] = '>';
				h[++idx] = localise.set["c_no"];
				h[++idx] = '</button>';
				h[++idx] = '<button class="btn btn-xs yesrole ';
				if(!gRoles[i].enabled) {
					h[++idx] = 'btn-default"';
				} else {
					h[++idx] = 'btn-success active"';
				}
				h[++idx] = '>';
				h[++idx] = localise.set["c_yes"];
				h[++idx] = '</button>';
				h[++idx] = '</div>';
			h[++idx] = '<td>';
				h[++idx] = '<button class="btn btn-xs row_filter';
				if(!gRoles[i].enabled) {
					h[++idx] = ' disabled';
				}
				if(gRoles[i].restrict_row) {
					h[++idx] = ' btn-success';
				}
				h[++idx] = '">';
				h[++idx] = '<i class="glyphicon glyphicon-filter"></i>';
				h[++idx] = '</button>';
			h[++idx] = '</td>';
		h[++idx] = '</tr>';
		
		if(gRoles[i].enabled) {
			hasEnabledRole = true;
		} 
	}
	h[++idx] = '</body>';
	h[++idx] = '</table>';
	
	$element.html(h.join(''));
	
	$('.btn-toggle', $element).click(function() {
		var $this = $(this),
			idx;
		
		$this.find('.btn').toggleClass('active').removeClass("btn-success btn-danger").addClass("btn-default"); 
		$this.find('.yesrole.active').addClass("btn-success").removeClass("btn-default");
		$this.find('.norole.active').addClass("btn-danger").removeClass("btn-default");
		
		idx = $this.data("idx");
		gRoles[idx].enabled = !gRoles[idx].enabled;
		updateRole(idx);
		
		$this.closest('tr').find('.row_filter').toggleClass("disabled");
		
		setInfoMsg();
	});
	
	// Row filtering logic
	$('.row_filter', $element).click(function() {
		var $this = $(this);
		
		if(!$this.hasClass("disabled")) {
			gIdx = $this.closest('tr').find('.btn-group').data("idx");
			$('#row_filter_popup').modal("show");
		}
	});
	
	if(hasEnabledRole) {
		$('#roles_alert').html(localise.set["msg_has_roles"]);
	} else {
		$('#roles_alert').html(localise.set["msg_no_roles"]);
	}
	
}

function setInfoMsg() {
	var i,
		hasEnabledRole = false;
	
	for(i = 0; i < gRoles.length; i++) {
		if(gRoles[i].enabled) {
			hasEnabledRole = true;
		}
	}
	if(hasEnabledRole) {
		$('#roles_alert').html(localise.set["msg_has_roles"]);
	} else {
		$('#roles_alert').html(localise.set["msg_no_roles"]);
	}
}

function updateRole(idx) {
	
	addHourglass();
	$.ajax({
		  type: "POST",
		  contentType: "application/json",
		  cache: false,
		  url: "/surveyKPI/role/survey/" + globals.gCurrentSurvey,
		  data: { 
			  role: JSON.stringify(gRoles[idx])
			  },
		  success: function(data, status) {
			  removeHourglass();
			  gRoles[idx].linkid = data.linkid;
		  }, error: function(data, status) {
			  removeHourglass();
			  if(data && data.responseText) {
				  alert(data.responseText);
			  } else {
				  alert(localise.set["msg_u_f"]);
			  }
		  }
	});
}

});