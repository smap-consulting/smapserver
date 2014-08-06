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

require.config({
    baseUrl: 'js/libs',
    locale: gUserLocale,
    paths: {
     	app: '../app',
     	i18n: '../../../../js/libs/i18n',
     	async: '../../../../js/libs/async',
     	localise: '../../../../js/app/localise',
    	jquery: [
    	       '//ajax.googleapis.com/ajax/libs/jquery/1.8.3/jquery.min',
    	       '../../../../js/libs/jquery-1.8.3.min'
    	       ],
    	jquery_ui: [
    	            '//ajax.googleapis.com/ajax/libs/jqueryui/1.10.3/jquery-ui.min',
    	            '../../../../js/libs/jquery-ui-1.10.3.custom.min'
    	            ],

    	modernizr: '../../../../js/libs/modernizr',
    	rmm: '../../../../js/libs/responsivemobilemenu',
    	common: '../../../../js/app/common',
    	globals: '../../../../js/app/globals',
    	tablesorter: '../../../../js/libs/tablesorter',
    	crf: '../../../../js/libs/commonReportFunctions',
    	googlemaps: 'http://maps.google.com/maps/api/js?v=3.6&amp;sensor=false',
    	openlayers: '../../../../js/libs/OpenLayers/OpenLayers',
    	lang_location: '../../../../js'
    },
    shim: {
    	'rmm': ['jquery'],
    	'jquery_ui': ['jquery'],
    	'jquery-ui-timepicker-addon': ['jquery_ui'],
    	'jquery.dataTables.min': ['jquery'],
    	'fp/flowplayer.min': ['jquery'],
    	'common': ['jquery'],
    	
    	'jqplot/jquery.jqplot.min': ['jquery_ui'],
    	'jqplot/plugins/jqplot.highlighter.min': ['jqplot/jquery.jqplot.min'],
    	'jqplot/plugins/jqplot.cursor.min': ['jqplot/jquery.jqplot.min'],
    	'jqplot/plugins/jqplot.dateAxisRenderer.min': ['jqplot/jquery.jqplot.min'],
    	'jqplot/plugins/jqplot.barRenderer.min': ['jqplot/jquery.jqplot.min'],
    	'jqplot/plugins/jqplot.categoryAxisRenderer.min': ['jqplot/jquery.jqplot.min'],
    	'jqplot/plugins/jqplot.canvasAxisLabelRenderer.min': ['jqplot/jquery.jqplot.min'],
    	'jqplot/plugins/jqplot.canvasAxisTickRenderer.min': ['jqplot/jquery.jqplot.min'],
    	'jqplot/plugins/jqplot.canvasTextRenderer.min': ['jqplot/jquery.jqplot.min'],
    	'jqplot/plugins/jqplot.enhancedLegendRenderer.min': ['jqplot/jquery.jqplot.min'],
    	
    	'app/jqplot_image': ['jquery'],
    	'app/map-functions': ['jquery'],
    	'app/map-ol': ['jquery', 'globals'],
    	'app/graph-functions': ['jquery'],
    	'app/graph-view2': ['jquery'],
    	'app/table-functions': ['jquery'],
    	'app/table-view': ['jquery'],
    	'app/media-view': ['jquery'],	
    	'app/survey_control': ['jquery_ui'],	
    	'app/plugins': ['jquery'],
    	'app/script': ['jquery_ui'],
    	'app/data': ['jquery'],
    	'app/reports': ['jquery_ui'],
    	'tablesorter': ['jquery'],
    	'crf': ['jquery']
    	
    	}
    });

require(['jquery', 'jquery_ui', 'modernizr', 'rmm', 'common', 'localise', 'globals',
         
         'jqplot/jquery.jqplot.min',
         'jqplot/plugins/jqplot.highlighter.min',
         'jqplot/plugins/jqplot.cursor.min',
         'jqplot/plugins/jqplot.dateAxisRenderer.min',
         'jqplot/plugins/jqplot.barRenderer.min',
         'jqplot/plugins/jqplot.categoryAxisRenderer.min',
         'jqplot/plugins/jqplot.canvasAxisLabelRenderer.min',
         'jqplot/plugins/jqplot.canvasAxisTickRenderer.min',
         'jqplot/plugins/jqplot.canvasTextRenderer.min',
         'jqplot/plugins/jqplot.enhancedLegendRenderer.min',
         
         'jquery-ui-timepicker-addon',
         'fp/flowplayer.min',
         'jquery.dataTables.min',
         
         'app/jqplot_image',
         'app/map-functions',
         'app/map-ol',
         'app/graph-functions',
         'app/graph-view2',
         'app/table-functions',
         'app/table-view',
         'app/media-view',
         'app/survey_control',
         'app/plugins',
         'app/script',
         'app/data',
         'app/reports',
         'app/panels',
         
         'tablesorter',
         'crf',
         'async!googlemaps',
         'openlayers'
         
         ], function($, jquery_ui, modernizr, rmm, common, localise, globals) {});

