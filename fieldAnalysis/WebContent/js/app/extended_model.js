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
	
	var local_sId,
		local_fId,
		local_language,
		available_questions,
		taElement,				// Thingsat element currently being edited
		taIdx;					// The index of the element currently being edited
	
	return {
		
		showModel: showModel,
		convertMetaToGraph: convertMetaToGraph
	}
	
	function showModel(element, width, height) {
		
		if(graph.nodes.length === 0) {
			$(element).empty().append("<h1 class='center'>Create a model to export</h1>");
			$('#export').next().find("button:contains('Export')").addClass("ui-state-disabled");
			return;
		}
		
		$('#export').next().find("button:contains('Export')").removeClass("ui-state-disabled");
		
		var $elem = $(element);
		
		var color = d3.scale.category20();

		var force = d3.layout.force()
		    .charge(-120)
		    .linkDistance(30)
		    .size([width, height]);
		
		$elem.empty();
		var svg = d3.select(element).append("svg")
	    	.attr("width", width)
	    	.attr("height", height);
		

		try {
		  force
		      .nodes(graph.nodes)
		      .links(graph.links)
		      .start();
		} catch(err) {
			alert("There is an error in the model, try deleting nodes to fix");
		}

		var link = svg.selectAll(".link")
		      .data(graph.links)
		    .enter().append("line")
		      .attr("class", "link")
		      .style("stroke", "black")
		      .style("stroke-width", function(d) { return Math.sqrt(d.value); });

		var node = svg.selectAll(".node")
		      .data(graph.nodes)
		    .enter().append("circle")
		      .attr("id", function(d) { return d.name})
		      .attr("class", "node")
		      .attr("r", 10)
		      .style("fill", function(d) { return "red"; })
		      .call(force.drag);

        node.append("title").text(function (d) {
            return d.name;
        });
	      
        /*
         * The next section is from neo4j visualisation http://www.neo4j.org/learn/cypher
         */
        var text = svg.append("svg:g").selectAll("g").data(force.nodes()).enter().append("svg:g");

        text.append("svg:text").attr("x", 8).attr("y", "-.31em").text(function (d) {
            return d.name;
        });
        /*
         * End neo4j code
         */

		  force.on("tick", function() {
		    link.attr("x1", function(d) { return d.source.x; })
		        .attr("y1", function(d) { return d.source.y; })
		        .attr("x2", function(d) { return d.target.x; })
		        .attr("y2", function(d) { return d.target.y; });

		    node.attr("cx", function(d) { return d.x; })
		        .attr("cy", function(d) { return d.y; });
		    
          //  text.attr("transform", function (d) {
          //      return "translate(" + d.x + "," + d.y + ")";
          //  });
            text.attr("transform", function (d) {
                return "translate(" + d.x + "," + d.y + ")";
            });
		  });

		  /*
		  $('circle').off().click(function() {
			  alert($(this).text());
		  });
		  */
		
	}
	
	function convertMetaToGraph(meta) {
		var i,
			graph = {
				nodes: [],
				links: []
		}
		
		for(i = 0; i < meta.forms.length; i++) {
			console.log("   form: " + meta.forms[i].name);
			
		}
	}
	
});