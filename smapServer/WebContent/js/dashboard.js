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


requirejs.config({
    baseUrl: 'js/libs',
    waitSeconds: 0,
    paths: {
    	app: '../app',
    	jquery: '../../../../js/libs/jquery-2.1.1',
    	moment: 'moment-with-locales.min',
       	lang_location: '../'
    },
    shim: {
    	'app/common': ['jquery'],
    	'bootstrap.min': ['jquery'],
    	'inspinia': ['jquery'],
    	'metismenu': ['jquery'],
    	'icheck': ['jquery'],
    	'slimscroll': ['jquery'],
    }
});

require([
         'jquery', 
         'bootstrap.min',
         'app/common', 
         'app/globals',
         'app/localise',
         'bootstrapfileinput',
         'moment',
         'd3',
         'inspinia',
         'metismenu',
         'slimscroll',
         'icheck'
         ], function($, bootstrap, common, globals, localise, bsfi, moment) {

	$(document).ready(function() {

		window.moment = moment;
		
		localise.setlang();		// Localise HTML
		
		globals.gIsAdministrator = false;
		getLoggedInUser(getAlerts, false, true, undefined, false, false);
		
		enableUserProfileBS();
		
		$('#show_alerts').click(function(){
			if(!globals.gAlertSeen) {
				globals.gAlertSeen = true;
				$('.alert_icon').removeClass("text-danger");
				saveLastAlert(globals.gLastAlertTime, true);
			}
		});
		
		$('input', '#tab-settings-content').iCheck({
			 checkboxClass: 'icheckbox_square-green',
			 radioClass: 'iradio_square-green'
		});
		
		$('#d_rep_def_freq').click(function(){
			getReportData(1490, "main");
		});
	});
	
	/*
	 * Get the data for a form
	 */
	function getReportData(sId, form) {
		$.ajax({
			url: "/api/v1/data/" + sId,
			dataType: 'json',
			cache: false,
			success: function(data) {
				removeHourglass();
				processReportResults(data);
			},
			error: function(xhr, textStatus, err) {
				removeHourglass();
				if(xhr.readyState == 0 || xhr.status == 0) {
		              return;  // Not an error
				} else {
					alert("Error: Failed to get data: " + err);
				}
			}
		});	
	}
	
	/*
	 * Show a report
	 */
	function processReportResults(results) {
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
				  var diff = timeDifference(d.start, d.end); 
				  return diff._milliseconds / 1000; 
		  	  }); 
		  })	  
		  .entries(results);
		
		console.log("Duration by device: " + JSON.stringify(avgDurDevice));
		addChart("#chart1", countByRegion, "pie");
		addChart("#chart2", avgDurDevice, "bar");
	}
	
	/*
	 * Add a chart
	 */
	function addChart(chart, data, type) {
		
		// Get dynamic widths of container
		var widthContainer = $(chart).width();
		var heightContainer = $(chart).height();
		var view = "0 0 " + widthContainer + " " + heightContainer;
		
		var svg = d3.select(chart).append("svg")
			  .attr("preserveAspectRatio", "xMinYMin meet")
			  .attr("viewBox", view)
			  .classed("svg-content", true);
		
		var margin = {top: 20, right: 20, bottom: 30, left: 40},
	    	width = +widthContainer - margin.left - margin.right,
	    	height = +heightContainer - margin.top - margin.bottom;
		
		if(type === "bar") {
			addBarChart(svg, data, width, height, margin);
		} else if(type === "pie") {
			addPieChart(svg, data, width, height, margin);
		} else {
			console.log("unknown chart type");
		}
	}
	
	/*
	 * Add a bar chart
	 */
	function addBarChart(svg, data, width, height, margin) {

		var x = d3.scaleBand().rangeRound([0, width]).padding(0.1),
	    	y = d3.scaleLinear().rangeRound([height, 0]);
		
		var g = svg.append("g")
	    	.attr("transform", "translate(" + margin.left + "," + margin.top + ")");
		


		x.domain(data.map(function(d) { return d.key; }));
		y.domain([0, d3.max(data, function(d) { return d.value; })]);
	
		  g.append("g")
		      .attr("class", "axis axis--x")
		      .attr("transform", "translate(0," + height + ")")
		      .call(d3.axisBottom(x));
	
		  g.append("g")
		      .attr("class", "axis axis--y")
		      .call(d3.axisLeft(y).ticks(10).tickFormat(d3.format("d")))
		    .append("text")
		      .attr("transform", "rotate(-90)")
		      .attr("y", 6)
		      .attr("dy", "0.71em")
		      .attr("text-anchor", "end")
		      .text("Count");
	
		  g.selectAll(".bar")
		    .data(data)
		    .enter().append("rect")
		      .attr("class", "bar")
		      .attr("x", function(d) { return x(d.key); })
		      .attr("y", function(d) { return y(d.value); })
		      .attr("width", x.bandwidth())
		      .attr("height", function(d) { return height - y(d.value); });
		
	}
	
	function addPieChart(svg, data, width, height, margin) {
		
		var radius = Math.min(width, height) / 2;
		
		var color = d3.scaleOrdinal()
	    	.range(["#98abc5", "#8a89a6", "#7b6888", "#6b486b", "#a05d56", "#d0743c", "#ff8c00"]);
		
		var arc = d3.arc()
	    	.outerRadius(radius - 10)
	    	.innerRadius(0);
		
		var labelArc = d3.arc()
	    	.outerRadius(radius - 40)
	    	.innerRadius(radius - 40);
		
		var pie = d3.pie()
	    	.sort(null)
	    	.value(function(d) { return d.value; });
		
		svg.append("g")
	    	.attr("transform", "translate(" + width / 2 + "," + height / 2 + ")");
		
		var g = svg.selectAll(".arc")
	      .data(pie(data))
	      .enter().append("g")
	      .attr("class", "arc");
		
		g.append("path")
	      .attr("d", arc)
	      .style("fill", function(d) { return color(d.key); });
		
		g.append("text")
	      .attr("transform", function(d) { return "translate(" + labelArc.centroid(d) + ")"; })
	      .attr("dy", ".35em")
	      .text(function(d) { return d.key; });
	}
	
});
	


