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


function getDisplayDescription(fn, reportType, survey, question, group, option, qtype, 
		date_question, start, end, interval, units, filter) {
	
	var txt = [],
		idx = -1,
		msg;
	/*
		fnText = {ocha: "location of responses to ", 
			percent: "percentage of responses that selected ", 
			average: "average of responses to ", 
			count: "count of responses to ", 
			total: "total of responses to ",
			none: "responses to "};
			*/
	
	console.log("getDisplayDescription: " + qtype + " : " + question);
	/*
	txt[++idx] = "Shows the ";
	if(typeof question === "undefined" || question === "None") {
		txt[++idx] = "results";
	} else {
		txt[++idx] = fnText[fn];
		if(reportType === "map") {
			txt[++idx] = option + " in ";
		} else if(typeof qtype !== "undefined" && qtype.substring(0, 6) === 'select'){
			txt[++idx] = "each option in ";
		}
		txt[++idx] = "the question '";
		txt[++idx] = question;
		txt[++idx] ="'";
	}
	*/
	if(typeof question === "undefined" || question === "None") {
		txt[++idx] = localise.set["a_dd_sl"];
	} else {
		if(reportType === "map") {
			if(fn === "ocha") {
				msg = localise.set["a_dd_ocha_map"];
			} else if(fn === "percent") {
				msg = localise.set["a_dd_percent_map"];
			} else if(fn === "average") {
				msg = localise.set["a_dd_average_map"];
			} else if(fn === "count") {
				msg = localise.set["a_dd_count_map"];
			} else if(fn === "total") {
				msg = localise.set["a_dd_total_map"];
			} else if(fn === "none") {
				msg = localise.set["a_dd_none_map"];
			}
			msg = msg.replace('%s1', option);
		} else {
			if(fn === "ocha") {
				msg = localise.set["a_dd_ocha_table"];
			} else if(fn === "percent") {
				msg = localise.set["a_dd_percent_table"];
			} else if(fn === "average") {
				msg = localise.set["a_dd_average_table"];
			} else if(fn === "count") {
				msg = localise.set["a_dd_count_table"];
			} else if(fn === "total") {
				msg = localise.set["a_dd_total_table"];
			} else if(fn === "none") {
				msg = localise.set["a_dd_none_table"];
			}

		}
		msg = msg.replace('%s2', question);
		msg = msg.replace('%s3', survey);
		txt[++idx] = msg;
	}

	
	if(units) {
		//txt[++idx] = " (units are " + units + ")";
		msg = localise.set["a_dd_units"];
		msg = msg.replace("%s1", units);
		txt[++idx] = ' ';
		txt[++idx] = msg;
	}

	//txt[++idx] = " in survey '";
	//txt[++idx] = survey;
	//txt[++idx] = "'";

	/*
	if(typeof group !== "undefined") {
		txt[++idx] = " grouped by the responses to the question ";
		txt[++idx] = group;
		if(typeof interval !== "undefined") {
			txt[++idx] = " and by ";
			txt[++idx] = interval;
		}
	} else {
		if(typeof interval !== "undefined") {
			txt[++idx] = " grouped by ";
			txt[++idx] = interval;
		}
	}*/
	if(typeof group !== "undefined") {
		if(typeof interval !== "undefined") {
			msg = localise.set["a_dd_group_interval"];
			msg = msg.replace("%s2", interval);
		} else {
			msg = localise.set["a_dd_group"];
		}
		msg = msg.replace("%s1", group);
		txt[++idx] = ' ';
		txt[++idx] = msg;
	} else if(typeof interval !== "undefined"){
		msg = localise.set["a_dd_interval"];
		msg = msg.replace("%s1", interval);
		txt[++idx] = ' ';
		txt[++idx] = msg;
	}
	
	if(typeof end === "undefined") {
		end = ""
	}
	if(typeof date_question !== "undefined" && typeof start !== "undefined") {
		txt[++idx] = " (";
		txt[++idx] = date_question;
		txt[++idx] = ": ";
		txt[++idx] = start;
		txt[++idx] = " - ";
		txt[++idx] = end;
		txt[++idx] = ")";
	}

	// Add filter
	if(filter) {
		var filterObj = JSON.parse(filter);
		msg = localise.set["a_dd_where"];
		msg.replace("%s1", filterObj.qName);
		msg.replace("%s2", filterObj.value);
		txt[++idx] = ' ';
		txt[++idx] = msg;
		//txt[++idx] = " <b>Where ";
		//txt[++idx] = filterObj.qName;
		//txt[++idx] = " equals ";
		//txt[++idx] = filterObj.value;
		//txt[++idx] = "</b>";
	}
	
	return txt.join('');
}

