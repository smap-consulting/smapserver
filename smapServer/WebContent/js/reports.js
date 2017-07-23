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
 * Purpose: Allow the user to select a web form in order to complete a survey
 */
var gUserLocale = navigator.language;
if (Modernizr.localstorage) {
	gUserLocale = localStorage.getItem('user_locale') || navigator.language;
} 

var gReportList = [];
var gConfig;
var gReportIdx;

requirejs.config({
    baseUrl: 'js/libs',
    waitSeconds: 0,
    locale: gUserLocale,
    paths: {
    	app: '../app',
    	jquery: '../../../../js/libs/jquery-2.1.1',
       	lang_location: '../'
    },
    shim: {
    	'app/common': ['jquery'],
    	'bootstrap.min': ['jquery'],
    	'icheck': ['jquery'],
       	'inspinia': ['jquery'],
    	'metismenu': ['jquery'],
    	'slimscroll': ['jquery']
    }
});

require([
         'jquery', 
         'bootstrap.min',
         'app/common', 
         'app/globals',
         'app/localise',
         'bootstrapfileinput',
         'inspinia',
         'metismenu',
         'slimscroll',
         'pace',
         'icheck'
         ], function($, bootstrap, common, globals, localise, bsfi) {

	$(document).ready(function() {

		localise.setlang();		// Localise HTML

		// Get the user details
		globals.gIsAdministrator = false;
		getLoggedInUser(undefined, false, true, undefined);
		getReportsForUser();

        $('#generateReport').click(function() {
        	var i;
        	var filename;
        	var val;
        	var params = [];
        	var url;

        	for(i = 0; i < gConfig.length; i++) {
        		val = $('#param_' + gConfig[i].name).val();

        		// Validate
        		if(gConfig[i].required && (typeof val === "undefined" || val.trim().length === 0)) {
                    $('#alert').html(localise.set['ed_req'] + " : " + localise.set[gConfig[i].trans])
        			$('#alert').show();
        			return false;
                }

                if(gConfig[i].name === "filename") {
                    filename = val;
                } else {
                    params.push({
                        key: gConfig[i].name,
                        val: val
                    })
                }

                gReportList[gReportIdx].savedParams[gConfig[i].name] = val;
			}
            $('#alert').hide();

            url = '/surveyKPI/reportgen/' +
                gReportList[gReportIdx].sId + '/' +
                cleanFileName(gReportList[gReportIdx].name);
            if(params.length > 0) {
                for(i = 0; i < params.length; i++) {
                    if(i == 0) {
                        url += '?';
                    } else {
                        url += '&';
                    }
                    url += params[i].key + '=' + params[i].val;
                }
            }

            $('#report_popup').modal("hide");
            window.location.href = url;

		});



		enableUserProfileBS();
	});


	function getReportsForUser() {

		url="/surveyKPI/reportgen";

		addHourglass();
		$.ajax({
			url: url,
			dataType: 'json',
			cache: false,
			success: function(data) {
				removeHourglass();
				gReportList = data;
				completeReportList(data);
			},
			error: function(xhr, textStatus, err) {
				removeHourglass();
				if(xhr.readyState == 0 || xhr.status == 0) {
					  return;  // Not an error
				} else {
					console.log("Error: Failed to get list of reports: " + err);
				}
			}
		});
	}

	/*
	 * Fill in the report list
	 */
	function completeReportList() {

		var i,
			h = [],
			idx = -1,
			$reportList = $('#report_list');

		// Add the reports
		if(gReportList) {
            for (i = 0; i < gReportList.length; i++) {
            	/*
				h[++idx] = '<a role="button" class="btn btn-primary btn-block btn-lg" href="/surveyKPI/reportgen/';
				h[++idx] = reportList[i].sId;
				h[++idx] = '/';
				h[++idx] = cleanFileName(reportList[i].name);
                h[++idx] = '">';
                h[++idx] = reportList[i].name;
                h[++idx] = '</a>';
                */
                h[++idx] = '<button type="button" data-idx="';
                h[++idx] = i;
                h[++idx] = '" class="btn btn-block btn-primary report">';
                h[++idx] = gReportList[i].name;
                h[++idx] = '</button>';

                // Add an object to store parameter values
				gReportList[i].savedParams = {};

            }
        }

		$reportList.html(h.join(''));

		$reportList.find('.report').click(function() {

			gReportIdx = $(this).data("idx");

			// Hard code the config for present - this should have come from the DB
			gConfig = [
				{
					name: "from",
					type: "date",
					trans: "a_from_date",
					required: true
				},
                {
                    name: "to",
                    type: "date",
                    trans: "a_to_date",
                    required: true
                },
				{
                    name: "filename",
                    type: "text",
                    trans: "sr_fn",
                    required: true
                }
			];

			// Set up the dialog according to the required parameters
			idx = -1;
			h = [];

			for(i = 0; i < gConfig.length; i++) {
				h[++idx] = '<div class="form-group">';

				// Label
				h[++idx] = '<label for="param_';
				h[++idx] = gConfig[i].name;
				h[++idx] = '" class="col-sm-2 control-label">';
				h[++idx] = localise.set[gConfig[i].trans];
				h[++idx] = '</label>';

				// Control
				h[++idx] = '<div class="col-sm-10">';
				h[++idx] = '<input type="';
				h[++idx] = gConfig[i].type;
				h[++idx] = '" id="param_';
                h[++idx] = gConfig[i].name;
                h[++idx] = '"';
                if(gConfig[i].required) {
                	h[++idx] = " required";
				}
				h[++idx] = ' class="form-control">';
                h[++idx] = '</div>';

                h[++idx] = '</div>';    // Form group

			}
			$('#report_params_form').empty().html(h.join(''));

            $('#alert').hide();

            // Restore saved parameters
            for(i = 0; i < gConfig.length; i++) {
                if(gReportList[gReportIdx].savedParams[gConfig[i].name]) {
                    $('#param_' + gConfig[i].name).val(gReportList[gReportIdx].savedParams[gConfig[i].name]);
				} else if(gConfig[i].name === "filename") {
                    $('#param_' + gConfig[i].name).val(gReportList[gReportIdx].name);
				}
            }

			$('#report_popup').modal("show");
		});

	}

});

