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
	function refreshCharts() {
		console.log("#############Refreshing chart");
		
		var results = globals.gMainTable.rows({
	    	order:  'current',  // 'current', 'applied', 'index',  'original'
	    	page:   'all',      // 'all',     'current'
	    	search: 'applied',     // 'none',    'applied', 'removed'
		}).data();
		
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
			
			addChart("#c_" + chart.name, data, chart.cType, i);
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
	function addChart(chart, data, type, index) {
		
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
			config.index = index;
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
	 * Set the list of charts to show based on:
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
		 * New Charts
		 */
	    var wrapper = chartRow.enter()
	    	.append("div").attr("class", "aChart col-lg-6")
	    		.attr("id", function(d) {return "c_" + d.name + "_ibox"})
	    	.append("div").attr("class", "ibox float-e-margins");
		
	    var title = wrapper.append("div").attr("class", "ibox-title");
	    title.append("div").append("h5").text(function(d) {return d.humanName});		// Add title
	    addChartTools(title);
	    
	    var content = wrapper.append("div").attr("class", "ibox-content");
	    content
	    	.append("div")
	    	.append("div").attr("class", "svg-container").attr("id", function(d) {return "c_" + d.name;})
	    
	    setupIbox("#chartrow");		// Add event listeners
	}
	
	/*
	 * Add the controls to an chart ibox
	 */
	function addChartTools(title) {
		
		var tools = title.append("div").attr("class", "ibox-tools"),
			i;
		
		tools.append("a").attr("class", "collapse-link")
			.append("i").attr("class", "fa fa-chevron-up");
	 
		tools.append("a").attr("class", "dropdown-toggle")
			.attr("data-toggle", "dropdown")
			.attr("href", "#")
			.append("i").attr("class", "fa fa-wrench");
		
		var toolList = tools.append("ul")
			.attr("class", "dropdown-menu dropdown-user");
		
		for (var key in avCharts) {
		    if (avCharts.hasOwnProperty(key)) {
		    	toolList.append("li").append("a")
		    		.attr("href", "#")
		    		.attr("class", "chart-type")
		    		.attr("data-ctype", key)
		    		.text(localise.set[key]);
		    }
		}
		//tools.append("a").attr("class", "close-link")
		//	.append("i").attr("class", "fa fa-times");

	}
	
	function setupIbox(element) {
	    // Collapse ibox function
	    $('.collapse-link', element).click(function () {
	        var ibox = $(this).closest('div.ibox');
	        var button = $(this).find('i');
	        var content = ibox.find('div.ibox-content');
	        content.slideToggle(200);
	        button.toggleClass('fa-chevron-up').toggleClass('fa-chevron-down');
	        ibox.toggleClass('').toggleClass('border-bottom');
	        setTimeout(function () {
	            ibox.resize();
	            ibox.find('[id^=map-]').resize();
	        }, 50);
	    });

	    // Close ibox function
	    $('.close-link', element).click(function () {
	        var content = $(this).closest('div.ibox');
	        content.remove();
	    });

	    // Fullscreen ibox function
	    $('.fullscreen-link',element).click(function () {
	        var ibox = $(this).closest('div.ibox');
	        var button = $(this).find('i');
	        $('body').toggleClass('fullscreen-ibox-mode');
	        button.toggleClass('fa-expand').toggleClass('fa-compress');
	        ibox.toggleClass('fullscreen');
	        setTimeout(function () {
	            $(window).trigger('resize');
	        }, 100);
	    });	
	    
	    $('.chart-type').off().click(function(){
	    	var $this = $(this),
	    		filtered = gTasks.cache.surveyConfig[gTasks.gSelectedSurveyIndex].filtered,
	    		cType = $this.data("ctype"),
	    	    chart = "#" + $this.closest('.aChart').find(".svg-container").attr("id"),
	    	    ibox = chart + "_ibox",
	    	    name;
	    	
	    	var config = globals.gCharts[chart];
	    	filtered[config.index].cType = cType;
	    	config.svg.remove();
	    	globals.gCharts[chart] = undefined;
	    	
	    	refreshCharts();
	    })
	}

});