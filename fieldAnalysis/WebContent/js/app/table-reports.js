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

/**
 * Get the data
 */
function getData(feature_url) {
	
	var	hourGlass,
		xmlhttp,
		dataJSON;

	// Show hourglass 
	hourGlass = document.getElementById('hour_glass');
	hourGlass.style.display="block";
	
	xmlhttp = new XMLHttpRequest();
	  
	xmlhttp.onreadystatechange=function() {
		if (xmlhttp.readyState === 4 && xmlhttp.status === 200) {
			dataJSON = JSON.parse(xmlhttp.responseText);
			generateTable("table", dataJSON[0]);
		}
		hourGlass.style.display="none";
	}
	
	xmlhttp.open("GET", feature_url, true);
	xmlhttp.send();
	
}