/*
 * Add html references to media files
 * Return all properties as an Array
 * Deprecate this approach and progressively replace with processMedia()
 */
function addAnchors (property, minsize) {
	var output = [],
		media,
		i,
		style = '';

	if(property && typeof property.type !== "undefined") {
		// Geospatial
		return [ property.coordinates ];

	}

	if( Object.prototype.toString.call(property) !== '[object Array]' ) {
		property = [ property ];
	}
	
	for(i = 0; i < property.length; i++) {
	    media = getMedia(property[i]);
	    
	    if(typeof media !== "undefined") {  // Add links to media files
			if(media.supported) {
                if (media.type === "audio") {
                    if (minsize) {
                        style = ' style="width:75px;"';
                    }
                    output[i] = '<audio controls' + style + '><source src="' + media.url
                        + '" type="' + media.source_type + '"/>'
                        + 'Your browser does not support this audio type'
                        + '</audio>';
                } else if (media.type === "video") {
                    if (minsize) {
                        style = ' style="width:100px;"';
                    }
                    output[i] = '<video controls' + style +
                        (minsize ? 'width="75px" height="100px"' : '')
                        + '><source src="' + media.url
                        + '" type="' + media.source_type + '">'
                        + 'Your browser does not support this video type'
                        + '</video>';
                } else {
                    style = (media.thumbNail == '/fieldAnalysis/img/link.png') ? "width:32px;" : "width:100%;";
                    output[i] = '<a href="' + media.url
                        + '" target="_blank"><img style="' + style + '" src="'
                        + (minsize ? media.thumbNail : media.url)
						+ '" alt="Picture"></a>';
                }
            } else {
                output[i] = '<a href="' + media.url
                    + '" download style="color:#0000FF;">' + localise.set["c_download"] + '</a>';
			}
	
		} else { 
			output[i] = property[i];
		}
	} 
	
	return output;
	
}

/*
 * Get an object with the media details for the property
 */
function getMedia(property) {
	
	var idx, name, ext, urlBase, thumbNail, flv, type,
		media;
	
	if(typeof property === "string" && (property.indexOf("//") === 0 || property.indexOf("http") === 0)) {  // Add links to media files
		
		if(property.indexOf("//") === 0) {
			property = "https:" + property;
		}
		// Get name, type, urlBase and thumbName from the input url

		idx = property.lastIndexOf("/");
		name = property.substring(idx+1);
		urlBase = property.substring(0, idx+1);
		idx = name.lastIndexOf(".");
		ext = name.substring(idx+1).toLowerCase();
		thumbNail = urlBase + "thumbs/" + name + ".jpg";
		flv = urlBase + "flv/" + name + ".flv";
		
		// Create a media object
		media = {};
		media.name = name;
		media.supported = true;
		if(ext === "jpg" || ext === "png" || ext === "gif" || ext === "jpeg" || ext === "ico") {
			media.type = "image";
			media.url = property; 
			media.thumbNail = thumbNail;
		} else if(ext === "mp4" || ext === "3gp" || ext === "flv" || ext === "m4p" || ext === "mov") {
            if(ext == "mp4") {
                media.source_type = "video/mp4";
            } else  if(ext == "3gp") {
                media.supported = false;
                media.source_type = "video/3gp";
            } else  if(ext == "flv") {
                media.supported = false;
                media.source_type = "video/x-flv";
            } else  if(ext == "mov") {
                media.supported = false;
                media.source_type = "video/quicktime";
            }
			media.type = "video";
			media.url = property;
			media.thumbNail = thumbNail;
		} else if(ext === "mp3" || ext === "amr" || ext === "3ga" || ext === "m4a" || ext === "ogg" || ext === "wav") {
			if(ext == "m4a") {
				media.source_type = "audio/mp4";
			} else if(ext == "mp3") {
                media.source_type = "audio/mp3";
            } else if(ext == "amr") {
				media.supported = false;
                media.source_type = "audio/amr";
            } else if(ext == "ogg") {
                media.source_type = "audio/ogg";
            } else if(ext == "wav") {
                media.source_type = "audio/wav";
            }
			media.type = "audio";
			media.url = property;		// Don't convert audio files
			media.thumbNail = "/fieldAnalysis/img/audio-icon.png"
		} else {
			media.type = "unknown";
			media.url = property; 
			media.thumbNail = "/fieldAnalysis/img/link.png"
		}


	}
	
	return media;
		
}