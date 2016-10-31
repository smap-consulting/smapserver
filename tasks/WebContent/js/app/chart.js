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

	
	/*
	 * Available charts
	 * The system knows how to show these
	 */
	var avCharts = {
	                 bar: bar, 
	                 pie: pie
	                 };
	
	var report = undefined;
	
	return {	
		setReport: setReport,
		setChartList: setChartList,
		refreshCharts: refreshCharts,

	};
	
	function setReport(r) {
		report = r;
	}
	
	

	/*
	 * Show a report
	 */
	function refreshCharts(results) {
		console.log("#############Refreshing chart");
		console.log(results);
		
		var i,
			data,
			chart;
		
		var filtered = gTasks.cache.surveyConfig[gTasks.gSelectedSurveyIndex].filtered;
		
		for(i = 0; i < filtered.length; i++) {
			
			chart = filtered[i];
			
			data = d3.nest()
			  .key(function(d) { return d[chart.name]; })
			  .rollup(function(v) { return v[chart.cFn]; })
			  .entries(results);
			
			addChart("#c_" + chart.name, data, chart.cType);
		}
		
		/*
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
		*/
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
		
		if(avCharts[type]) {
			if(init) {
				avCharts[type].add(chart, config, data, width, height, margin)
			} 
			avCharts[type].redraw(chart, config, data, width, height, margin);
		} else {
			alert("unknown chart type: " + type);
		}
		
	}
	
	/*
	 * Set the list of charts to show baed on:
	 *     the report
	 *     the available data
	 */
	function setChartList() {
		
		var columns = gTasks.cache.surveyConfig[gTasks.gSelectedSurveyIndex].columns,
			filtered = columns.filter(function(d) {
			   return d.include && !d.hide && d.name !== "prikey" && d.name !== "_start" && d.name !== "_end"; 
			}),
			i,
			def = report.def;
		
		gTasks.cache.surveyConfig[gTasks.gSelectedSurveyIndex].filtered = filtered;	// cache
		
		for (i = 0; i < filtered.length; i++) {
			if(!filtered[i].cFn) {
				filtered[i].cFn = def.fn;
			}
			if(!filtered[i].cDom) {
				filtered[i].cDom = "c_" + filtered[i].name;
			}
			if(!filtered[i].cType) {
				filtered[i].cType = def.cType;
			}
		}
		
		/*
		 * Generate the HTML
		 */
		var chartRow = d3.select("#chartrow")
	    	.selectAll("aChart")
	    	.data(filtered);
		
 
           /*
          
                <div>
                    <div class="svg-container" id="chart1"></div>
                </div>
       
			*/
    
		// New charts
	    var wrapper = chartRow.enter()
	    	.append("div").attr("class", "aChart col-lg-6")
	    	.append("div").attr("class", "ibox float-e-margins");
		
	    wrapper.append("div").append("h5").text(function(d) {return d.humanName});
	    wrapper.append("div").attr("class", "ibox-content")
	    	.append("div")
	    	.append("div").attr("class", "svg-container").attr("id", function(d) {return "c_" + d.name;})
	}

});