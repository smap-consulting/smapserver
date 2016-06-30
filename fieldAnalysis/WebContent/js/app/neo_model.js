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

	$(document).ready(function() {
		
		/*
		 * Thingsat edit dialog
		 */	
		$('#dialog_edit_ta').dialog(
			{
				autoOpen: false, closeOnEscape:true, draggable:true, modal:true,
				show:"drop",
				width: 350,
				maxHeight: 700,
				zIndex: 3000,
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
			        		var graphString = cleanGraph();
			        		var $dialog = $(this);
			        		$.ajax({
			        			  type: "POST",
			        			  dataType: 'text',
			        			  cache: false,
			        			  async: false,
			        			  url: "/surveyKPI/survey/" + local_sId + "/model",
			        			  data: { model: graphString },
			        			  success: function(data, status) {
			        				  removeHourglass();
			        				  var sMeta = globals.gSelector.getSurvey(local_sId);
			        				  sMeta.model = graphString;
			        				  showModel('#ta_model_show', 300, 200);
			        				  $dialog.dialog("close");
			     
			        			  }, error: function(xhr, textStatus, err) {
			        				  removeHourglass();
			        				  if(xhr.readyState == 0 || xhr.status == 0) {
			        					  return;  // Not an error
			        				  } else { 
			        					  alert("Error survey model not saved" + err); 
			        				  }
			        			  }
			        		});
			        	}
		
			        }
				]
			}
		);
		
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
			        		
			        		var type,
			        			sourceIdx,
			        			targetIdx,
			        			name;
			        			
			        		// validity checks
			        		type = $('#ta_type').val();
			        		sourceIdx = parseInt($('#ta_source').val());
			        		targetIdx = parseInt($('#ta_target').val());
			        		name = $('#ta_name').val();
			        		
			        		if(type === "link" && sourceIdx === targetIdx) {
			        			alert("You can't link a thing to itself, select a different source or from thing");
			        			$('#ta_source').focus();
			        			return;
			        		}
			        		
			        		if(name.length === 0) {
			        			alert("You must specify a name");
			        			$('#ta_name').focus();
			        			return;
			        		}	
			        		if(name.indexOf(' ') > 0) {
			        			alert("Names cannot have spaces");
			        			$('#ta_name').focus();
			        			return;
			        		}
			        		
			        		/*
			        		 * Get the property values from the property table
			        		 */
			        		var count = 0;
			        		var selectMultipleCount = 0;
			        		taElement.properties = [];
			        		$('#ta_property_list .question:checkbox:checked').each(function(index){
			        			
			        			var $this = $(this);
			        			var prop = {
			        					value_type: "record",		// TODO add support for other value types
			        					colName: $this.val(),
			        					q_type: $this.data("type"),
			        					q_id: $this.data("id"),
			        					unique: $this.parent().next().find('.unique').prop('checked')
			        				};
			        			
			        			taElement.properties.push(prop);
			        			
			        			if(prop.q_type === "select") {
			        				selectMultipleCount++;
			        			}
			        			count++;
			        		});
			        		if(type === "node" && count == 0) {
			        			alert("You must select at least one property for a node");
			        			return;
			        		}
			        		if(selectMultipleCount > 1) {
			        			alert("You can only use one select multiple question in a node or relation");
			        			return;
			        		}
			        		
			        		/*
			        		 * Update the element values
			        		 */
			        		if(!taElement.label) {
			        			taElement.label = {};
			        		}
			        		taElement.label.value = $('#ta_name').val();
			        		taElement.type = type;
			        		taElement.name = name;
			        		
			        		
			        		// Set the source and target if this is a link
			        		if(taElement.type === "link") {
			        			taElement.source = graph.nodes[sourceIdx];
			        			taElement.target = graph.nodes[targetIdx];
			        		}
			        		
			        		console.log("Updated element");
			        		console.log(taElement);
			        		
			        		// Add the element to the graph
			        		if(taElement.isnew) {
				        		if(taElement.type === "node") {
				        			graph.nodes.push(taElement);
				        		} else {
				        			graph.links.push(taElement);
				        		}
			        				
			        		} else {
				        		if(taElement.type === "node") {
				        			graph.nodes.splice(taIdx, 1, taElement);
				        		} else {
				        			graph.links.splice(taIdx, 1, taElement);
				        		}
			        		}
			        		$(this).dialog("close");
			        		
			        		// Update the model views
			        		showModel('#ta_model_edit', 300, 200);
							showTable('#ta_items_edit');
			        	}
			        
			        
		
			        }
				]
			}
		);
		
		$('#ta_type').change(function(){
			var type = $('#ta_type').val();
			if(type === "link") {
				$('.link_only').show();
			} else {
				$('.link_only').hide();
			}
		});
		
	});
	
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
		
		init: function(sId, fId, language, modelString) {
			local_sId = sId;
			local_fId = fId;
			available_questions = undefined;
			if(modelString) {
				graph = JSON.parse(modelString);
			} else {
				graph = {
						  "nodes":[],
						  "links":[],
						  "form": fId
						};
			}
			if(fId && language) {
				getAvailableQuestions(sId, fId, language);		// Get available questions for this export as an HTML option list
			}
		},
		showModel: function(element, width, height) {
			showModel(element, width, height);
		},
		showTable: function(element) {
			showTable(element);
		},
		startEdit: function() {
			$('#dialog_edit_ta').dialog("open");
		},
		clear: function(element) {
			$(element).empty();
		}
		
	}
	
	/*
	 * Show the node and link elements that make up the model in a table
	 */
	function showTable(element) {
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
					if(links[i].label) {
						h[++idx] = links[i].label.value;
					}
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
			showModel('#ta_model_edit', 300, 200);
			showTable('#ta_items_edit');
		});
		
		$(element).find('.ta_link_rem').click(function(){
			var linkIdx = $(this).val();
			links.splice(linkIdx, 1);
			showModel('#ta_model_edit', 300, 200);
			showTable('#ta_items_edit');
		});
		
		$(element).find('.ta_node_edit').click(function(){
			taIdx = $(this).val();
			taElement = nodes[taIdx];
			taElement.type = "node";
			taElement.isnew = false;
			setupTaElementEdit();
		});
		
		$(element).find('.ta_link_edit').click(function(){
			taIdx = $(this).val();
			taElement = links[taIdx];
			taElement.type = "link";
			taElement.isnew = false;
			setupTaElementEdit();
		});
		
		$('#ta_add').button().click(function(){
			taElement = {
					name: undefined,
					type: "node",
					isnew: true,
					group: 1,
		        	label: {
		        		   
		        	},
		        	properties: [],
		        	source: {},
		        	target: {},
		        	value: 1
		           
			};
			
			setupTaElementEdit();
		});
		
		
	}
	
	function setupTaElementEdit() {
		var	i,
			h,
			idx = -1;
		
		if(taElement.label) {
			$('#ta_name').val(taElement.label.value);
		}
		$('#ta_type').val(taElement.type);
		
		// Add Properties
		h = [];
		for(i = 0; i < availableQuestions.length; i++) {
			h[++idx] = '<tr>';
				h[++idx] = '<td>';
					if(availableQuestions[i].q) {
						h[++idx] = availableQuestions[i].q;
					} else {
						h[++idx] = availableQuestions[i].name;
					}
				h[++idx] = '</td>';
				h[++idx] = '<td>';
					h[++idx] = '<input class="question" type="checkbox" id="ta_prop_';
					h[++idx] = availableQuestions[i].name;
					h[++idx] = '" value="';
					h[++idx] = availableQuestions[i].name;
					h[++idx] = '" data-type="';
					h[++idx] = availableQuestions[i].type;
					h[++idx] = '" data-id="';
					h[++idx] = availableQuestions[i].id;
					h[++idx] = '"/>';
				h[++idx] = '</td>';
				h[++idx] = '<td>';
					h[++idx] = '<input class="unique" type="checkbox"/>';
				h[++idx] = '</td>';
			h[++idx] = '</tr>';
		}
		$('#ta_property_list').html(h.join(''));
		
		if(taElement.properties) {
			for(i = 0; i < taElement.properties.length; i++) {
				$('#ta_prop_' + taElement.properties[i].colName).prop('checked',true);
				if(taElement.properties[i].unique) {
					$('#ta_prop_' + taElement.properties[i].colName).parent().next().find('.unique').prop('checked',true);
				}
			}
		}
		
		// Add From / to link selections
		h=[];
		for(i = 0; i < graph.nodes.length; i++) {
			h[++idx] = '<option value="';
			h[++idx] = i;
			h[++idx] = '">';
			h[++idx] = graph.nodes[i].name;
			h[++idx] = '</option>';
		}
		$('#ta_source,#ta_target').html(h.join(''));


		
		if(taElement.type === "link") {
			$('#ta_source').val(taElement.source.index);
			$('#ta_target').val(taElement.target.index);
			$('.link_only').show();
		} else {
			$('.link_only').hide();
		}

		$('#dialog_element_edit_ta').dialog("open");
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
	
	function getAvailableQuestions(sId, fId, language) {
		addHourglass();
		$.ajax({
			url: "/surveyKPI/questionList/" + sId + "/" + language + "/" + fId,
			dataType: 'json',
			cache: false,
			success: function(data) {
				removeHourglass();
				var h=[],
					idx = -1,
					i;
				availableQuestions = data;
				
			},
			error: function(xhr, textStatus, err) {
				removeHourglass();
				if(xhr.readyState == 0 || xhr.status == 0) {
		              return;  // Not an error
				} else {
					alert("Error: Failed to get list of questions: " + err);
				}
			}
		});	
	}
	
	/*
	 * Remove properties from the graph that do not need to be stored
	 */
	function cleanGraph() {
		var i,
			elem,
			newGraph = {
				nodes: [],
				links: [],
				form: local_fId
		}
		
		for(i = 0; i < graph.nodes.length; i++) {

			newGraph.nodes.push({
				name: graph.nodes[i].name,
				group: graph.nodes[i].group,
				label: graph.nodes[i].label,
				properties: graph.nodes[i].properties
			});
			
			checkElem(newGraph.nodes[i]);	// Fix up graph
		}
		for(i = 0; i < graph.links.length; i++) {
			
			newGraph.links.push({
				source: graph.links[i].source.index,
				target: graph.links[i].target.index,
				value: graph.links[i].value,
				label: graph.links[i].label,
				properties: graph.links[i].properties	
			});
			
			checkElem(newGraph.links[i]);	// Add a generated unique key if one is not in the data
		}
		console.log("Clean graph");
		console.log(newGraph);
		return JSON.stringify(newGraph);
	}
	
	/*
	 * 
	 *  1)Every node and link must have a unique key during upload. 
	 *    A generated unique id will be used if the uniqueness is not in the data
	 *  2) Set the key to the column name if it is null
	 *  3) Clean keys to conform to neo4j naming conventions (avoid using back ticks)
	 */
	function checkElem(elem) {
		var i,
			hasUnique = false;
		
		// Fix properties
		for(i = 0; i < elem.properties.length; i++) {
			if(elem.properties[i].unique) {
				hasUnique = true;
			}
			if(!elem.properties[i].key) {
				elem.properties[i].key = elem.properties[i].colName;
			}
			//elem.properties[i].key = "`" + elem.properties[i].key + "`"; 

		}
		if(!hasUnique) {
			elem.properties.push ({
				key: "suid",				// Smap unique id, unique within Smap
				value_type: "record_suid",
				unique: true,
				colName: "suid"
				}
			);
		}
		
		// Fix other values
		//elem.label.value = "`" + elem.label.value + "`";
	}
	
});