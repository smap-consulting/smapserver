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
 * Purpose: Manage the panels that display graphs, maps etc of results data
 */

var gUserLocale = navigator.language;
if (Modernizr.localstorage) {
	gUserLocale = localStorage.getItem('user_locale') || navigator.language;
} 

requirejs.config({
    baseUrl: 'js/libs',
    locale: gUserLocale,
    waitSeconds: 0,
    paths: {
     	app: '../app',
     	i18n: '../../../../js/libs/i18n',
     	async: '../../../../js/libs/async',
     	localise: '../../../../js/app/localise',
	    globals: '../../../../js/app/globals',
    	modernizr: '../../../../js/libs/modernizr',
	    moment: '../../../../js/libs/moment-with-locales.2.24.0',
    	common: '../../../../js/app/common',
    	lang_location: '../../../../js',
    	metismenu: '../../../../js/libs/wb/metisMenu/jquery.metisMenu',
    	pace: '../../../../js/libs/wb/pace/pace.min'
    },
    shim: {

    	'common': ['jquery'],
    	'bootstrap': ['jquery'],
    	'metismenu': ['jquery']
    	}
    });

require([
         'jquery',
         'common',
         'localise',
		 'globals',
		 'moment',
         'metismenu',
         'pace'

         ], function($,
        		 common,
        		 localise,
        		 globals,
		         moment) {

	var table;
	
	$(document).ready(function() {

		window.moment = moment;		// Make moment global for use by common.js
        setCustomLogs();
		setupUserProfile(true);
		localise.setlang();		// Localise HTML

		$("#side-menu").metisMenu()

		getLoggedInUser(undefined, false, true, undefined);
		
		table = $('#log_table').DataTable({
			 processing: true,
			 deferRender: true,
		     ajax: "/api/v1/log/dt",
		     columns: [
		                 { "data": "id" },
		                 { "data": "log_time" },
		                 { "data": "sName", "width": "200px"  },
		                 { "data": "userIdent" },
		                 { "data": "event" },
		                 { "data": "note" }
		             ],
		      order: [[ 0, "desc" ]],
		      columnDefs: [{
                  targets: [1],
                  render: function (data, type, full, meta) {
                      return localTime(data);
                  }
              }
		     ],
		});

        $('#log_table').find('td').css('white-space','initial').css('word-wrap', 'break-word');
		
		$('#m_refresh').click(function(e) {	// Add refresh action
			table.ajax.reload();
		}); 
		
	    table.on( 'init.dt', function () {
	    		
    		table.columns().flatten().each( function ( colIdx ) {
    			if(colIdx == 2 || colIdx == 3 || colIdx == 4) {
	    		    var select = $('<select class="form-control"/>')
	    		        .appendTo(
	    		            table.column(colIdx).footer()
	    		        )
	    		        .on( 'change', function () {
	    		            table
	    		                .column( colIdx )
	    		                .search( "^" + $(this).val() + "$", true, false, false )
	    		                .draw();
	    		        } );
	    		
	    		    select.append( $('<option value=""></option>') );
	    		    
			    	table
				        .column( colIdx )
				        .cache('search')
				        .sort()
				        .unique()
				        .each( function ( d ) {
				            select.append( $('<option value="'+d+'">'+d+'</option>') );
				        } );
    			}
	    		
    		});
	    	
	    } );

	   
			
		
	});
	


});

