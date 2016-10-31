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
	function add(chart, config, svg, data, width, height, margin) {

		config.x = d3.scaleBand().rangeRound([0, width]).padding(0.1);
	    config.y = d3.scaleLinear().rangeRound([height, 0]);
		
		config.x.domain(data.map(function(d) { return d.key; }));
		config.y.domain([0, d3.max(data, function(d) { return d.value; })]).nice();
		
		config.g = svg.append("g")
	    	.attr("transform", "translate(" + margin.left + "," + margin.top + ")");

	
		  config.g.append("g")
		      .attr("class", "axis axis--x")
		      .attr("transform", "translate(0," + height + ")")
		      .call(d3.axisBottom(config.x));
	
		  config.g.append("g")
		      .attr("class", "axis axis--y")
		      .call(d3.axisLeft(config.y).ticks(10).tickFormat(d3.format("d")))
		    .append("text")
		      .attr("transform", "rotate(-90)")
		      .attr("y", 6)
		      .attr("dy", "0.71em")
		      .attr("text-anchor", "end")
		      .text("Count");
	
		  config.g.selectAll(".bar")
		    .data(data)
		    .enter().append("rect")
		      .attr("class", "bar")
		      .attr("x", function(d) { return config.x(d.key); })
		      .attr("y", function(d) { return config.y(d.value); })
		      .attr("width", config.x.bandwidth())
		      .attr("height", function(d) { return height - config.y(d.value); });
		
	}
	
	/*
	 * Update a bar chart
	 */
	function redraw(chart, config, svg, data, width, height, margin) {
		
		console.log("Refresh bar chart");
		
		config.x.domain(data.map(function(d) { return d.key; }));
		config.y.domain([0, d3.max(data, function(d) { return d.value; })]).nice();
		
		// Update axes
		d3.select(chart + " .axis--y")
			.transition()
			.duration(1000)
			.call(config.y);
		d3.select(chart + " .axis--x")
			.transition()
			.duration(1000)
			.call(config.x);
		
		config.g.selectAll(".bar")
			.data(data)
			.transition()
			.duration(1000)
	    	.attr("x", function(d) { return config.x(d.key); })
	    	.attr("y", function(d) { return config.y(d.value); })
	    	.attr("width", config.x.bandwidth())
	    	.attr("height", function(d) { return height - config.y(d.value); });
					
		config.g.selectAll(".bar")
			.data(data)
				.enter().append("rect")
			    	.attr("class", "bar")
			    	.attr("x", function(d) { return config.x(d.key); })
			    	.attr("y", function(d) { return config.y(d.value); })
			    	.attr("width", config.x.bandwidth())
			    	.attr("height", function(d) { return height - config.y(d.value); });
	}
	

});