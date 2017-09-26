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
		fnText = {ocha: "location of responses to ", 
			percent: "percentage of responses that selected ", 
			average: "average of responses to ", 
			count: "count of responses to ", 
			total: "total of responses to ",
			none: "responses to "};
	
	console.log("getDisplayDescription: " + qtype + " : " + question);
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
	
	if(units) {
		txt[++idx] = " (units are " + units + ")";
	}
	txt[++idx] = " in survey '";
	txt[++idx] = survey;
	txt[++idx] = "'";
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
		txt[++idx] = " <b>Where ";
		txt[++idx] = filterObj.qName;
		txt[++idx] = " equals ";
		txt[++idx] = filterObj.value;
		txt[++idx] = "</b>";
	}
	
	return txt.join('');
}

/*
 * Process URLs to make them displayable
 */
function processMedia (property) {
    var output = [],
        media,
        i;

    if( Object.prototype.toString.call(property) !== '[object Array]' ) {
        property = [ property ];
    }

    for(i = 0; i < property.length; i++) {
        media = getMedia(property[i]);

        if(typeof media !== "undefined") {  // Add links to media files
            output[i] = '<a href="' + media.url
                + '" target="_blank"><img src="'
                + media.thumbNail + '" alt="Picture"></a>';

        } else {
            output[i] = property[i];
        }
    }

    return output;

}

/*
 * Add html references to media files
 * Return all properties as an Array
 * Deprecate this approach and progressively replace with processMedia()
 */
function addAnchors (property) {
	var output = [],
		media,
		i;
	
	if( Object.prototype.toString.call(property) !== '[object Array]' ) {
		property = [ property ];
	}
	
	for(i = 0; i < property.length; i++) {
	    media = getMedia(property[i]);
	    
	    if(typeof media !== "undefined") {  // Add links to media files
			output[i] = '<a href="' + media.url
				+ '" target="_blank"><img src="'
				+ media.thumbNail + '" alt="Picture"></a>';
	
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
		media = undefined;
	
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
		media = new Object();
		media.name = name;
		if(ext === "jpg" || ext === "png" || ext === "gif" || ext === "jpeg" || ext === "ico") {
			media.type = "image";
			media.url = property; 
			media.thumbNail = thumbNail;
		} else if(ext === "mp4" || ext === "3gp" || ext === "flv" || ext === "m4p" || ext === "mov") {
			media.type = "video";
			media.url = flv; 
			media.thumbNail = thumbNail;
		} else if(ext === "mp3" || ext === "amr" || ext === "3ga" || ext === "m4a") {
			media.type = "audio";
			media.url = flv;		// Use the processed url for audio 
			media.thumbNail = "/fieldAnalysis/img/audio-icon.png"
		} else {
			media.type = "unknown";
			media.url = property; 
			media.thumbNail = "/fieldAnalysis/img/audio-icon.png"
		}


	}
	
	return media;
		
}