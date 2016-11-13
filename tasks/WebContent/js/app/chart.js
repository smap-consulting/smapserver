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
         'app/charts/pie',
         'app/charts/line',
         'svgsave'], 
		function($, modernizr, lang, globals, d3, bar, pie, line, svgsave) {

	
	/*
	 * Available charts
	 * The system knows how to show these
	 */
	var avCharts = {
	                 bar: bar, 
	                 pie: pie,
	                 line: line
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
			chart,
			date_col = getCol(report.date_q, gTasks.cache.surveyConfig[gTasks.gSelectedSurveyIndex].columns),
			parseTimeDay = d3.timeParse("%Y-%m-%d"),
			parseTimeMonth = d3.timeParse("%Y-%m"),
			parseTimeYear = d3.timeParse("%Y");	
		
		var filtered = gTasks.cache.surveyConfig[gTasks.gSelectedSurveyIndex].filtered;
		
		for(i = 0; i < report.row.length; i++) {
			
			if(report.row[i].datatable) {
				for(j = 0; j < filtered.length; j++) {
			
					chart = filtered[j];
			
					data = d3.nest()
					  .key(function(d) { return d[chart.name]; })
					  .rollup(function(v) { return v[chart.cFn]; })
					  .entries(results);
					
					addChart("#c_" + chart.name, data, chart, i);
				}
			} else {
				for(j = 0; j < report.row[i].charts.length; j++) {
					
					chart = report.row[i].charts[j];
					
					if(chart.tSeries) {
						data = d3.nest()
						  .key(function(d) {
							  var 	dateArray,
							  		adjKey,
							  		adjKeyArray;
							  
							  if(d[report.date_q]) {
								  dateArray = d[report.date_q].split(" ");
								  if(dateArray.length > 0) {
									  adjKey = dateArray[0];
									  adjKeyArray = adjKey.split("-");
									  
									  // Default is day
									  if(chart.period === "month") {
										  adjKey = adjKeyArray[0] + "-" + adjKeyArray[1]; 
									  } else if(chart.period === "year") {
										  adjKey = adjKeyArray[0];
									  }
								  }
							  }
							 
							  return adjKey; 
						  })
						  .rollup(function(v) {return v[chart.fn]; })
						  .entries(results);
						
						console.log(data);
						
					} else {
						data = d3.nest()
						  .key(function(d) { return d[chart.name]; })
						  .rollup(function(v) { return v[chart.cFn]; })
						  .entries(results);
					}
					
					// Convert dates to date format
					if(chart.tSeries) {
						var parseTime = chart.period === "day" ? parseTimeDay : 
								chart.period === "month" ? parseTimeMonth :
								parseTimeYear;
						
						var dateRange = chart.period === "day" ? d3.timeDay : 
								chart.period === "month" ? d3.timeMonth :
									d3.timeYear;
						
						data.forEach(function(d) {
						      d.key = parseTime(d.key);
						});
						
						var dateValueMap = data.reduce(function(r, v) {
							r[v.key.toISOString()] = v.value;
								return r;
						}, {});
						
						var dateExtent = d3.extent(data.map(function(v)  {
							return v.key;
						}));
						
						var range = dateRange.range(
							    dateExtent[0], 
							    dateRange.offset(dateExtent[1], 1)
							  );
						
						var newData = [];
						range.forEach(function(date) {
							var dx = date.toISOString();
							if(!(dx in dateValueMap)) {
								newData.push({
									'key'  : date,
								    'value' : 0
								});
							} else {
								newData.push({
									'key'  : date,
								    'value' : dateValueMap[dx]
								});
							}
						});
						
						data = newData;
					}
					
					addChart("#c_" + chart.name, data, chart, i);
					
				}
			}
		}
	}
	
	/*
	 * Add a chart
	 */
	function addChart(chartId, data, chart, index) {
		
		// Get dynamic widths of container
		var widthContainer = $(chartId).width();
		var heightContainer = $(chartId).height();
		var view = "0 0 " + widthContainer + " " + heightContainer,
			margin,
			width,
			height;
		
		var config = globals.gCharts[chartId],
			init = false;
		
		if(typeof config === "undefined") {
			init = true;
			globals.gCharts[chartId] = {};
			config = globals.gCharts[chartId];
			config.index = index;
		} 
		
		margin = {top: 20, right: 20, bottom: 30, left: 40};
	    width = +widthContainer - margin.left - margin.right;
	    height = +heightContainer - margin.top - margin.bottom;
		
		if(init) {
			if(chart.chart_type === "pie") {
				config.svg = d3.select(chartId).append("svg")
				  .attr("preserveAspectRatio", "xMinYMin meet")
				  .attr("viewBox", view)
				  .classed("svg-content", true)
				  .append("g")
				  .attr("transform", "translate(" + width / 2 + "," + height / 2 + ")");
			} else {
				config.svg = d3.select(chartId).append("svg")
				  .attr("preserveAspectRatio", "xMinYMin meet")
				  .attr("viewBox", view)
				  .classed("svg-content", true);
			}
 
		}
		
		if(avCharts[chart.chart_type]) {
			if(init) {
				avCharts[chart.chart_type].add(chartId, chart, config, data, width, height, margin)
			} 
			avCharts[chart.chart_type].redraw(chartId, chart, config, data, width, height, margin);
		} else {
			alert("unknown chart type: " + chart.chart_type);
		}
		
	}
	
	/*
	 * Set the list of charts to show based on:
	 *     the report
	 *     the available data
	 */
	function setChartList() {
		
		/*
		 * Get the list of visible columns
		 */
		var columns = gTasks.cache.surveyConfig[gTasks.gSelectedSurveyIndex].columns,
			filtered = columns.filter(function(d) {
			   return d.include && !d.hide && d.name !== "prikey" && d.name !== "_start" && d.name !== "_end"; 
			}),
			i,
			def = report.row[1].def;	
		
		gTasks.cache.surveyConfig[gTasks.gSelectedSurveyIndex].filtered = filtered;	// cache
		
		for (i = 0; i < filtered.length; i++) {
			if(!filtered[i].cFn) {
				filtered[i].cFn = def.fn;
			}
			if(!filtered[i].cDom) {
				filtered[i].cDom = "c_" + filtered[i].name;
			}
			if(!filtered[i].chart_type) {
				filtered[i].chart_type = def.chart_type;
			}
		}
		
		/*
		 * Generate the HTML
		 * Start by creating rows of related charts
		 */
		var chartContent = d3.select("#chartcontent")
			.selectAll(".row")
			.data(report.row);
		
		var row = chartContent.enter()
			.append("div")
			.attr("class", "row")
			.attr("id", function(d) {return d.name});
		

		/*
		 * Create the charts for each row
		 */
		var chartRow,
			wrapper,
			title,
			content,
			data;
		
		for(i = 0; i < report.row.length; i++) {
			if(report.row[i].datatable) {
				data = filtered;
			} else {
				data = report.row[i].charts;
			}
			chartRow = d3.select("#" + report.row[i].name)
		    	.selectAll(".aChart")
		    	.data(data);
		
			/*
			 * New Charts
			 */
		    wrapper = chartRow.enter()
		    	.append("div").attr("class", function(d) {
		    		return "aChart col-lg-" + (d.width ? d.width : "6");
		    		})
		    		.attr("id", function(d) {return "c_" + d.name + "_ibox"})
		    	.append("div").attr("class", "ibox float-e-margins");
			
		    title = wrapper.append("div").attr("class", "ibox-title");
		    title.append("div").append("h5").text(function(d) {return d.humanName});		// Add title
		    addChartTools(title);
		    
		    content = wrapper.append("div").attr("class", "ibox-content");
		    content
		    	.append("div")
		    	.append("div").attr("class", "svg-container").attr("id", function(d) {return "c_" + d.name;})
		}
	    
	    setupIbox("#chartcontent");		// Add event listeners
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
	    		chart_type = $this.data("ctype"),
	    	    chart = "#" + $this.closest('.aChart').find(".svg-container").attr("id"),
	    	    ibox = chart + "_ibox",
	    	    name;
	    	
	    	var config = globals.gCharts[chart];
	    	filtered[config.index].chart_type = chart_type;
	    	config.svg.remove();
	    	globals.gCharts[chart] = undefined;
	    	
	    	refreshCharts();
	    })
	}
	
	/*
	 * Get the human name  column name
	 */
	function getCol(qname, columns) {
		var col = -1, 
			i;
		
		for(i = 0; i < columns.length; i++) {
			if(qname === columns[i].name) {
				col = i;
				break;
			}
		}
		return col;
	}

});