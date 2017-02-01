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
         'app/charts/wordcloud',
         'app/charts/groupedBar',
         'app/charts/map',
         'svgsave'], 
		function($, modernizr, lang, globals, d3, bar, pie, line, wordcloud, groupedBar, map, svgsave) {

	
	/*
	 * Available charts
	 * The system knows how to show these
	 */
	var avCharts = {
	                 bar: bar, 
	                 pie: pie,
	                // line: line,
	                 wordcloud: wordcloud,
	                 groupedBar: groupedBar,
	                 map: map
	                 };
		
	var gCurrentReport = undefined;
	var gEdConfig,			// Temporary objects used when editing a chart
		gEdChart,
		gIsNewChart,
		gEdFilteredChart,
		gChartId;
	
	return {
		init: init,
		setReport: setReport,
		setChartList: setChartList,
		refreshCharts: refreshCharts,
		addChartTypeSelect: addChartTypeSelect,
		addNewChart: addNewChart

	};
	
	function init() {
		localise.setlang();
		$('#editWidgetSave').off().click(function(){

			var width = $('#ew_width').val(),
				reset = false,
				filtered = gTasks.cache.surveyConfig[gTasks.gSelectedSurveyIndex].filtered;
			
			//if(width != gEdChart.width) {
			//	reset = true;
			//}
			gEdChart.qIdx = $('#ew_question').val();
			if(gEdChart.qIdx) {		// Question specified
				gEdChart.name = filtered[gEdChart.qIdx].name;
				gEdChart.type = filtered[gEdChart.qIdx].type;
			}
			gEdChart.fn = $('#ew_fn').val();
			gEdChart.humanName = $('#ew_title').val();
			gEdChart.chart_type = $('#ew_chart_type').val();
			gEdChart.width = $('#ew_width').val();
			gEdChart.group = $('#ew_group').val();
			if(gEdChart.time_interval) {
				gEdChart.groups[0].q = $("#ew_date1").val();
				gEdChart.groups[0].label = $("#ew_date1 option[value='" + gEdChart.groups[0].q + "']").text();
				
				gEdChart.groups[1].q = $("#ew_date2").val();
				gEdChart.groups[1].label = $("#ew_date2 option[value='" + gEdChart.groups[1].q + "']").text();
			}
			if(gEdChart.tSeries) {
				var period = $('#ew_period').val();
				if(period != gEdChart.period) {
					reset = true;
				}
				gEdChart.period = $('#ew_period').val();
			}
			// if(gEdConfig.fromDT) {
			//	gEdFilteredChart.width = gEdChart.width;
			//	saveConfig();
			//} else {
			if(gIsNewChart) {
				gCurrentReport.row[0].charts.push(gEdChart);
			}
			saveReport(gCurrentReport);
			//}

	    	//gEdConfig.svg.remove();
	    	
	    	//if(reset) {
	    		setChartList();
	    		refreshCharts();
	    	//} else {
	    	//	refreshCharts();
	    	//}
		});
	}
	
	function setReport(r) {
		gCurrentReport = r;
	}
	
	

	/*
	 * Show a report
	 */
	function refreshCharts() {
		
		var results = globals.gMainTable.rows({
	    	order:  'current',  // 'current', 'applied', 'index',  'original'
	    	page:   'all',      // 'all',     'current'
	    	search: 'applied',     // 'none',    'applied', 'removed'
		}).data();
		
		var i,
			data,
			chart,
			date_col = getCol(gCurrentReport.date_q, gTasks.cache.surveyConfig[gTasks.gSelectedSurveyIndex].columns),
			filtered = gTasks.cache.surveyConfig[gTasks.gSelectedSurveyIndex].filtered,
			index = 0,
			dataLength = results.count();
		
		for(i = 0; i < gCurrentReport.row.length; i++) {
			
			for(j = 0; j < gCurrentReport.row[i].charts.length; j++) {
				
				chart = gCurrentReport.row[i].charts[j];
				if(chart.groups) {
					chart.groupLabels = chart.groups.map(function(e) { return e.label; });
				}
				data = processData(results, chart, dataLength);
				
				addChart("#c_" + chart.name, data, chart, i, j, false);		

			}
		}
	}
	
	/*
	 * Process the data according to chart type
	 */
	function processData(results, chart, dataLength) {
		
		var allData = [],
			data,
			parseTimeSec = d3.timeParse("%Y-%m-%d %H:%M:%S%Z"),
			parseTimeDay = d3.timeParse("%Y-%m-%d"),
			formatTimeDay = d3.timeFormat("%Y-%m-%d"),
			parseTimeMonth = d3.timeParse("%Y-%m"),
			formatTimeMonth = d3.timeFormat("%Y-%m"),
			parseTimeYear = d3.timeParse("%Y"),
			formatTimeYear = d3.timeFormat("%Y"),
			i,
			dateValueMap = [],
			dateExtent,
			parseTime,
			formatTime,
			dateRange,
			d3Fn;
		
		if(chart.fn === "count" || chart.fn === "percent") {
			d3Fn = "length";
		} else {
			d3Fn = chart.fn;
		}
		
		if(chart.fn !== "percent") {
			dataLength = 1;			// Don't divide by total number of records unless percent
		}
		
 		if(chart.tSeries) {
			parseTime = chart.period === "day" ? parseTimeDay : 
				chart.period === "month" ? parseTimeMonth :
				parseTimeYear;
			formatTime = chart.period === "day" ? formatTimeDay : 
				chart.period === "month" ? formatTimeMonth :
				formatTimeYear;
			dateRange = chart.period === "day" ? d3.timeDay : 
				chart.period === "month" ? d3.timeMonth :
					d3.timeYear;
		} else {
			parseTime = parseTimeSec;
		}
		
		if(chart.tSeries) {
			for(i = 0; i < chart.groups.length; i++) {
				
				allData.push(d3.nest()
				  .key(function(d) {
					  var 	dateArray,
					  		adjKey,
					  		adjKeyArray;
					  
					  if(d[chart.groups[i].q]) {
						  dateArray = d[chart.groups[i].q].trim().split(" ");
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
				  .rollup(function(v) {return v[d3Fn]; })
				  .entries(results));
			}
			
			/*
			 * Remove undefined dates
			 */
			for(i = 0; i < allData.length; i++) {
				allData[i] = allData[i].filter(function(v){
					return v.key !== "undefined";
				})
			}
			
			// Add missing dates
			// Based on http://stackoverflow.com/questions/18835053/d3-js-calculate-width-of-bars-in-time-scale-with-changing-range
			if(chart.chart_type === "groupedBar" || chart.chart_type === "bar") {
				
				for(i = 0; i < allData.length; i++) {
					allData[i].forEach(function(d) {
					      d.key = parseTime(d.key);
					});
					
					dateValueMap[i] = allData[i].reduce(function(r, v) {
						r[v.key.toISOString()] = v.value;
							return r;
					}, {});
					
					
				}
				
				// Get the extent from all the dates in the time series
				var allDates = [];
				for(i = 0; i < allData.length; i++) {
					allDates = allDates.concat(allData[i])
				}
				
				dateExtent = d3.extent(allDates.map(function(v)  {
					return v.key;
				}));
				
				
				var range = dateRange.range(
					    dateExtent[0], 
					    dateRange.offset(dateExtent[1], 1)
					  );
				
				var newData = [];
				range.forEach(function(date) {
					var dx = date.toISOString();
					var newDataItem = {
							'key': formatTime(date),
							'pr': [] 
						},
						newResults;
					
					for(i = 0; i < allData.length; i++) {
						newResults = {};
						newResults.key = chart.groups[i].label;
						if(!(dx in dateValueMap[i])) {
							newResults.value = 0;
						} else {
							newResults.value = dateValueMap[i][dx];
						}
						newDataItem.pr.push(newResults);
					}
					newData.push(newDataItem);
				});
				
				data = newData;
			}
			
		} else if (chart.chart_type === "wordcloud") {
			/*
			 * Generate data for a word cloud
			 */
			//var text_string = "Of course that’s your contention. You’re a first year grad student. You just got finished readin’ some Marxian historian, Pete Garrison probably. You’re gonna be convinced of that ’til next month when you get to James Lemon and then you’re gonna be talkin’ about how the economies of Virginia and Pennsylvania were entrepreneurial and capitalist way back in 1740. That’s gonna last until next year. You’re gonna be in here regurgitating Gordon Wood, talkin’ about, you know, the Pre-Revolutionary utopia and the capital-forming effects of military mobilization… ‘Wood drastically underestimates the impact of social distinctions predicated upon wealth, especially inherited wealth.’ You got that from Vickers, Work in Essex County, page 98, right? Yeah, I read that, too. Were you gonna plagiarize the whole thing for us? Do you have any thoughts of your own on this matter? Or do you, is that your thing? You come into a bar. You read some obscure passage and then pretend, you pawn it off as your own, as your own idea just to impress some girls and embarrass my friend? See, the sad thing about a guy like you is in 50 years, you’re gonna start doin’ some thinkin’ on your own and you’re gonna come up with the fact that there are two certainties in life. One: don’t do that. And two: you dropped a hundred and fifty grand on a fuckin’ education you coulda got for a dollar fifty in late charges at the public library.";
		    var common = "poop,i,me,my,myself,we,us,our,ours,ourselves,you,your,yours,yourself,yourselves,he,him,his,himself,she,her,hers,herself,it,its,itself,they,them,their,theirs,themselves,what,which,who,whom,whose,this,that,these,those,am,is,are,was,were,be,been,being,have,has,had,having,do,does,did,doing,will,would,should,can,could,ought,i'm,you're,he's,she's,it's,we're,they're,i've,you've,we've,they've,i'd,you'd,he'd,she'd,we'd,they'd,i'll,you'll,he'll,she'll,we'll,they'll,isn't,aren't,wasn't,weren't,hasn't,haven't,hadn't,doesn't,don't,didn't,won't,wouldn't,shan't,shouldn't,can't,cannot,couldn't,mustn't,let's,that's,who's,what's,here's,there's,when's,where's,why's,how's,a,an,the,and,but,if,or,because,as,until,while,of,at,by,for,with,about,against,between,into,through,during,before,after,above,below,to,from,up,upon,down,in,out,on,off,over,under,again,further,then,once,here,there,when,where,why,how,all,any,both,each,few,more,most,other,some,such,no,nor,not,only,own,same,so,than,too,very,say,says,said,shall";

		    var textArray = results.map(function(d) { return d[chart.name]; });
		   
		    var data = {};
		    for(i = 0; i < textArray.length; i++) {
			    var words = textArray[i].split(/[ '\-\(\)\*":;\[\]|{},.!?]+/);
			    if (words.length == 1){
			    	if (data[words[0]]){
		        		data[words[0]]++;
		            } else {
		            	data[words[0]] = 1;
		            }
			    } else {
			    	words.forEach(function(word){
				        var word = word.toLowerCase();
				        if (word != "" && common.indexOf(word)==-1 && word.length>1){
				        	if (data[word]){
				        		data[word]++;
				            } else {
				            	data[word] = 1;
				            }
				        }
			    	})
			    }
		    }
		} else if (chart.type === "select") {   
			var i, j,
				data = [],
				dc;
			for(i = 0; i < chart.choices.length; i++) {
				dc = d3.nest()
				  .key(function(d) { return d[chart.choices[i]]; })
				  .rollup(function(v) { return v[d3Fn]; })
				  .entries(results);
				
				for(j = 0; j < dc.length; j++) {
					var choiceName = chart.choices[i].split(" - ");
					if(dc[j].key == "1") {
						data.push({
							key: choiceName[1],
							value: dc[j].value
						});
						break;
					}
				}
			}

		} else if (chart.fn === "avgdurn") { 
			var i, j,
				data = [],
				dc,
				maxValue;
			
			data = d3.nest()
			  .key(function(d) { return d[chart.group]; })
			  .rollup(function(v) { return d3.mean(v, function(d) {
				  	if(d[chart.groups[0].q] && d[chart.groups[1].q]) {	
				  		return d3.timeSecond.count(parseTime(d[chart.groups[0].q]), parseTime(d[chart.groups[1].q]));
				  	} else {
				  		return 0;
				  	}
				  });   	
			  })
			  .entries(results);
			
			maxValue = d3.max(data, function(d) { return +d.value; });
			console.log("Max value: " + maxValue);
			console.log(data);
			if(maxValue < 120) {
				chart.scale = "seconds";
			} else if(maxValue >= 120 && maxValue < 7200) {	// Between 2 minutes and 2 hours
				data.forEach(function(d) {
				      d.value = d.value / 60;
				});
				chart.scale = "minutes";
			} else {
				data.forEach(function(d) {
				      d.value = d.value / 3600;
				});
				chart.scale = "hours";
			}

		} else {
			data = d3.nest()
			  .key(function(d) { return d[chart.name]; })
			  .rollup(function(v) { return v[d3Fn] / dataLength; })
			  .entries(results);
			
			
		}
		
		return data;
	}
	
	/*
	 * Add a chart
	 */
	function addChart(chartId, data, chart, rowIndex, index, fromDT) {
		
		// Get dynamic widths of container
		var widthContainer = $(chartId).width();
		var heightContainer = $(chartId).height();
		var view = "0 0 " + widthContainer + " " + heightContainer;
		
		if(widthContainer > 0 && heightContainer > 0) {
			var config = globals.gCharts[chartId],
				init = false;
			
			if(typeof config === "undefined") {
				globals.gCharts[chartId] = {};
				globals.gCharts[chartId].rowIndex = rowIndex;
				globals.gCharts[chartId].index = index;
				config = globals.gCharts[chartId];
				init = true;
			} 
			
			if(avCharts[chart.chart_type]) {
				if(init || chart.chart_type === "pie" || chart.chart_type === "wordcloud") {	// Pie charts tricky to update, wordcloud not implemented update yet
					if(chart.chart_type === "map") {
						d3.select(chartId).append("div")
						.attr("id", chartId + '_map')
						.classed("dashboard_map", true);
					} else {
						if(config.svg) {
							config.svg.remove();
						}
						config.svg = d3.select(chartId).append("svg")
						  .attr("preserveAspectRatio", "xMinYMin meet")
						  .attr("viewBox", view)
						  .classed("svg-content", true);
					}
					avCharts[chart.chart_type].add(chartId, chart, config, data, widthContainer, heightContainer);
				} 
				if(chart.chart_type !== "pie" && chart.chart_type !== "wordcloud") {
					avCharts[chart.chart_type].redraw(chartId, chart, config, data, widthContainer, heightContainer);
				}
			} else {
				console.log("unknown chart type: " + chart.chart_type);
			}
		} else {
			alert("Could not find html for " + chart.humanName);
		}
		
	}
	
	/*
	 * Set the list of charts to show based on:
	 *     the report
	 *     the available data  - TODO Remove its all report based now
	 */
	function setChartList() {
		
		/*
		 * Get the list of visible columns
		 */
		
		var columns = gTasks.cache.surveyConfig[gTasks.gSelectedSurveyIndex].columns,
			h = [],
			idx = -1,
			hGrp = [],
			idxGrp = -1,
			hQ = [],
			idxQ = -1;
		
		
		/*
		 * Generate the HTML
		 * Start by creating rows of related charts
		 */
		$("#chartcontent").empty();
		var chartContent = d3.select("#chartcontent")
			.selectAll(".row")
			.data(gCurrentReport.row);
		
		var row = chartContent.enter()
			.append("div")
			.attr("class", "row")
			.attr("id", function(d) {return d.name});
		

		globals.gCharts = {};		// Force reinitialise of charts
		
		/*
		 * Create the charts for each row
		 */
		var chartRow,
			wrapper,
			title,
			content,
			data,
			i, j;
		
		for(i = 0; i < gCurrentReport.row.length; i++) {
			
			data = gCurrentReport.row[i].charts;
			
			chartRow = d3.select("#" + gCurrentReport.row[i].name)
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
		    title.append("div").append("h5").text(function(d) {
			    	if(d.tSeries) {
			    		return localise.set["d_c_" + d.period];
			    	} else {
			    		return d.humanName;
			    	}
		    	});		// Add title
		    addChartTools(title);
		    
		    content = wrapper.append("div").attr("class", "ibox-content");
		    content
		    	.append("div")
		    	.append("div").attr("class", "svg-container").attr("id", function(d) {return "c_" + d.name;})
		}
	    
	    setupIbox("#chartcontent");		// Add event listeners
	    
	    /*
	     * Filter the questions that the user is allowed to select
	     */   
	    var filtered = [],
			filtered_prelim = columns.filter(function(d) {
			   return d.include && 
			   		!d.hide && 
			   		d.name !== "prikey" && 
			   		d.name !== "_upload_time" && 
			   		d.name !== "_start" && 
			   		d.name !== "_end" &&
			   		d.name !== "instancename" && 
			   		d.type !== "dateTime" &&
			   		d.type !== "time" &&
			   		d.type !== "date" &&
			   		d.type !== "image" && d.type !== "video" && d.type !== "audio"; 
			});
	     
	    // Merge choices from select multiple questions
		var select_questions = {};
		for(i = 0; i < filtered_prelim.length; i++) {
			if(filtered_prelim[i].type === "select") {
				var n = filtered_prelim[i].humanName.split(" - ");
				if(n.length > 1) {
					
					if(!select_questions[n[0]]) {		// New choice
						
						filtered_prelim[i].select_name = n[0];
						filtered_prelim[i].choices = [];
						filtered_prelim[i].choices.push(filtered_prelim[i].humanName);
						
						select_questions[n[0]] = filtered_prelim[i];
						filtered.push(filtered_prelim[i]);
					} else {
						var f = select_questions[n[0]];
						f.choices.push(filtered_prelim[i].humanName);
					}
				}
				
				
			} else {
				filtered.push(filtered_prelim[i]);
			}
		}
		
		for (i = 0; i < filtered.length; i++) {
			if(!filtered[i].fn) {
				filtered[i].fn = "length";
			}
			if(!filtered[i].cDom) {
				filtered[i].cDom = "c_" + filtered[i].name;
			}
		}
		gTasks.cache.surveyConfig[gTasks.gSelectedSurveyIndex].filtered = filtered;	// cache
		
		/*
		 * Add question select options
		 */
		for(i = 0; i < filtered.length; i++) {
			hQ[++idxQ] = '<option value="';
			hQ[++idxQ] = i;
			hQ[++idxQ] = '">';
			hQ[++idxQ] = filtered[i].humanName;
			hQ[++idxQ] = '</option>';
		}
		$('.question').empty().append(hQ.join(''));
		
		/*
		 * Add date question select options
		 * Add group questions
		 */
	    hGrp[++idxGrp] = h[++idx] = '<option value="none">';
	    hGrp[++idxGrp] = h[++idx] = localise.set["c_none"];
	    hGrp[++idxGrp] = h[++idx] = '</option>';
		for(i = 0; i < columns.length; i++) {
			if(columns[i].type === "date" || columns[i].type === "dateTime") {
				h[++idx] = '<option value="';
				h[++idx] = columns[i].name;
				h[++idx] = '">';
				h[++idx] = columns[i].humanName;
				h[++idx] = '</option>';
			} else {
				hGrp[++idxGrp] = '<option value="';
				hGrp[++idxGrp] = columns[i].name;
				hGrp[++idxGrp] = '">';
				hGrp[++idxGrp] = columns[i].humanName;
				hGrp[++idxGrp] = '</option>';
			}
		}
		$('.date_question').empty().append(h.join(''));
		$('.group_question').empty().append(hGrp.join(''));
	}
	
	/*
	 * Add the controls to an chart ibox
	 */
	function addChartTools(title) {
		
		var tools = title.append("div").attr("class", "ibox-tools"),
			i;
		
		tools.append("a").attr("class", "widget-delete fa-widget-edit")
			.attr("href", "#")
			.append("i").attr("class", "fa fa-trash-o");
		
		tools.append("a").attr("class", "widget-edit")
			.attr("href", "#")
			.append("i").attr("class", "fa fa-edit fa-widget-edit");
		
		tools.append("a").attr("class", "close-link widget-close")
			.append("i").attr("class", "fa fa-close fa-widget-close");

	}
	
	function setupIbox(element) {
	    
		var h = [],
			idx = -1,
			key,
			keylangid;
		
	    // Close ibox function
	    $('.close-link', element).click(function () {
	        var content = $(this).closest('div.ibox');
	        content.remove();
	    });
  
	    $('.widget-delete').off().click(function() {
	    	var $this = $(this);
	    	
	    	gChartId = "#" + $this.closest('.aChart').find(".svg-container").attr("id");
	    	gEdConfig = globals.gCharts[gChartId];
	    	
	    	gCurrentReport.row[gEdConfig.rowIndex].charts.splice(gEdConfig.index, 1);
			saveReport(gCurrentReport);
    		setChartList();
    		refreshCharts();
	    	console.log("delete");
	    	
	    });
	    
	    $('.widget-edit').off().click(function() {
	    	
	    	var $this = $(this),
	    		chart = d3.select($this.closest('.aChart')),
	    		filtered = gTasks.cache.surveyConfig[gTasks.gSelectedSurveyIndex].filtered,
	    		chart_type = $this.data("ctype");
	    	
	    	gChartId = "#" + $this.closest('.aChart').find(".svg-container").attr("id");
	    	gEdConfig = globals.gCharts[gChartId];
	    	gIsNewChart = false;
	    	
	    	if(gEdConfig.fromDT) {
	    		var fullIndex = getFullIndex(filtered[gEdConfig.index].name);
	    		gEdChart = gTasks.cache.surveyConfig[gTasks.gSelectedSurveyIndex].columns[fullIndex];
	    		gEdFilteredChart = filtered[gEdConfig.index];
	    	} else {
	    		// Custom report
	    		gEdChart = gCurrentReport.row[gEdConfig.rowIndex].charts[gEdConfig.index];
	    	}
	    	
	    	/*
	    	 * Fix up saved configurations with changes
	    	 */
	    	if(gEdChart.name === "completion_time" || gEdChart.name === "periodic_count") {
	    		gEdChart.time_interval = true;	// 2016-12-06
	    	}
	    	
	    	initialiseWidgetDialog();
	    	
	    	$('#editWidget').modal("show");
	    });
	    
	    $('.chart-type').off().click(function(){
	    	var $this = $(this),
	    		filtered = gTasks.cache.surveyConfig[gTasks.gSelectedSurveyIndex].filtered,
	    		chart_type = $this.data("ctype"),
	    	    chartId = "#" + $this.closest('.aChart').find(".svg-container").attr("id"),
	    	    ibox = chart + "_ibox",
	    	    name;
	    	
	    	var config = globals.gCharts[chartId];
	    	var fullIndex = getFullIndex(filtered[config.index].name);

	    	// Set the new value in the full index then save it
	    	if(fullIndex >= 0) {
	    		gTasks.cache.surveyConfig[gTasks.gSelectedSurveyIndex].columns[fullIndex].chart_type = chart_type;
	    		saveConfig();
	    	}
	    	// Set the new value in the current list of charts
	    	filtered[config.index].chart_type = chart_type;
	    	config.svg.remove();
	    	
	    	refreshCharts();
	    })
	}
	
	/*
	 * Set the initial values for the widget dialog
	 */
	function initialiseWidgetDialog() {
		
		$("#chartForm")[0].reset();
		
    	addChartTypeSelect(gEdChart);
		$('#ew_chart_type').val(gEdChart.chart_type);
		$("#ew_title").val(gEdChart.humanName);
    	$('#ew_width').val(gEdChart.width);
    	$('#ew_fn').val(gEdChart.fn);
    	$('#ew_question').val(gEdChart.qIdx);
    	
    	if(gEdChart.chart_type === "bar" || gEdChart.chart_type === "pie") {
    		$(".numeric_only").show();
    	} else {
    		$(".numeric_only").hide();
    	}
    	if(gEdChart.group && gEdChart.chart_type !== "wordcloud") {
    		$(".group_only").show();
    		$('#ew_group').val(gEdChart.group);
    	} else {
    		$(".group_only").hide();
    	}
    	if(gEdChart.time_interval) {
    		$(".date_range_only").show();
    		$("#ew_date1").val(gEdChart.groups[0].q);
    		$("#ew_date2").val(gEdChart.groups[1].q);
    	} else {
    		$(".date_range_only").hide();
    	}
    	if(gEdChart.tSeries) {
    		$(".period_only").show();		
    		$('#ew_period').val(gEdChart.period);

    	} else {
    		$(".period_only").hide();
    	}
    	
	}
	
	/*
	 * Get the config item that this filtered item refers to
	 */
	function getFullIndex(name) {
		var i = 0,
			columns = gTasks.cache.surveyConfig[gTasks.gSelectedSurveyIndex].columns,
			index = -1;
		
		for(i = 0; i < columns.length; i++) {
			if(columns[i].name === name) {
				index = i;
				break;
			}
		}
		return index;
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
	
	/*
	 * Add a new chart
	 */
	function addNewChart() {
		gEdChart = $.extend(true, {}, gBlankChart);
		gIsNewChart = true;
		initialiseWidgetDialog();
		$('#editWidget').modal("show");
	}

	/*
	 * Add the list of selectable chart types
	 */
	function addChartTypeSelect() {
		/*
		 * Add the chart type select list
		 */
		h = [];
		idx = -1;
		for (key in avCharts) {
			
		    if (avCharts.hasOwnProperty(key)) {
		    	if(gEdChart.tSeries) {
		    		if(key !== "groupedBar") {
		    			continue;
		    		}
		    	} else {
		    		if(key === "groupedBar") {
		    			continue;
		    		}
		    	}
		    	keylangid = (key === "groupedBar") ? "bar" : (key === "map") ? "c_map" : key;
		    	h[++idx] = '<option value="';
		    	h[++idx] = key;
		    	h[++idx] = '">';
		    	h[++idx] = localise.set[keylangid];
		    	h[++idx] = '</option>';
		    }
		}
		$('.chart_type').empty().append(h.join(''));
	}

});
