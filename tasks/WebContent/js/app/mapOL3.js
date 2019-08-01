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
 * Open layers 3 functions
 */

"use strict";

define([
        'jquery',
        'modernizr',
        'localise',
        'globals',
        'icheck'],
    function ($, modernizr, lang, globals) {


        var gMap,
            gLayers = [],
            gVectorSources = [],
            gVectorLayers = [],
            gMapUpdatePending = true,
            gSelectFeature;

        return {
            init: init,
            setLayers: setLayers,
            refreshLayer: refreshLayer,
            refreshAllLayers: refreshAllLayers,
            saveLayer: saveLayer,
            deleteLayers: deleteLayers,
            initDynamicMap: initDynamicMap,
            clearSelectFeatures: clearSelectFeatures,
            setSelectedFeature: setSelectedFeature
        };

        function deleteLayers() {
            if(gLayers) {
                for(i = 0; i < gLayers.length; i++) {
                    deleteLayer(i);
                }
            }
        }

        function setLayers(layers) {

            var i;

            console.log("====== set layers: ");

            /*
             * Delete existing layers
             */
            deleteLayers();

            /*
             * Add new layers
             */
            gLayers = layers;
            // Add default data layer
            var layer = {
                title: localise.set["c_data"],
                local: true,
                clump: undefined,
                enabled: true,
                fixed: true
            };
            if(gLayers.length < 2) {
                gLayers.push(layer);
            } else {
                gLayers[0] = layer;     // replace
            }
            layer = {
                title: localise.set["c_heatmap"],
                local: true,
                clump: "heatmap",
                enabled: false,
                fixed: true
            };
            if(gLayers.length < 2) {
                gLayers.push(layer);
            } else {
                gLayers[1] = layer;     // replace
            }

            showLayerSelections();
        }

        function init(selectCallback) {

            // Add the map
            if (!gMap) {

                gMap = new ol.Map({
                    target: 'map',
                    layers: [
                        new ol.layer.Group({
                            'title': 'Base maps',
                            layers: [
                                new ol.layer.Tile({
                                    title: 'HOT',
                                    type: 'base',
                                    visible: true,
                                    source: new ol.source.OSM({
                                        url: 'http://{a-c}.tile.openstreetmap.fr/hot/{z}/{x}/{y}.png'
                                    })
                                }),
                                new ol.layer.Tile({
                                    title: 'OSM',
                                    type: 'base',
                                    visible: true,
                                    source: new ol.source.OSM()
                                })
                            ]
                        })
                    ],
                    view: new ol.View(
                        {
                            center: ol.proj.transform([0.0, 0.0], 'EPSG:4326', 'EPSG:3857'),
                            zoom: 1,
                            maxZoom: 21
                        }
                    )


                });


                // Add additional maps specified in the shared resources page
                var sharedMaps = globals.gSelector.getSharedMaps();
                if(!sharedMaps) {
                    getMapboxDefault(getSharedMapsOL3, gMap);
                } else {
                    addSharedMapsOL3(gMap, sharedMaps)
                }

                var layerSwitcher = new ol.control.LayerSwitcher({
                    tipLabel: 'Legend' // Optional label for button
                });
                gMap.addControl(layerSwitcher);

                $('#layerEdit').on('shown.bs.modal', function () {
                    $('#ml_title').focus();
                });

                // Show the layers selector
                $('#showlayers').click(function () {
                    globals.gMapLayersShown = !globals.gMapLayersShown;
                    if (globals.gMapLayersShown) {
                        $('#map_content').removeClass("col-md-12").addClass("col-md-8");
                        $('.map_layers').show();
                        gMap.updateSize();
                    } else {
                        $('#map_content').removeClass("col-md-8").addClass("col-md-12");
                        $('.map_layers').hide();
                        gMap.updateSize();
                    }
                });

                // Respond to clicks
                // select interaction working on "click"
                gSelectFeature = new ol.interaction.Select({
                    condition: ol.events.condition.singleClick,
                    layers: function (layer) {
                        // defines layer from which features are selectable
                        return layer.get('id') == 'base';
                    }
                });
                gMap.addInteraction(gSelectFeature);
                gSelectFeature.on('select', selectCallback, this);

            }

            if (gMapUpdatePending) {
                refreshAllLayers(true);
            }
            showLayerSelections();


        }

        /*
         * Show layer selections on the screen
         */
        function showLayerSelections() {
            var h = [],
                idx = -1,
                i;

            for (i = 0; i < gLayers.length; i++) {
                h[++idx] = '<tr>';

                h[++idx] = '<td>';      // Select
                h[++idx] = '<div class="switch custom-control custom-checkbox">';
                h[++idx] = '<input type="checkbox" class="custom-control-input layerSelect" name="columnSelect"';
                if(gLayers[i].enabled) {
                    h[++idx] = 'checked="checked"';
                }
                h[++idx] = ' value="';
                h[++idx] = i;
                h[++idx] = '"';
                h[++idx] = ' id="layer' + i + '"';
                h[++idx] = '>';
                h[++idx] = '<label class="custom-control-label" for="layer' + i + '">';
                h[++idx] = gLayers[i].title;
                h[++idx] = '</label>';
                h[++idx] = '</div>';
                h[++idx] = '</td>';
                h[++idx] = '<td>';      // Delete
                h[++idx] = '<button type="button" data-idx="';
                h[++idx] = i;
                h[++idx] = '" class="btn btn-default btn-sm rm_layer danger"';
                if(gLayers[i].fixed) {
                    h[++idx] = ' disabled';
                }
                h[++idx] = '>';
                h[++idx] = '<span class="glyphicon glyphicon-trash" aria-hidden="true"></span></button>';
                h[++idx] = '</td>';

                h[++idx] = '</tr>';
            }


            $('#layerSelect tbody').empty().html(h.join(''));
            //$('input', '#layerSelect tbody').iCheck({
            //    checkboxClass: 'icheckbox_square-green',
            //    radioClass: 'iradio_square-green'
            //});
            $('.rm_layer', '#layerSelect tbody').click(function() {
                var idx = $(this).data("idx");
                deleteLayer(idx);
                gLayers.splice(idx, 1);
                saveToServer(gLayers);
                showLayerSelections();
            });

            $('.layerSelect').change(function() {
                var $this = $(this);
                var index = $(this).val();
               if($this.prop('checked')) {
                   showLayer(index);
               } else {
                   hideLayer(index);
               }
            });
        }

        /*
         * Redisplay a single layer
         */
        function refreshLayer(index) {

            if (gMap) {
                var results = globals.gMainTable.rows({
                    order: 'current',  // 'current', 'applied', 'index',  'original'
                    page: 'all',      // 'all',     'current'
                    search: 'applied',     // 'none',    'applied', 'removed'
                }).data();

                updateSingleLayer(index, results);
            }
        }

        /*
         * Redisplay all layers
         */
        function refreshAllLayers(mapView) {
            console.log("====== refresh all layers: " + mapView);
            if (mapView) {
                if (gMap) {
                    var i;
                    var results = globals.gMainTable.rows({
                        order: 'current',  // 'current', 'applied', 'index',  'original'
                        page: 'all',      // 'all',     'current'
                        search: 'applied',     // 'none',    'applied', 'removed'
                    }).data();

                    for (i = 0; i < gLayers.length; i++) {
                        deleteLayer(i);
                        updateSingleLayer(i, results);
                    }
                }
                gMapUpdatePending = false;
            } else {
                gMapUpdatePending = true;
            }
        }

        /*
         * Set up a single layer
         * This function is called by both refreshLayer and refreshAllLayers as refreshLayer has to do some setup
         *  that is also done by refreshAllLayers
         */
        function updateSingleLayer(index, results) {

            var layer = gLayers[index];
            var geoJson = getGeoJson(results, layer);		// Get a geoson of data
            //var styles = getStyles(layer);					// Get the styles
            var defaultStyle = new ol.style.Style({
                fill: new ol.style.Fill({
                    color: 'rgba(255, 100, 50, 0.3)'
                }),
                stroke: new ol.style.Stroke({
                    width: 2,
                    color: 'rgba(255, 10, 10, 0.8)'
                }),
                image: new ol.style.Circle({
                    fill: new ol.style.Fill({
                        color: 'rgba(255, 0, 0, 1.0)'
                    }),
                    stroke: new ol.style.Stroke({
                        width: 1,
                        color: 'rgba(255, 255, 255, 0.8)'
                    }),
                    radius: 7
                })
            });

            if (!gVectorSources[index]) {
                gVectorSources[index] = new ol.source.Vector();
            } else {
                gVectorSources[index].clear();
            }
            gVectorSources[index].addFeatures((new ol.format.GeoJSON()).readFeatures(geoJson,
                {
                    dataProjection: 'EPSG:4326',
                    featureProjection: 'EPSG:3857'
                }));


            if (layer.clump === "heatmap") {
                gVectorLayers[index] = new ol.layer.Heatmap({
                    source: gVectorSources[index],
                    radius: 5,
                    id: 'heatmap'
                });
            } else {
                gVectorLayers[index] = new ol.layer.Vector({
                    source: gVectorSources[index],
                    style: [defaultStyle],
                    id: 'base'
                });
            }

            if(layer.enabled) {
                gMap.addLayer(gVectorLayers[index]);
            }
            gMap.getView().fit(gVectorSources[index].getExtent(), gMap.getSize());
        }

        function deleteLayer(index) {

            if (gVectorLayers[index]) {
                gMap.removeLayer(gVectorLayers[index]);
                gVectorSources.splice(index, 1);
                gVectorLayers.splice(index, 1);
            }

        }

        function hideLayer(index) {
            gMap.removeLayer(gVectorLayers[index]);
            saveToServer(gLayers);
        }

        function showLayer(index) {
            gMap.addLayer(gVectorLayers[index]);
            saveToServer(gLayers);
        }

        /*
         * Save a layer after the user specifies it in the layer dialog
         */
        function saveLayer() {

            var title = $('#ml_title').val(),
                local = $('#usecurrent_tabledata').is(':checked'),
                layer = {};

            // Validation
            if (typeof title === "undefined" || title.trim().length === 0) {
                $('#layerInfo').show().removeClass('alert-success').addClass('alert-danger').html(localise.set["mf_tr"]);
                return false;
            }

            layer.title = title;
            layer.local = local;
            layer.clump = $('input[name=clump]:checked', '#mapForm').val();

            gLayers.push(layer);
            $('#layerEdit').modal("hide");	// All good close the modal

            refreshLayer(gLayers.length - 1);
            saveToServer(gLayers);
            showLayerSelections();

        };

        /*
         * Process the map data according to the layer specification
         */
        function getGeoJson(results, layer) {

            var i, j, k, m;

            var geoJson = {
                type: "FeatureCollection",

                features: []
            };

            for (i = 0; i < results.length; i++) {

                var keep = false;   // default

                if (!results[i]._geolocation) {                      // Invalid Geometry
                    keep = false;
                } else {
                    // Do not show points at 0,0
                    if(results[i]._geolocation.type === "Point") {
                        for (j = 1; j < results[i]._geolocation.coordinates.length; j++) {
                            if (results[i]._geolocation.coordinates[j] != 0) {
                                keep = true;                            // At least one non zero geometry
                                break;
                            }
                        }
                    } else  if(results[i]._geolocation.type === "LineString") {
                        for (j = 0; j < results[i]._geolocation.coordinates.length; j++) {

                            for(k = 0; k < results[i]._geolocation.coordinates[j].length; k++) {
                                if (results[i]._geolocation.coordinates[j][k] != 0) {
                                    keep = true;                            // At least one non zero geometry
                                    break;
                                }
                            }
                        }
                    } else if(results[i]._geolocation.type === "Polygon") {
                        for (j = 0; j < results[i]._geolocation.coordinates.length; j++) {

                            for(k = 0; k < results[i]._geolocation.coordinates[j].length; k++) {
                                for(m = 0; m < results[i]._geolocation.coordinates[j][k].length; m++) {
                                    if (results[i]._geolocation.coordinates[j][k][m] != 0) {
                                        keep = true;                            // At least one non zero geometry
                                        break;
                                    }
                                }
                            }
                        }

                    }


                }

                if (keep) {
                    geoJson.features.push(
                        {
                            "type": "Feature",
                            //"geometry": {"type": "Point", "coordinates": results[i]._geolocation},
                            "geometry": results[i]._geolocation,
                            "properties": {
                                record: i
                            }
                        });
                }
            }

            return geoJson;
        }

        /*
         * Save the layers to the server
         */
        function saveToServer(layers) {

            var saveString = JSON.stringify(layers);
            var viewId = globals.gViewId || 0;
            var url = "/surveyKPI/surveyview/" + viewId;
            url += '?survey=' + globals.gCurrentSurvey;
            url += '&managed=' + 0;
            url += '&query=' + 0;

            addHourglass();
            $.ajax({
                type: "POST",
                dataType: 'json',
                contentType: "application/json",
                cache: false,
                url: url,
                data: {mapView: saveString},
                success: function (data, status) {
                    removeHourglass();
                    //if(globals.gViewId != data.viewId) {  // Store data under new viewId
                    //    gTasks.cache.surveyConfig[data.viewId] = gTasks.cache.surveyConfig[globals.gViewId];
                    //    globals.gViewId = data.viewId;
                    //}
                }, error: function (data, status) {
                    removeHourglass();
                    alert(localise.set["msg_err_save"] + " " + data.responseText);
                }
            });
        }

        /*
         * Get the shared maps from the server
         */
        function getSharedMapsOL3(map) {

            var url = '/surveyKPI/shared/maps';
            $.ajax({
                url: url,
                dataType: 'json',
                cache: false,
                success: function(data) {
                    globals.gSelector.setSharedMaps(data);
                    addSharedMapsOL3(map, data);
                },
                error: function(xhr, textStatus, err) {
                    if(xhr.readyState == 0 || xhr.status == 0) {
                        return;  // Not an error
                    } else {
                        alert("Error: Failed to get list of shared maps: " + err);
                    }
                }
            });

        }

        /*
         * Add shared maps
         */
        function addSharedMapsOL3(map, sharedMaps) {

            var i,
                layerUrl,
                layer;

            if(sharedMaps) {


                var baseLayers = map.getLayers().item(0).getLayers().getArray();


                for(i = 0; i < sharedMaps.length; i++) {

                    layer = sharedMaps[i];

                    if(layer.type === "mapbox") {
                        //layerUrl = 'http://api.tiles.mapbox.com/v4/' + layer.config.mapid + ".jsonp?access_token=" + globals.gMapboxDefault;
                        layerUrl = "http://a.tiles.mapbox.com/v4/" + layer.config.mapid + "/{z}/{x}/{y}.png?access_token=" + globals.gMapboxDefault;
                        baseLayers.unshift(new ol.layer.Tile( {
                            title: layer.name,
                            type: 'base',
                            visible: false,
                            source: new ol.source.OSM({
                                url: layerUrl,
                                crossOrigin: 'anonymous'
                            })
                        }));

                    } /* else if(layer.type === "vector") {
                        layerUrl = "/surveyKPI/file/" + layer.config.vectorData + "/organisation";
                        var vectorLayer = new OpenLayers.Layer.Vector(layer.name + ".", {
                            projection: "EPSG:4326",
                            strategies: [new OpenLayers.Strategy.Fixed()],
                            protocol: new OpenLayers.Protocol.HTTP({
                                url: layerUrl,
                                format: new OpenLayers.Format.GeoJSON()
                            })
                        });
                        map.addLayer(vectorLayer);

                    }
                    */
                }
            }
        }


        /*
         ********************************
         * Functions for dynamic maps
         */

        function initDynamicMap (config, setUserLocation) {

            // Add the map
            if (!config.map) {

                config.map = new ol.Map({
                    target: config.id,
                    layers: [
                        new ol.layer.Group({
                            'title': 'Base maps',
                            layers: [
                                new ol.layer.Tile({
                                    title: 'HOT',
                                    type: 'base',
                                    visible: true,
                                    source: new ol.source.OSM({
                                        url: 'http://{a-c}.tile.openstreetmap.fr/hot/{z}/{x}/{y}.png'
                                    })
                                }),
                                new ol.layer.Tile({
                                    title: 'OSM',
                                    type: 'base',
                                    visible: true,
                                    source: new ol.source.OSM()
                                })
                            ]
                        })
                    ],
                    view: new ol.View(
                        {
                            center: ol.proj.transform([0.0, 0.0], 'EPSG:4326', 'EPSG:3857'),
                            zoom: 1,
                            maxZoom: 21
                        }
                    )

                });

                // Add the vector layer
                var features = [];
                if(config.currentValue) {
                    var currentValue = {
                        "type": "Feature",
                        "geometry": config.currentValue,
                        "properties": {
                            "type": (config.readOnly ? "current" : "old"),      // If editable then set the curent value as the old
                            "label": localise.set["c_to"]
                        }
                    };

                    features.push(currentValue);
                }
                if(config.readOnly && config.oldValue) {       // Only care about old value if we are reviewing and not editing
                    var oldValue = {
                        "type": "Feature",
                        "geometry": config.oldValue,
                        "properties": {
                            "type": "old",
                            "label": localise.set["c_from"]
                        }
                    };

                    features.push(oldValue);
                }

                var collection = {
                    "type": "FeatureCollection",
                    features: features
                };
                window.gStyleFn = function(feature, resolution) {

                    var pointFill;
                    var areaFill;
                    var line;

                    if(feature.get('type')=== 'current') {
                        pointFill = 'rgba(255, 0, 0, 1.0)';
                        areaFill = 'rgba(255, 100, 50, 0.3)';
                        line = 'rgba(255, 10, 10, 0.8)';
                    } else if(feature.get('type')=== 'old') {
                        pointFill = 'rgba(0, 0, 255, 1.0)';
                        areaFill = 'rgba(50, 100, 255, 0.3)';
                        line = 'rgba(10, 10, 255, 0.8)';
                    } else {
                        pointFill = 'rgba(0, 255, 255, 1.0)';
                        areaFill = 'rgba(10, 200, 255, 0.3)';
                        line = 'rgba(10, 200, 255, 0.8)';
                    }

                    return [new ol.style.Style({
                        fill: new ol.style.Fill({
                            color: areaFill
                        }),
                        stroke: new ol.style.Stroke({
                            width: 2,
                            color: line
                        }),
                        image: new ol.style.Circle({
                            fill: new ol.style.Fill({
                                color: pointFill
                            }),
                            stroke: new ol.style.Stroke({
                                width: 2,
                                color: 'rgba(100, 100, 100, 0.8)'
                            }),
                            radius: 7
                        })
                    })];
                };

                if(collection.features.length > 0) {

                    var source = new ol.source.Vector();
                    try {
                        source.addFeatures((new ol.format.GeoJSON()).readFeatures(collection,
                            {
                                dataProjection: 'EPSG:4326',
                                featureProjection: 'EPSG:3857'
                            }));

                        var layer = new ol.layer.Vector({
                            source: source,
                            style: gStyleFn
                        });
                        config.map.addLayer(layer);

                        config.map.getView().fit(source.getExtent(), config.map.getSize());

                    } catch(err) {

                    }

                }

                var layerSwitcher = new ol.control.LayerSwitcher({
                    tipLabel: 'Legend' // Optional label for button
                });
                config.map.addControl(layerSwitcher);


                if(setUserLocation) {
                    config.geolocation = new ol.Geolocation({
                        projection: config.map.getView().getProjection(),
                        tracking: true,
                        trackingOptions: {
                            enableHighAccuracy: true,
                            maximumAge: 2000
                        }
                    });

                    var iconStyle = new ol.style.Style({
                        image: new ol.style.Icon(({
                            anchor: [0.5, 25],
                            anchorXUnits: 'fraction',
                            anchorYUnits: 'pixels',
                            opacity: 1.0,
                            src: '/js/libs/OpenLayers/img/marker-gold.png'
                        }))
                    });

                    // add an empty iconFeature to the source of the layer
                    var iconFeature = new ol.Feature();
                    var iconSource = new ol.source.Vector({
                        features: [iconFeature]
                    });
                    var iconLayer = new ol.layer.Vector({
                        source: iconSource,
                        style : iconStyle
                    });
                    config.map.addLayer(iconLayer);

                    config.geolocation.on('change', function() {
                        var pos = config.geolocation.getPosition();
                        var view = config.map.getView();
                        iconFeature.setGeometry(new ol.geom.Point(pos));
                        view.setCenter(pos);
                        view.setZoom(18);
                    });

                }

                /*
                 * Add events
                 */
                config.map.on('click', function(evt) {

                    var noFeature = true;
                    var id = config.id;
                    var $tooltip = $('#tooltip_' + id);

                    config.map.forEachFeatureAtPixel(evt.pixel, function(feature, layer) {

                        $tooltip.html(feature.getProperties().label);
                        $tooltip.css(
                            {
                                position:"absolute",
                                left:evt.pixel[0],
                                top:evt.pixel[1],
                                "z-index": 20000,
                                "background-color": "white",
                                "padding": 2
                            }).show();
                        noFeature = false;

                    });

                    if(noFeature) {
                        $tooltip.hide();

                        if(!config.readOnly) {
                            var coord = ol.proj.transform(evt.coordinate, "EPSG:900913", 'EPSG:4326');
                            var newValue = {
                                itemIndex: $('#' + id).data('item'),
                                value: {
                                    coordinates: coord,
                                    type: "Point"
                                }
                            };

                            if(config.task) {
                                $('#taskPropertiesForm').trigger("smap_task::geopoint", newValue);
                            } else {
                                if(newValue.itemIndex) {
                                    $('#editRecordForm').trigger("smap::geopoint", newValue);
                                }
                            }

                            setSelectedFeature(config, evt.coordinate, undefined, undefined, false);

                        }
                    }

                });

            } else {
                console.log('Map ' + config.id + ' already initialised');
            }


        }

        function clearSelectFeatures(config) {
            if(config.selectSource) {
                config.selectSource.clear();
                config.map.removeLayer(config.selectLayer);
                config.selectGeometry = undefined;
                config.selectSource = undefined;
                config.selectLayer = undefined;
            }

        }

        function setSelectedFeature(config, coordinate, lon, lat, recenter) {

            if(!coordinate) {
                var coord = [];
                coord.push(lon);
                coord.push(lat);
                // Convert lat lon to coordinates
                coordinate = ol.proj.transform(coord,'EPSG:4326', "EPSG:900913");

            }
            if(config.selectLayer !== undefined) {
                config.selectGeometry.setCoordinates(coordinate);
            } else {
                config.selectGeometry = new ol.geom.Point(coordinate);
                var selectFeature = new ol.Feature({
                    geometry: config.selectGeometry,
                    type: "current",
                    label: localise.set["c_to"]
                });
                config.selectSource = new ol.source.Vector({
                    features: [selectFeature]
                });
                config.selectLayer = new ol.layer.Vector({
                    source: config.selectSource,
                    style: gStyleFn
                });
                config.map.addLayer(config.selectLayer);
            }
            if(recenter) {
                config.map.getView().fit(config.selectSource.getExtent(), config.map.getSize());
            }
        }


    });


