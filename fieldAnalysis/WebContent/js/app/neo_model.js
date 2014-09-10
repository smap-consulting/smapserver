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

define(['jquery', 'jquery_ui', 'rmm', 'localise', 'globals', 'd3'],
		function($, jquery_ui, rmm, localise, globals, d3) {

	$(document).ready(function() {
		/*
		 * Thingsat edit element
		 */	
		$('#dialog_element_edit_ta').dialog(
			{
				autoOpen: false, closeOnEscape:true, draggable:true, modal:true,
				show:"drop",
				width: 350,
				maxHeight: 700,
				zIndex: 4000,
				buttons: [
			        {
			        	text: "Cancel",
			        	click: function() {
			        		$(this).dialog("close");
			        	}
			        },
			        {
			        	text: "Save",
			        	click: function() {
			        	}
		
			        }
				]
			}
		);
		
		$('#ta_add_property').button().click(function (e){
			e.preventDefault();
			var property = {};
			globals.gTaElement.properties.splice(globals.gTaElement.properties.length - 1, 0, property);
			addProperty(property);
		});
		
	});
	
	var graph = {
			  "nodes":[
			           {"name":"Myriel","group":1,
			        	   label: {
			        		   
			        	   },
			        	   properties: []
			           },
			           {"name":"Napoleon","group":1,
			        	   label: {
			        		   
			        	   },
			        	   properties: []
			           },
			           {"name":"Mlle.Baptistine","group":1,
			        	   label: {
			        		   
			        	   },
			        	   properties: []
			           },
			           {"name":"Mme.Magloire","group":1,
			        	   label: {
			        		   
			        	   },
			        	   properties: []
			           }
			          
			         ],
			         "links":[
			           {"source":1,"target":0,"value":1},
			           {"source":2,"target":0,"value":8},
			           {"source":3,"target":0,"value":10},
			           {"source":3,"target":2,"value":6}
			          
			         ]
			       };
	
	return {
		
		showModel: function(element, sId, width, height) {
			showModel(element, sId, width, height);
		},showTable: function(element, sId) {
			showTable(element, sId);
		}
		
	}
	
	/*
	 * Show the node and link elements that make up the model in a table
	 */
	function showTable(element, sId) {
		var h = [],
			idx = -1,
			i,
			nodes = graph.nodes,
			links = graph.links;
		
		// Nodes
		h[++idx] = '<button id="ta_add">Add</button>';
		
		h[++idx] = '<table id="fl" border="0" width="100%">';
		h[++idx] = '<thead>';
			h[++idx] = '<tr>';
				h[++idx] = '<th>Node</th>';
				h[++idx] = '<th>Edit</th>';
				h[++idx] = '<th>Delete</th>';
			h[++idx] = '</tr>';
		h[++idx] = '</thead>';

		h[++idx] = '<tbody>';
		for(i = 0; i < nodes.length; i++) {
			h[++idx] = '<tr>';
				h[++idx] = '<td>';
					h[++idx] = nodes[i].name;
				h[++idx] = '</td>';
				h[++idx] = '<td>';
					h[++idx] = '<button value="' + i + '" class="media_btn_rem ta_node_edit">*</button>';
				h[++idx] = '</td>';
				h[++idx] = '<td>';
					h[++idx] = '<button value="' + i + '" class="media_btn_rem ta_node_rem">-</button>';
			h[++idx] = '</td>';
			h[++idx] = '</tr>';
		}
		h[++idx] = '</tbody>';
		h[++idx] = '</table>';
		
		
		// Show table of links
		
		h[++idx] = '<table id="fl" border="0" width="100%">';
		h[++idx] = '<thead>';
			h[++idx] = '<tr>';
				h[++idx] = '<th>Relation</th>';
				h[++idx] = '<th>Edit</th>';
				h[++idx] = '<th>Delete</th>';
			h[++idx] = '</tr>';
		h[++idx] = '</thead>';
		
		h[++idx] = '<tbody>';
		for(i = 0; i < links.length; i++) {
			h[++idx] = '<tr>';
				h[++idx] = '<td>';
					h[++idx] = links[i].value;
				h[++idx] = '</td>';
				h[++idx] = '<td>';
					h[++idx] = '<button value="' + i + '" class="media_btn_rem ta_link_edit">*</button>';
				h[++idx] = '</td>';
				h[++idx] = '<td>';
					h[++idx] = '<button value="' + i + '" class="media_btn_rem ta_link_rem">-</button>';
			h[++idx] = '</td>';
			h[++idx] = '</tr>';
		}
		h[++idx] = '</tbody>';
		h[++idx] = '</table>';

		$(element).empty().append(h.join(''));
		
		// Add events
		$(element).find('.ta_node_rem').click(function(){
			var nodeIdx = $(this).val();
			nodes.splice(nodeIdx, 1);
			
			// Remove any links to this node
			for(i = links.length - 1; i >= 0; i--) {
				if(links[i].source.index == nodeIdx || links[i].target.index == nodeIdx) {
					links.splice(i, 1);
				}
			}
			showModel('#ta_model_edit', sId, 300, 200);
			showTable('#ta_items_edit', sId);
		});
		
		$(element).find('.ta_link_rem').click(function(){
			var linkIdx = $(this).val();
			links.splice(linkIdx, 1);
			showModel('#ta_model_edit', sId, 300, 200);
			showTable('#ta_items_edit', sId);
		});
		
		$(element).find('.ta_node_edit').click(function(){
			var nodeIdx = $(this).val();
			globals.gTaElement = nodes[nodeIdx];
			globals.gTaElement.type = "node";
			setupTaElementEdit();
		});
		
		$(element).find('.ta_link_edit').click(function(){
			var linkIdx = $(this).val();
			globals.gTaElement = links[linkIdx];
			globals.gTaElement.type = "link";
			setupTaElementEdit();
		});
		
		$('#ta_add').button().click(function(){
			globals.gTaElement = {
			};
			setupTaElementEdit();
		});
		
		
	}
	
	function setupTaElementEdit() {
		var element = globals.gTaElement;
		console.log(element);
		
		//if(element.name) {
			$('#ta_name').val(element.name);
		//}
		//if(element.type) {
			$('#ta_type').val(element.type);
			if(element.properties) {
				for(i = 0; i < element.properties.length; i++) {
					addProperty(element.properties[i]);
				}
			}
		//}
		$('#dialog_element_edit_ta').dialog("open");
	}
	
	function showModel(element, sId, width, height) {
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
		

		  force
		      .nodes(graph.nodes)
		      .links(graph.links)
		      .start();

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
		      .attr("r", 5)
		      .style("fill", function(d) { return color(d.form); })
		      .call(force.drag);

		 
		  
		  node.append("title")
		  	.text(function(d) { return d.name; });

		  force.on("tick", function() {
		    link.attr("x1", function(d) { return d.source.x; })
		        .attr("y1", function(d) { return d.source.y; })
		        .attr("x2", function(d) { return d.target.x; })
		        .attr("y2", function(d) { return d.target.y; });

		    node.attr("cx", function(d) { return d.x; })
		        .attr("cy", function(d) { return d.y; });
		  });

		  /*
		  $('circle').off().click(function() {
			  alert($(this).text());
		  });
		  */
		
	}
	
	function addProperty(property) {
		var h = [],
			idx = -1;
		
		h[++idx] = '<tr>';
		h[++idx] = '<td>';
		h[++idx] = 'name';
		h[++idx] = '</td>';
		h[++idx] = '<td>';
		h[++idx] = 'unique';
		h[++idx] = '</td>';
		h[++idx] = '<td>';
		h[++idx] = 'delete';
		h[++idx] = '</td>';
		h[++idx] = '</tr>';
		
		$('#ta_property_list').append(h.join(''));
		
	}
});