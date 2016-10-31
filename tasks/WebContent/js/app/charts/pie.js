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

		var radius = Math.min(width, height) / 2;
		
		var color = d3.scaleOrdinal()
	    	.range(d3.schemeCategory20);
		
		var arc = d3.arc()
	    	.outerRadius(radius - 10)
	    	.innerRadius(10);
		
		var labelArc = d3.arc()
	    	.outerRadius(radius - 40)
	    	.innerRadius(radius - 40);
		
		var pie = d3.pie()
	    	.sort(null)
	    	.value(function(d) { return d.value; });
		
	
		var g = config.svg.selectAll(".arc")
	      .data(pie(data))
	      .enter().append("g")
	      .attr("class", "arc");
		
		g.append("path")
	      .attr("d", arc)
	      .style("fill", function(d) { return color(d.data.key); });
		
		g.append("text")
	      .attr("transform", function(d) { return "translate(" + labelArc.centroid(d) + ")"; })
	      .attr("dy", ".35em")
	      .text(function(d) { 
	    	  return d.data.key; 
	    	  });
	}
	
	/*
	 * Redraw
	 */
	function redraw(chart, config, svg, data, width, height, margin) {
		
		console.log("Redraw pie chart");
		

	}
	

});