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
 * Entry point for report list page
 */

requirejs.config({
    baseUrl: '/fieldAnalysis/js/libs',
    paths: {
     	app: '../app',
    	i18n: '../../../../js/libs/i18n',
     	async: '../../../../js/libs/async',
    	jquery: [
    	       '//ajax.googleapis.com/ajax/libs/jquery/1.8.3/jquery.min',
    	       '../../../../js/libs/jquery-1.8.3.min'
    	       ],
	    jquery_ui: [
	    	   '//ajax.googleapis.com/ajax/libs/jqueryui/1.10.3/jquery-ui.min',
	    	   '../../../../js/libs/jquery-ui-1.10.3.custom.min'
	    	   ],
    	modernizr: '../../../../js/libs/modernizr',
    	localise: '../../../../js/app/localise',
    	rmm: '../../../../js/libs/responsivemobilemenu',
    	crf: '../../../../js/libs/commonReportFunctions',
    	openlayers: '../../../../js/libs/OpenLayers/OpenLayers',
    	lang_location: '../../../../js'
    },
    shim: {
    	'rmm': ['jquery'],
    	'jquery_ui': ['jquery'],
    	
       	'jqplot/jquery.jqplot.min': ['jquery_ui'],
    	'jqplot/plugins/jqplot.highlighter.min': ['jqplot/jquery.jqplot.min'],
    	'jqplot/plugins/jqplot.cursor.min': ['jqplot/jquery.jqplot.min'],
    	'jqplot/plugins/jqplot.dateAxisRenderer.min': ['jqplot/jquery.jqplot.min'],
    	'jqplot/plugins/jqplot.barRenderer.min': ['jqplot/jquery.jqplot.min'],
    	'jqplot/plugins/jqplot.categoryAxisRenderer.min': ['jqplot/jquery.jqplot.min'],
    	'jqplot/plugins/jqplot.canvasAxisLabelRenderer.min': ['jqplot/jquery.jqplot.min'],
    	'jqplot/plugins/jqplot.canvasAxisTickRenderer.min': ['jqplot/jquery.jqplot.min'],
    	'jqplot/plugins/jqplot.canvasTextRenderer.min': ['jqplot/jquery.jqplot.min'],
    	'jqplot/plugins/jqplot.enhancedLegendRenderer.min': ['jqplot/jquery.jqplot.min']
    	}
    });


require([
         'jquery',  
         'jquery_ui',
         'modernizr', 
         'rmm', 
         'crf', 
         'localise', 
         'app/graph-functions',
         'app/graph-reports',
         
         'jqplot/jquery.jqplot.min',
         'jqplot/plugins/jqplot.highlighter.min',
         'jqplot/plugins/jqplot.cursor.min',
         'jqplot/plugins/jqplot.dateAxisRenderer.min',
         'jqplot/plugins/jqplot.barRenderer.min',
         'jqplot/plugins/jqplot.categoryAxisRenderer.min',
         'jqplot/plugins/jqplot.canvasAxisLabelRenderer.min',
         'jqplot/plugins/jqplot.canvasAxisTickRenderer.min',
         'jqplot/plugins/jqplot.canvasTextRenderer.min',
         'jqplot/plugins/jqplot.enhancedLegendRenderer.min'
         
         ], function($, jquery_ui, modernizr, rmm, crf, localise, 
        		 graph_functions, graph_reports) {
	
	var data_source = $("#data_source").text();
	getData(data_source);
});
