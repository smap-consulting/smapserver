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

define(['jquery', 'jquery_ui', 'rmm', 'localise', 'globals'],
		function($, jquery_ui, rmm, localise, globals) {
	
	var graph = {
			  "nodes":[],
			  "links":[]
			    };
	
	var svg,
		svgNodes,
		svgLinks,
		simulation,
		color,
		duration = 500,
		theElement,
		theWidth,
		theHeight;
		
	
	return {
		
		showModel: showModel,
		convertMetaToGraph: convertMetaToGraph
	}
	
	/*
	 * Create a graph from the survey meta data
	 * Styling
	 *  Survey: color
	 *  Selected Form: Large Circle
	 *  Form: Small Circle
	 *  Path: dark link thick
	 *  Parent child link: thick
	 *  Other survey link: thin
	 *  Unselectable forms: low opacity
	 */
	function convertMetaToGraph(meta) {
		var i,
			node,
			link,
			surveys = {};
		
		for(i = 0; i < meta.forms.length; i++) {
			node = {
					id: meta.forms[i].f_id,
					survey: meta.forms[i].s_id,
					name: meta.forms[i].name,
					selected: false
			}
			graph.nodes.push(node);		
			
			if(+meta.forms[i].p_id != 0) {
				link = {
						source: meta.forms[i].f_id,
						target: meta.forms[i].p_id,
						value: 2
				}
				graph.links.push(link);	
			} else {
				surveys[meta.forms[i].s_id] = meta.forms[i].f_id;	// Save the top level form for the survey
			}
		}
		
		for(i = 0; i < meta.links.length; i++) {
			link = {
					source: meta.links[i].fromFormId,
					target: surveys[meta.links[i].toSurveyId],
					value: 1
			}
			graph.links.push(link);	
		}
		
		
		
		showModel('#extsvg', 200, 200)
	}
	
	/*
	 * Show model of selectable forms including those from linked surveys
	 */
	function showModel(element, width, height) {
		
		if(graph.nodes.length === 0) {
			$(element).empty().append("<h1 class='center'>" + localise.set["msg_nf"] + "</h1>");
			$('#export').next().find("button:contains('Export')").addClass("ui-state-disabled");
			return;
		}
		
		$('#export').next().find("button:contains('Export')").removeClass("ui-state-disabled");
		
		theElement = element;
		theWidth = width;
		theHeight = height;
		color = d3.scaleOrdinal(d3.schemeCategory20);

		simulation = d3.forceSimulation()
	    	.force("link", d3.forceLink().id(function(d) { return d.id; }))
	    	.force("charge", d3.forceManyBody())
	    	.force("center", d3.forceCenter(width / 2, height / 2));
		
		update();

	}
		
	/*
	 * Functions for d3
	 */
	function update() {
		
		var $elem = $(theElement);	
		$elem.empty();
		
		// Get dynamic widths of container
		var width = $elem.width();
		var height = $elem.height();
		
		svg = d3.select(theElement).append("svg")
	    	.attr("width", width)
	    	.attr("height", height);
		
		svgLinks = svg.append("g").attr("class", "links");
		svgNodes = svg.append("g").attr("class", "nodes");
		
		var link = svgLinks
	    	.selectAll("line")
	    	.data(graph.links)
	    	.enter().append("line")
	    		.attr("stroke-width", function(d) { return Math.sqrt(d.value); });

		var nodeGroup = svgNodes
	    	.selectAll("circle")
	    	.data(graph.nodes)
	    	.enter().append("g");
		
		var node = nodeGroup
	    	.append("circle")
	    		.attr("r", function(d) {return d.selected ? 10 : 5;})
	    		.attr("fill", function(d) { return color(d.survey); })
	    		.on("click", click)
	    		.call(d3.drag()
	    				.on("start", dragstarted)
	    				.on("drag", dragged)
	    				.on("end", dragended));
		
		var text = nodeGroup.append("text")
        	.attr("text-anchor", "left")
        	.text(function(d) { return d.name });
		
		node.append("title")
	      .text(function(d) { return d.id; });
		
		simulation
	      .nodes(graph.nodes)
	      .on("tick", ticked);
	   
		simulation.force("link")
	    	.links(graph.links);
		
	  function ticked() {
	    link
	        .attr("x1", function(d) { return d.source.x; })
	        .attr("y1", function(d) { return d.source.y; })
	        .attr("x2", function(d) { return d.target.x; })
	        .attr("y2", function(d) { return d.target.y; });
	
	    node
	        .attr("cx", function(d) { return d.x; })
	        .attr("cy", function(d) { return d.y; });
	    
	    text
        	.attr("x", function(d) { return d.x + 10 })
        	.attr("y", function(d) { return d.y; });
		}
	}
  
	function click(d) {
		
		d.selected = !d.selected;
		update();
	}
	
	function dragstarted(d) {
		  if (!d3.event.active) simulation.alphaTarget(0.3).restart();
		  d.fx = d.x;
		  d.fy = d.y;
		}

		function dragged(d) {
		  d.fx = d3.event.x;
		  d.fy = d3.event.y;
		}

		function dragended(d) {
		  if (!d3.event.active) simulation.alphaTarget(0);
		  d.fx = null;
		  d.fy = null;
		}
});