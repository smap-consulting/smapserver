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

var arc,
	enterAntiClockwise = {
		  startAngle: Math.PI * 2,
		  endAngle: Math.PI * 2
		},
		color;

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
	function add(chartId, chart, config, data, width, height, margin) {

		console.log("Show pie chart");
		
		var radius = Math.min(width, height) / 2;
		
		arc = d3.arc()
			.outerRadius(radius - 10)
			.innerRadius(10);
		
		color = d3.scaleOrdinal()
			.range(d3.schemeCategory10);
		
		var labelArc = d3.arc()
	    	.outerRadius(radius - 40)
	    	.innerRadius(radius - 40);
		
		config.pie = d3.pie()
	    	.sort(null)
	    	.value(function(d) { return d.value; });
		
		config.g = config.svg.append("g")
	    	.attr("id", "pieChart")
	    	.attr("transform", "translate(" + width / 2 + "," + height / 2 + ")");
		
		config.gLabel = config.svg.append("g")
			.attr("transform", "translate(" + width / 2 + "," + height / 2 + ")");
		
		config.path = config.g.selectAll("path")
	    	.data(config.pie(data))
	    	.enter()
	    	.append("path")
	    	.attr("fill", function(d, i) { return color(d.data.key); })
		      .attr("d", arc)
		      .each(function(d) { this._current = d; }); // store the initial angles
		
		config.gLabel.selectAll("text") 
			.data(config.pie(data))
	    	.enter()
	    		.append("text")
	    		.attr("transform", function(d) { return "translate(" + labelArc.centroid(d) + ")"; })
	    		.attr("dy", ".35em")
	    		.text(function(d) { 
	    			return d.data.key; 
	    		});
	    	
	}
	
	/*
	 * Redraw
	 */
	function redraw(chartId, chart, config, data, width, height, margin) {
		
		console.log("Redraw pie chart");
		
		config.path = config.path.data(config.pie(data));
		
		config.path.enter().append("path")
			.merge(config.path)
	    	.attr("fill", function(d, i) { return color(d.data.key); })
	    	.attr("d", arc(enterAntiClockwise))
	    	.each(function (d) {
	    		this._current = {
	    		data: d.key,
	    		value: d.value,
	    		startAngle: enterAntiClockwise.startAngle,
	    		endAngle: enterAntiClockwise.endAngle
	    		};
	      }); // store the initial values
		 
		config.path.exit()
	      .transition()
	      .duration(750)
	      .attrTween('d', arcTweenOut)
	      .remove(); // now remove the exiting arcs
	      
		config.path.transition().duration(750).attrTween("d", arcTween);

	}
	
	// Store the displayed angles in _current.
	// Then, interpolate from _current to the new angles.
	// During the transition, _current is updated in-place by d3.interpolate.
	function arcTween(a) {
	  var i = d3.interpolate(this._current, a);
	  this._current = i(0);
	  return function(t) {
	    return arc(i(t));
	  };
	}
	
	// Interpolate exiting arcs start and end angles to Math.PI * 2
	// so that they 'exit' at the end of the data
	function arcTweenOut(a) {
	  var i = d3.interpolate(this._current, {startAngle: Math.PI * 2, endAngle: Math.PI * 2, value: 0});
	  this._current = i(0);
	  return function (t) {
	    return arc(i(t));
	  };
	}

});