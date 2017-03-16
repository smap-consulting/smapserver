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
 * Chart functions
 */

"use strict";

define([
         'jquery',
         'modernizr',
         'localise',
         'globals'], 
		function($, modernizr, lang, globals) {

	var gLayers = [];
	
	return {
		init: init,
		setLayers: setLayers
	};
	
	function setLayers(layers) {
		gLayers = layers;
	}

	function init() {
			
		 // Create osm layer
		 var osm = new ol.layer.Tile({source: new ol.source.OSM()}); 
		 
		 // Add the map	
		 if(!gMap) {
			 gMap = new ol.Map({
		        target: 'map',
		        layers: [
		                         new ol.layer.Group({
		                             'title': 'Base maps',
		                             layers: [
		                                 new ol.layer.Group({
		                                     title: 'Water color with labels',
		                                     type: 'base',
		                                     combine: true,
		                                     visible: false,
		                                     layers: [
		                                         new ol.layer.Tile({
		                                             source: new ol.source.Stamen({
		                                                 layer: 'watercolor'
		                                             })
		                                         }),
		                                         new ol.layer.Tile({
		                                             source: new ol.source.Stamen({
		                                                 layer: 'terrain-labels'
		                                             })
		                                         })
		                                     ]
		                                 }),
		                                 new ol.layer.Tile({
		                                     title: 'Water color',
		                                     type: 'base',
		                                     visible: false,
		                                     source: new ol.source.Stamen({
		                                         layer: 'watercolor'
		                                     })
		                                 }),
		                                 new ol.layer.Tile({
		                                     title: 'OSM',
		                                     type: 'base',
		                                     visible: true,
		                                     source: new ol.source.OSM()
		                                 })
		                             ]
		                         }),
		                         new ol.layer.Group({
		                             title: 'Overlays',
		                             layers: [
		                                 new ol.layer.Tile({
		                                     title: 'Countries',
		                                     source: new ol.source.TileWMS({
		                                         url: 'http://demo.opengeo.org/geoserver/wms',
		                                         params: {'LAYERS': 'ne:ne_10m_admin_1_states_provinces_lines_shp'},
		                                         serverType: 'geoserver'
		                                     })
		                                 })
		                             ]
		                         })
		                     ],
		        view: new ol.View(
		        		{
		        			center: ol.proj.transform([0.0, 0.0], 'EPSG:4326', 'EPSG:3857'),
		        			zoom: 1
		        		}
		        	)
		      });
			 
			 var layerSwitcher = new ol.control.LayerSwitcher({
			        tipLabel: 'Legend' // Optional label for button
			    });
			 gMap.addControl(layerSwitcher);
			 
			// Add a new map layer
			$('.addlayer').click( function () {
				addLayer();
			});
				
			$('#addLayerSave').click(function() {
				saveLayer(); 
			});
			
			// Show the layers selector
			$('#showlayers').click( function () {
				gMapLayersShown = !gMapLayersShown;
				if(gMapLayersShown) {
					$('.main_content').removeClass("col-md-12").addClass("col-md-8");
					$('.map_layers').show();
				} else {
					$('.main_content').removeClass("col-md-8").addClass("col-md-12");
					$('.map_layers').hide();
				}
			});
		 }
		 
		
	 }
	 
	 function addLayer() {
		 $('#layerInfo').show();
		 $('#ml_title').val("");
		 $('#layerEdit').modal("show");
	 }
	 
	 function saveLayer() {
		 
		 var title = $('#ml_title').val(),
		 	local = $('#usecurrent_tabledata').is(':checked')
		 	layer = {};
		 	
		 // Validation
		 if(typeof title === "undefined" || title.trim().length === 0) {
			 $('#layerInfo').show().removeClass('alert-success').addClass('alert-danger').html(localise.set["mf_tr"]);
			 return false;
		 }
		 
		 layer.title = title;
		 layer.local = local;
		 
		 gLayers.push(layer);
		 
		 $('#layerEdit').modal("hide");	// All good close the modal
		
	 };

});
