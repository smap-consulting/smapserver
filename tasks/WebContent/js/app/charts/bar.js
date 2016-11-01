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
         'd3'], 
		function($, modernizr, lang, globals, d3) {

	
	return {	
		add: add,
		redraw: redraw
	};
	


	/*
	 * Add
	 */
	function add(chart, config, data, width, height, margin) {

		config.x = d3.scaleBand().rangeRound([0, width]).padding(0.1);
	    config.y = d3.scaleLinear().rangeRound([height, 0]);
		
		config.x.domain(data.map(function(d) { return d.key; }));
		config.y.domain([0, d3.max(data, function(d) { return d.value; })]).nice();
		
		config.xAxis = d3.axisBottom(config.x);
		
		config.yAxis = d3.axisLeft(config.y);
		
		config.svg.append("g")
	    	.attr("transform", "translate(" + margin.left + "," + margin.top + ")");

		config.svg.append("g")
			.attr("class", "axis axis--x")
		    .attr("transform", "translate(0," + height + ")")
		    .call(config.xAxis)
		    .call(config.xAxis);
	
		config.svg.append("g")
		    .attr("class", "axis axis--y")
		    .call(config.yAxis)
		    .append("text")
		      .attr("transform", "rotate(-90)")
		      .attr("y", 6)
		      .attr("dy", "0.71em")
		      .attr("text-anchor", "end")
		      .text("Count");
	
		/*
		  config.g.selectAll(".bar")
		    .data(data)
		    .enter().append("rect")
		      .attr("class", "bar")
		      .attr("x", function(d) { return config.x(d.key); })
		      .attr("y", function(d) { return config.y(d.value); })
		      .attr("width", config.x.bandwidth())
		      .attr("height", function(d) { return height - config.y(d.value); });
		      */
		
	}
	
	/*
	 * Update a bar chart
	 */
	function redraw(chart, config, data, width, height, margin) {
		
		console.log("Refresh bar chart");
		
		config.x.domain(data.map(function(d) { return d.key; }));
		config.y.domain([0, d3.max(data, function(d) { return d.value; })]);
		
		// Update axes
		config.svg.select(".axis--y")
			//.transition()
			//.duration(500)
			.call(config.yAxis.ticks(5, ""));
		config.svg.select(".axis--x")
			//.transition()
			//.duration(500)
			.call(config.xAxis);
		
		var bars = config.svg.selectAll(".bar").data(data, function(d) { return d.key; });
		
		// Bars being removed
		bars.exit()
			.transition()
			.duration(300)
			.attr("y", config.y(0))
			.attr("height", height - config.y(0))
			//.style('fill-opacity', 1e-6)
			.remove();
		
		// New bars
		bars.enter()
			.append("rect")
			.attr("class", "bar")
			.attr("x", function(d) { return config.x(d.key); })
	    	.attr("y", function(d) { 
	    		console.log(d);
	    		console.log(config.y(d.value));
	    		return config.y(d.value); 
	    		})
	    	.attr("width", config.x.bandwidth())
	    	.attr("height", function(d) { return height - config.y(d.value); });
		
		// BArs being update
		bars.transition()
			.duration(300)
	    	.attr("x", function(d) { return config.x(d.key); })
	    	.attr("y", function(d) { return config.y(d.value); })
	    	.attr("width", config.x.bandwidth())
	    	.attr("height", function(d) { return height - config.y(d.value); });
					
	}
	

});