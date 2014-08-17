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

/*
 * Purpose: Show a list of reports
 */

define(['jquery', 'jquery_ui', 'localise', 'common', 'globals'], function($, ui, lang, common, globals) {
	
var reportList = null,
	minDate = null,
	maxDate = null,
	exportCss =  '.post-image {width:600px;} ' +
		'table,th,td {border:1px solid black}',
	gAccessToken,		// Facebook Access Token
	gFacebookEnabled = false,
	gEmailEnabled = false,
	gTwitterEnabled = false,
	gData,
	gFeatures = [],
	gMap,
	gOverallBounds,
	gSelectResultsControl,
	gResultsLayer;

$(document).ready(function() {
	
	/*
	 * Initialise Facebook
	 */
	  window.fbAsyncInit = function() {
	      FB.init({
	        appId      : '563570780321012', // App ID
	    channelUrl : '//dev.smap.com.au/channel.php', // Channel File
	    status     : true, // check login status
	    cookie     : true, // enable cookies to allow the server to access the session
	    xfbml      : true  // parse XFBML
	  });
	  
	  // Get notified of the users logon status
	  FB.Event.subscribe('auth.statusChange', authStatusChange);
	};
	
	// Load the SDK Asynchronously
	(function(d){
	  var js, id = 'facebook-jssdk'; if (d.getElementById(id)) {return;}
	  js = d.createElement('script'); js.id = id; js.async = true;
	  js.src = "//connect.facebook.net/en_US/all.js";
	  d.getElementsByTagName('head')[0].appendChild(js);
	}(document));    
	    
	initializeReportsMap();
	
	 /*
	  * Get the user details so we have the default project
	  * Then load the available projects for the user and load the reports for the default project
	  */
	getLoggedInUser(getReportList, false, true);
	
	// Set change function on projects
	$('#project_name').change(function() {
		globals.gCurrentProject = $('#project_name option:selected').val();
		globals.gCurrentSurvey = -1;
		getReportList(globals.gCurrentProject);			// Get reports
		saveCurrentProject(globals.gCurrentProject, globals.gCurrentSurvey);	// Save the current project id
 	 });
	
	// Add function to show map
	$('#show_map').button().click(function() {
		var $map = $('#map'),
			$this = $(this);
		if($map.is(':visible')) {
			$this.button('option', 'label', 'Show Map');
		} else {
			$this.button('option', 'label', 'Hide Map');
		}
		$map.toggle();

	});
	
	// Add function to clear map selection
	$('#clear_bounds').button().click(function() {
		if(gCurrentBoundsLayer) {
			gMap.removeLayer(gCurrentBoundsLayer);
			gCurrentBoundsLayer = undefined;
			gSelectedBounds = undefined;
			zoomToData(gMap);
			updateListView();
		}
		$('#clear_bounds').button("option", "disabled", true);
	});
	$('#clear_bounds').button("option", "disabled", true);	// Default to disabled
	
	enableUserProfile();
});


/*
 * Get the reports for this project
 */
function getReportList(projectId) {
	if(projectId != -1) {
	 	$.ajax({
			url: "/surveyKPI/reports/list/" + projectId,
			dataType: 'json',
			cache: false,
			success: function(data) {
				gData = data;
				setEarliestLatest();
				updateListView();
				$( "#slider-range" ).slider('enable');	// Enable slider
				$('#word').click(function() {			// Enable export menu
					exportToFile(); 
				});
			},
			error: function(xhr, textStatus, err) {
				if(xhr.readyState == 0 || xhr.status == 0) {
		              return;  // Not an error
				} else {
					alert("Failed to retrieve reports");
				}
			}
		});
	}
}

function updateListView() {
	 
	// Get the allowed date range
	var firstDate = $( "#slider-range" ).slider( "values", 0 ),
		lastDate = $( "#slider-range" ).slider( "values", 1 ),
		html = [],
		th = -1,
		index,
		yesDate,
		yesLocation,
		postDate,
		featureIdx = -1,
		bbox = [],
		selectedBbox = [],
		selWidth,
		selHeight,
		selX,
		selY,
		bounds;
	
	gOverallBounds = new OpenLayers.Bounds();
	gFeatures = [];
	if(typeof gSelectedBounds !== "undefined") {
		selectedBbox = gSelectedBounds.
			transform(gMap.getProjectionObject(),new OpenLayers.Projection("EPSG:4326")).
			toArray();
		selWidth = selectedBbox[2] - selectedBbox[0];
		selHeight = selectedBbox[3] - selectedBbox[1];
		selX = selectedBbox[0] + (selWidth) / 2;
		selY = selectedBbox[1] + (selHeight) / 2;

	}

	// Build body
	for(index = 0; index < gData.length; index++) {
			
		yesDate = true;	// Default to true and pick up posts with no date
		postDate = gData[index].smap.epoch;
	
		if(postDate && postDate < firstDate || postDate > lastDate) {
			yesDate = false;
		}
		
		yesLocation = true;	// Default to true and pick up posts with no location	
		if(gSelectedBounds) {
			bbox = gData[index].smap.bbox;
			if(selectedBbox && bbox && bbox[0]) {
				if(!boxesIntersect(selX, selY, selWidth, selHeight, bbox)) {
					yesLocation = false;
				}
			} else {
				console.log("Undefined location elements:" + index);
				yesLocation = true;
			}
		}

		if(yesDate && yesLocation) {
			
			var numberPanels = 0;
			html[++th] = '<div class="post">';
			
			// Add publishing box
			html[++th] = '<div class="post-right">';
			var reportUrl = gData[index].smap.report_url;

			if(gEmailEnabled) {
				var mailtoSubject = escape(gData[index].title);
				var desc = gData[index].smap.description;
				if(desc && desc.length > 100) {	// Only send first 100 characters of description
					desc = desc.substring(0,100);
				}
				var mailtoBody = escape(reportUrl + '\n\n' + desc);
				html[++th] = '<a class="publish_button email_button" href="mailto:?subject=' + mailtoSubject + '&body=' + mailtoBody + '">Email</a>';
			}
			if(gFacebookEnabled) {
				html[++th] = '<button class="publish_button fb_post_button" type="button" value="';
				html[++th] = index;
				html[++th] = '">Facebook</button>';
			}
			if(gTwitterEnabled) {
				var hashtags = "#smaptesting";
				var tweetTitle = gData[index].title;
				var tweetLength = hashtags.length + reportUrl.length + tweetTitle.length;
				if(tweetLength + 2 >= 140) {
					tweetTitle = tweetTitle.substring(0, tweetTitle.length + tweetLength - 140);
				}

				html[++th] = '<a class="publish_button twitter_button" href="https://twitter.com/home?status=';
				html[++th] = encodeURIComponent(hashtags + ' ' + tweetTitle + ' ' + reportUrl);
				html[++th] = '" target="_blank"><img src="img/tweetthis.gif"  height="30" width="100" border="0" alt="Tweet This!"></a>';
				
			}
			html[++th] = '</div>';
			numberPanels++;
			
			// Add images
			if(gData[index].type == "table") {
				picUrl = 'img/table_ico.png';
			} else {
				picUrl = gData[index].thumbnail_url;
			}
			
			html[++th] = '<div class="post-right">';
			if(typeof picUrl !== "undefined") {
				html[++th] = '<a href="' + reportUrl +
						'" target="_blank"><img class="post-image" src="' +
						picUrl + '" alt="Article"/></a>';
			}
			html[++th] = '</div>';
			numberPanels++;

			
			// Add Meta data box
			html[++th] = '<div class="post-left">';
			html[++th] = '<p class="post-item">Title: <a href="' + reportUrl + '" target="_blank">' + gData[index].title + '</a></p>'; 
			html[++th] = '<p class="post-item">Author: ' + gData[index].author_name + '</p>';
			html[++th] = '<p class="post-item">Location: ' + gData[index].smap.country +
					',' + gData[index].smap.region + ',' + gData[index].smap.district + 
					',' + gData[index].smap.community +'</p>';
			html[++th] = '<p class="post-item">Published: ' + gData[index].smap.pub_date + '</p>';
			html[++th] = '<button class="edit_button" type="button" value="';
			html[++th] = gData[index].smap.ident;
			html[++th] = '">Edit</button>';
			html[++th] = '<button class="delete_button" type="button" value="';
			html[++th] = gData[index].smap.ident;
			html[++th] = '">Delete</button>';
			html[++th] = '</div>';
			
			// Calculate available width for description
			var windowWidth = $(window).width();
			var otherSpace = 400 + 110 * numberPanels;
			var availableSpace = windowWidth - otherSpace - 100;	
			
			if(availableSpace > 0) {	
				// Add description box
				html[++th] = '<div class="post-middle" ';
				html[++th] = 'style="width: ';
				html[++th] = availableSpace;
				html[++th] = 'px;">';
				html[++th] = '<p>';
				html[++th] = gData[index].smap.description;	// TODO format description
				html[++th] = '</p>';
				html[++th] = '</div>';
			}
						
			html[++th] = '</div>';
			
			/*
			 * Add the location of the report to the map
			 */
			bbox = gData[index].smap.bbox;
			if(bbox && bbox[0]) {
				bounds = new OpenLayers.Bounds(bbox);
				gFeatures[++featureIdx] = new OpenLayers.Feature.Vector(
						bounds.toGeometry(), 
						{
							category: "a"
						});

				gFeatures[featureIdx].geometry.transform(new OpenLayers.Projection("EPSG:4326"), new OpenLayers.Projection("EPSG:900913"));
			}			
		}
	}
	
	// Write the table content
	$tab_content = $('#report_content'); 
	$tab_content.empty();
	$tab_content.html(html.join(''));
	
	$('.email_button').button({icons: {primary: "ui-icon-mail-closed"}});	// Add icons to email button
	$('.delete_button').button().on("click", function() {reportDelete($(this).val());});
	$('.edit_button').button().on("click", function() {window.location.href = '/fieldAnalysis?ident=' + $(this).val() + '&projectId=' + globals.gCurrentProject;});
	$('.fb_post_button').button().on("click", function(){
		gIndex = $(this).val(); 
		$('#fb_groups').dialog("open");
	});
	
    /*
 	 * Add html markup to URLs
 	 * From http://adamzwakk.com/?p=639
 	 */

	var siteRegEx = /(\b(https?|ftp|file):\/\/[-A-Z0-9+&@#\/%?=~_|!:,.;]*[-A-Z0-9+&@#\/%=~_|])/ig;
	$(".post-middle").find("p").filter(function() {
		return $(this).html().match(siteRegEx);
	}).each(function() {
		$(this).html($(this).html().replace(siteRegEx, "<a href=\"$1\">$1</a>"));
	});
	
 	// Add paragraphs to text
	$("p").each(function() {
		$(this).html($(this).html().replace('\n\n', '</p><p>'));
	});
	
	// Load the map features and enable feature selection
	if(typeof gResultsLayer !== "undefined") {
		gMap.removeLayer(gResultsLayer);
		gResultsLayer = undefined;
	}
	if(featureIdx >= 0) {
		setMapReports();
	}
	
}

function reportDelete(ident) {
	
	var msg = "Are you sure you want to delete this report?";
	var decision = confirm(msg);
	if (decision == true) {
		
		$.ajax({
			type : 'Delete',
			url : "/surveyKPI/reports/report/" + globals.gCurrentProject + "/" + ident,
			error : function() {
				alert("Error: Failed to delete");
			},
			success : function() {
				window.location.reload(true);
			}
		});
	}
}

/*
 * Put the contents of the table into a file
 */
function exportToFile() {
	htmlHead1 = '<!DOCTYPE html><html><head><title>Smap</title><style type="text/css">';
	htmlHead2 = '</style></head><body>';
	htmlTail = '</body></html>';
	textOutput = htmlHead1 + exportCss + htmlHead2 + $('#report_content').html() + htmlTail;
	window.location.href='data:application/msword;charset=UTF-8,' + encodeURIComponent(textOutput);
}

/*
 * Set the earliest and latest report dates
 */
function setEarliestLatest() {
	var index,
		postDate;	// publish date in seconds since 1/1/1970
	
	for(index = 0; index < gData.length; index++) {
		postDate = gData[index].smap.epoch;
		if(postDate) {
			if(minDate == null || postDate < minDate) {minDate = postDate;}
			if(maxDate == null || postDate > maxDate) {maxDate = postDate;}
		}
	}
	
	// initialise slider
	$( "#slider-range" ).slider({
		range: true,
		min: minDate,
		max: maxDate,
		values: [ minDate, maxDate ],
		slide: function( event, ui ) {
			var v1 = getDateString(ui.values[0]);
			var v2 = getDateString(ui.values[1]);
			$("#dates_field").attr("value", v1  + " / " + v2 );
		},
		stop: function(event, ui) {
			updateListView();
		}
	});

	$("#dates_field").attr("value", getDateString($( "#slider-range" ).slider( "values", 0 )) + " / " +
		getDateString($( "#slider-range" ).slider( "values", 1 ) ));  
} 

// Get the date as a string
function getDateString(unixDate) {
	var uDate = new Date(unixDate * 1000),
		dateDay = uDate.getDate(),
		dateMonth = uDate.getMonth() + 1,
		dateYear = uDate.getFullYear();
	
	// Todo make this locale dependent
	return dateYear + "-" + dateMonth + "-" + dateDay;
} 

/*
 * Get the access token
 */
function authStatusChange(response) {
	
	gAccessToken = FB.getAuthResponse().accessToken;
	// Get the new list of groups
	$.ajax({		// TODO handle paging, however a lot of groups should be returned in first page, may never be necessary
		type : 'get',
		dataType: 'json',
		url : "https://graph.facebook.com/me/groups?access_token=" + gAccessToken,
		error : function(xhr, textStatus, err) {
			if(xhr.readyState == 0 || xhr.status == 0) {
	              return;  // Not an error
			} else {
				alert("Error: Failed to get facebook groups");
			}
			
		},
		success : function(groups) {
			var html = [],
				th = -1,
				i,
				data;
			
			data = groups.data;
			for(i = 0; i < data.length; i++) {
				html[++th] = '<option value="';
				html[++th] = data[i].id;
				html[++th] = '">';
				html[++th] = data[i].name;
				html[++th] = '</option>';
			}
			
			$('#fb_group_selected').empty().append(html.join(''));
		}
	});

}

// Initialise the facebook group select dialog
function enableFacebookDialog() {


	$('#fb_groups').dialog(
		{
			autoOpen: false, closeOnEscape:true, draggable:true, modal:true,
			show:"drop",
			width:350,
			height:250,
			zIndex: 2000,
			buttons: [
		        {
		        	text: "Cancel",
		        	click: function() {			        		
		        		$(this).dialog("close");
		        	}
		        }, {
		        	text: "Post",
		        	click: function() {
		        		var i, 
		        			picUrl = undefined, 
		        			url,
		        			groupId,
		        			desc,
		        			locn;
		        		
		        		groupId = $('#fb_group_selected').val();
		        		
		        		// Add an image
		        		if(gData[gIndex].type == "table") {
		    				picUrl = 'img/table_ico.png';
		    			} else {
		    				picUrl = gData[gIndex].thumbnail_url;
		    			}
		        			
		        		if(!gAccessToken) {
		        			alert("not logged on to facebook");
		        		} else {
		        			url = "https://graph.facebook.com/" + groupId + "/feed?access_token=" + gAccessToken;
		        			
		        			/*
		        			 * Add the location and category to the description
		        			 */
		        			desc = gData[gIndex].smap.description;
		        			bbox = gData[gIndex].smap.bbox;
		        			if(bbox && bbox[0]) {
		        				locn = "POINT(" + (parseFloat(bbox[0]) + parseFloat(bbox[2])) / 2 + 
		        					" " + (parseFloat(bbox[3]) + parseFloat(bbox[1])) / 2 + ")";
		        			}
		        			if(locn) {
		        				desc += "\nOther information:==\nLocation: " + locn;
		        				desc += "\nCategory: none";		// TODO add category processing
		        			}
		        			
		        			// Post to facebook
		        			addHourglass();
		        			$.ajax({
		        				type : 'post',
		        				url : url,
		        				data : {
		        				    link: gData[gIndex].smap.report_url,
		        				    name: gData[gIndex].title,
		        				    picture: picUrl,
		        				    description: desc
		        				  },
		        				error : function(xhr, status, error) {
		        					removeHourglass();
		        					if(xhr.status != 200) {	// Error can be called if FB does not return valid json even if the call was a success
		        						alert("Error: Failed to post: " + error);
		        					} 
		        				},
		        				success : function() {
		        					removeHourglass();
		        				}
		        			});
		        		}
		        		$(this).dialog("close");
		        	}
		        }        
			]
		}
	);

}

/*
 * Show the reports on the map
 */
function setMapReports() {
	
	var features,
		featuresObj,
		defaultStyle,
		selectStyle,
		colour_lookup,
		styleMap,
		strategy;

	featuresObj = gFeatures;
	
	if(typeof featuresObj === "undefined" || typeof featuresObj === "null" || !featuresObj) {
		console.log("Error: No features were retrieved from the server");
		return;
	}

	if(featuresObj.constructor != Array) {
		featuresObj = [featuresObj];
	}
	
	defaultStyle = new OpenLayers.Style(
		{
			fillColor: "red",
			pointRadius: "${radius}",
			fillOpacity: 0.6,
			strokeWidth: "${width}",
			label: "${count}"
		}, {
            context: {
                width: function(feature) {
                    return (feature.cluster) ? 2 : 1;
                },
                radius: function(feature) {
                    var pix = 8;
                    if(feature.cluster) {
                        pix = Math.min(feature.attributes.count, 8) + 8;
                        // Set the value to be processed by color lookup rule
                        var newValue = 0;
                        for(var i = 0; i < feature.cluster.length; i++) {
                            newValue += feature.cluster[i].attributes.value;
                        }
                        feature.attributes.value = Math.round(newValue / feature.cluster.length);
                    } 
                    return pix;
                },
                count: function(feature) {
                	return (feature.cluster) ? feature.attributes.count : "";
                }
            }
		});
	
	selectStyle = new OpenLayers.Style(
		{
			'fillOpacity': 1.0
		});

	colour_lookup = {
		4 : {fillColor: "red"},
		3 : {fillColor: "orange"},
		2 : {fillColor: "yellow"},
		1 : {fillColor: "green"},
		0 : {fillColor: "blue"}
	};


	styleMap = new OpenLayers.StyleMap({
		'default': defaultStyle,
	    'select': selectStyle
    });


	strategy = new OpenLayers.Strategy.Cluster({distance: 10, threshold: 3});
	gResultsLayer = new OpenLayers.Layer.Vector("reports", {
		strategies: [strategy],
		styleMap:styleMap
	});
	
	gMap.addLayer(gResultsLayer);		// Add layer to map before adding features or clustering does not work
	gResultsLayer.addFeatures(featuresObj);
	
	if(typeof gSelectResultsControl !== "undefined") {
		gMap.removeControl(gSelectResultsControl);
	}
	gSelectResultsControl = new OpenLayers.Control.SelectFeature(gResultsLayer,
            {onSelect: onFeatureSelect, onUnselect: onFeatureUnselect});
	gMap.addControl(gSelectResultsControl);
	gSelectResultsControl.activate();
	
	// Register for clicks since when we generate the table any selected features are unselected
	// If the user clicks on the map and they have previously selected a feature then generate an unselect
	gMap.events.register("click", map , function(e) {
		onFeatureUnselect();
	});
	
	// Compute the bounds 
	if(gOverallBounds) {
		for (i = 0; i < featuresObj.length; i++) {
			if(featuresObj[i].geometry) {
				gOverallBounds.extend(featuresObj[i].geometry.getBounds());
			}
		}
	}
	gMap.zoomToExtent(gOverallBounds);

}

/**
 * Map Initialization
 */
function initializeReportsMap() {
	
	var mapOptions,
		arrayOSM,
		control,
		defaultMapExtent = [-20037508, -20037508, 20037508, 20037508.34],
		$map = $('#map'),
		isVisible = true;

	if(!$map.is(':visible')) {
		isVisible = false;
		$('#map').show();		// Make map visible
	}
	
	// Set options and initialize map
	mapOptions = {
		projection: new OpenLayers.Projection("EPSG:900913"),
		displayProjection: new OpenLayers.Projection("EPSG:4326"),
		units: "m",
		numZoomLevels: 18,
		maxResolution: 156543.0339,
		maxExtent: new OpenLayers.Bounds(-20037508, -20037508, 20037508, 20037508.34),
		fallThrough: false
	};
	
	gMap = new OpenLayers.Map("map", mapOptions);  	
		
	// OSM Tile from mapquest
	arrayOSM = ["http://otile1.mqcdn.com/tiles/1.0.0/osm/${z}/${x}/${y}.jpg",
                 "http://otile2.mqcdn.com/tiles/1.0.0/osm/${z}/${x}/${y}.jpg",
                 "http://otile3.mqcdn.com/tiles/1.0.0/osm/${z}/${x}/${y}.jpg",
                 "http://otile4.mqcdn.com/tiles/1.0.0/osm/${z}/${x}/${y}.jpg"];
	 
	// Add layers
	gMap.addLayer(new OpenLayers.Layer.OSM("OSM", arrayOSM,{numZoomLevels: 18}));
	if(typeof google != 'undefined') {
		gMap.addLayer(new OpenLayers.Layer.Google("Google Satellite",{type: google.maps.MapTypeId.SATELLITE, 'sphericalMercator': true, numZoomLevels: 18}));
		gMap.addLayer(new OpenLayers.Layer.Google("Google Maps",{type: google.maps.MapTypeId.ROADMAP, 'sphericalMercator': true, numZoomLevels: 18}));
		gMap.addLayer(new OpenLayers.Layer.Google("Google Hybrid",{type: google.maps.MapTypeId.HYBRID, 'sphericalMercator': true, numZoomLevels: 18}));
	}
	
	// Map Controls
	gMap.addControl(new OpenLayers.Control.Zoom());
	//gMap.addControl(new OpenLayers.Control.Scale());
	gMap.addControl(new OpenLayers.Control.LayerSwitcher());			

	// Register for bounding box
    control = new OpenLayers.Control();
    OpenLayers.Util.extend(control, {
        draw: function () {
            // this Handler.Box will intercept the shift-mousedown
            // before Control.MouseDefault gets to see it
            this.box = new OpenLayers.Handler.Box( control,
                {"done": this.notice},
                {keyMask: OpenLayers.Handler.MOD_SHIFT});
            this.box.activate();
        },

        notice: function (box) {
       		var newBounds = new OpenLayers.Bounds();
            var	ll = gMap.getLonLatFromPixel(new OpenLayers.Pixel(box.left, box.bottom)); 
            var ur = gMap.getLonLatFromPixel(new OpenLayers.Pixel(box.right, box.top)); 
            
            newBounds.extend(ll);
            newBounds.extend(ur);
            showBounds(gMap, newBounds);
            updateListView();
            $('#clear_bounds').button("option", "disabled", false);	 // Enable button to clear bounds selection
        }
    });
    gMap.addControl(control);

    // Zoom to max extent
	if (!gMap.getCenter()) {
		gMap.zoomToExtent(new OpenLayers.Bounds(defaultMapExtent[0],defaultMapExtent[1],defaultMapExtent[2],defaultMapExtent[3]));
	}
	
	if(!isVisible) {
		$map.hide();
	}
}

function onFeatureSelect() {
	
}

function onFeatureUnselect() {
	
}

});
