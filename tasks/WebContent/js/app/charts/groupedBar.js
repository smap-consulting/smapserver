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
         'localise'], 
		function($, modernizr, lang, globals, d3, localise) {

	
	return {	
		add: add,
		redraw: redraw
	};
	


	/*
	 * Add
	 */
	function add(chartId, chart, config, data, width, height, margin) {

		var barWidth;   
	    
		config.color = d3.scaleOrdinal(d3.schemeCategory10);
		
	    config.x0 = d3.scaleBand().rangeRound([0, width], .1);
	    config.x1 = d3.scaleBand();
	    
		config.x0.domain(data.map(function(d) { 
			return d.key; 
			}));
		config.x1.domain(chart.groupLabels).rangeRound([0, config.x0.bandwidth()]);
		barWidth = config.x1.bandwidth();
	    
	    config.y = d3.scaleLinear().rangeRound([height, 0]);
	    config.y.domain([0, d3.max(data, function(d) { 
			var i,
				maxv = d.pr[0].value;
			for(i = 0; i < d.pr.length - 1; i++) {
				maxv = Math.max(maxv, d.pr[i+1].value)
			}
			return maxv; 
		})]);
		
		config.xAxis = d3.axisBottom(config.x0);
		config.yAxis = d3.axisLeft(config.y).ticks(10, "");
		
		config.g = config.svg.append("g")
	    	.attr("transform", "translate(" + margin.left + "," + margin.top + ")");

		config.xTicks = config.g.append("g")
			.attr("class", "axis axis--x")
			.attr("transform", "translate(0," + height + ")");
		config.xTicks.call(config.xAxis);
	
		// Add x-axis label
		var text = config.svg.append("text")             
	    		.attr("x", width / 2 )
	    		.attr("y",  height + margin.top + 40 )
	    		.style("text-anchor", "middle");		
		if(chart.tSeries) {
			text.text(localise.set["c_" + chart.period]);
		} else if(chart.select_name) {
			text.text(chart.select_name);
		} else {
			text.text(chart.humanName);
		}
		
		// Add y-axis label
		config.svg.append("text")
        	.attr("text-anchor", "middle")  
        	.attr("transform", "translate("+ (margin.left/3) +","+(height/2)+")rotate(-90)")  
        	.text(localise.set[chart.fn]);
		
		config.g.append("g")
		    .attr("class", "axis axis--y")
		    .call(config.yAxis);

		
	}
	
	/*
	 * Update a bar chart
	 */
	function redraw(chartId, chart, config, data, width, height, margin) {
		
		var barWidth,
			i;
		
		/*
		config.x0.domain(data.map(function(d) { 
			if(!d.key || d.key === "") {
				return localise.set["c_undef"]; 
			} else {
				return d.key;
			}
		}));
		*/
	    
		config.x0.domain(data.map(function(d) { 
				return d.key; 
			}));
		config.x1.domain(chart.groupLabels).rangeRound([0, config.x0.bandwidth()]);
		barWidth = config.x1.bandwidth();

		config.y.domain([0, d3.max(data, function(d) { 
				var i,
					maxv = d.pr[0].value;
				for(i = 0; i < d.pr.length - 1; i++) {
					maxv = Math.max(maxv, d.pr[i+1].value)
				}
				return maxv; 
			})]);
		
		// Update axes
		config.svg.select(".axis--y").call(config.yAxis.ticks(5, ""));
		if(chart.tSeries) {
			var tvArray = [];
			if(data.length > chart.width) {
				var skips = Math.ceil(data.length / chart.width);
				for(i = 0; i < data.length; i++) {
					if(i == data.length - 1 || i%skips == 0) {
						tvArray.push(data[i].key);
					}
				}
				
			} else {
				tvArray = data.map(function (d) { return d.key});
			}
			config.xAxis.tickValues(tvArray);
		}
		
		config.xTicks.call(config.xAxis);
		
		
		var period = config.g.selectAll(".period").data(data);
		var periodEntries = period
			.enter().append("g").attr("class", "period")
			.merge(period)
			.attr("transform", function(d) { return "translate(" + config.x0(d.key) + ",0)"; });
		period.exit().remove();
		
		var bars = periodEntries.selectAll(".bar").data(function(d) { return d.pr; });
		
		// New bars
		bars.enter()
			.append("rect")
			.attr("class", "bar")
			.merge(bars)
			.attr("x", function(d) { 
				if(!d.key || d.key === "") {
					return config.x1(localise.set["c_undef"]);
				} else {
					return config.x1(d.key); 
				}
			})
	    	.attr("y", function(d) { 
	    		return config.y(d.value); 
	    		})
	    	.attr("width", barWidth)
	    	.attr("height", function(d) { return height - config.y(d.value); })
	    	.style("fill", function(d) { return config.color(d.key); });
			
		// Bars being removed
		bars.exit()
			.transition()
			.attr("y", config.y(0))
			.attr("height", height - config.y(0))
			//.style('fill-opacity', 1e-6)
			.remove();
		
		config.xTicks.call(config.xAxis);
		/*
		config.xTicks = config.g.append("g")
			.attr("class", "axis axis--x")
			.attr("transform", "translate(0," + height + ")")
			.call(config.xAxis)
			.selectAll("text");
			
		xText.enter()
	        	.style("text-anchor", "end")
	        	.attr("dx", "-.8em")
	        	.attr("dy", ".15em")
	        	.attr("transform", function(d) {
	        		return "rotate(-65)" 
	            	});
		xText.exit().remove();
		*
		if(chart.tSeries) {
			// Max 10 X axis ticks
			if(data.length > 10) {
				var skips = Math.ceil(data.length / 10);
				var tick_text = config.svg.selectAll(".axis--x .tick text");
	
				tick_text.attr("class", function(d,i){
					if(i%skips != 0) d3.select(this).remove();
				});
			}
		}
		*/
		
		/*
		 * Legend
		 */
		var legend = config.svg.selectAll(".legend")
	      .data(chart.groupLabels.slice().reverse())
	      .enter().append("g")
		      .attr("class", "legend")
		      .attr("transform", function(d, i) { return "translate(0," + i * 20 + ")"; });
		
		legend.append("rect")
	      .attr("x", width - 18)
	      .attr("width", 18)
	      .attr("height", 18)
	      .style("fill", function(d) { return config.color(d); });
		
		legend.append("text")
	      .attr("x", width - 24)
	      .attr("y", 9)
	      .attr("dy", ".35em")
	      .style("text-anchor", "end")
	      .text(function(d) { return d; });
	}
	

});