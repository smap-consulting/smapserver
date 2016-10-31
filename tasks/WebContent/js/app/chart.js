/*
This file is part of SMAP.

SMAP is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.
uSMAP is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with SMAP.  If not, see <http://www.gnu.org/licenses/>.

*/

/*
 * Functions for manipulating a question in the editor
 */

"use strict";

define([
         'jquery',
         'modernizr',
         'localise',
         'globals',
         'd3',
         'app/charts/bar',
         'app/charts/pie'], 
		function($, modernizr, lang, globals, d3, bar, pie) {

	
	var chartList = {
	                 bar: bar, 
	                 pie: pie
	                 };
	
	return {	
		init: init,
		refreshCharts: refreshCharts
	};
	
	function init() {
		
	}
	

	/*
	 * Show a report
	 */
	function refreshCharts(results) {
		console.log("#############Refreshing chart");
		console.log(results);
		
		var countByRegion = d3.nest()
		  .key(function(d) { return d.region; })
		  .rollup(function(v) { return v.length; })
		  .entries(results);
		
		
		var countByGender = d3.nest()
		  .key(function(d) { return d.gender; })
		  .rollup(function(v) { return v.length; })
		  .entries(results);
		
		var avgDurDevice = d3.nest()
		  .key(function(d) { return d.User; })
		  .rollup(function(v) { 
			  return d3.mean(v, function(d) {
				  var diff = timeDifference(d._start, d._end); 
				  if(diff) {
					  diff = diff._milliseconds / 1000;
				  }
				  return  diff;
		  	  }); 
		  })	  
		  .entries(results);
		
		console.log("Duration by device: " + JSON.stringify(avgDurDevice));
		addChart("#chart1", countByRegion, "bar");
		//addChart("#chart1", countByGender, "bar");
		addChart("#chart2", countByRegion, "pie");
	}
	
	/*
	 * Add a chart
	 */
	function addChart(chart, data, type) {
		
		// Get dynamic widths of container
		var widthContainer = $(chart).width();
		var heightContainer = $(chart).height();
		var view = "0 0 " + widthContainer + " " + heightContainer,
			margin,
			width,
			height;
		
		var config = globals.gCharts[chart],
			init = false;
		
		if(typeof config === "undefined") {
			init = true;
			globals.gCharts[chart] = {};
			config = globals.gCharts[chart];
		} 
		
		margin = {top: 20, right: 20, bottom: 30, left: 40};
	    width = +widthContainer - margin.left - margin.right;
	    height = +heightContainer - margin.top - margin.bottom;
		
		if(init) {
			if(type === "pie") {
				config.svg = d3.select(chart).append("svg")
				  .attr("preserveAspectRatio", "xMinYMin meet")
				  .attr("viewBox", view)
				  .classed("svg-content", true)
				  .append("g")
				  .attr("transform", "translate(" + width / 2 + "," + height / 2 + ")");
			} else {
				config.svg = d3.select(chart).append("svg")
				  .attr("preserveAspectRatio", "xMinYMin meet")
				  .attr("viewBox", view)
				  .classed("svg-content", true);
			}
			
		
			 
		}
		
		if(chartList[type]) {
			if(init) {
				chartList[type].add(chart, config, data, width, height, margin)
			} 
			chartList[type].redraw(chart, config, data, width, height, margin);
		} else {
			alert("unknown chart type: " + type);
		}
		
	}

});