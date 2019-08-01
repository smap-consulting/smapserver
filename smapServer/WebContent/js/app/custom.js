
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
 * Custom java script function(s) 
 * Overridden for each custom implementation
 */

function setCustom() {
	// Menus
}

function setCustomReports() {
    // Menus
}

function setCustomUserTrail() {
	// Menus
}

function setCustomAssignments() {
	// Menus
}

function setCustomTemplateMgmt() {
	// Menus
}

function setCustomUserMgmt() {
	// Menus
}

function setCustomResources() {
	// Menus
}

function setCustomNotifications() {
	// Menus
}

function setCustomDashboard() {
	// Menus
}

function setCustomReview() {
    // Menus
}

function setCustomAudit() {
    // Menus
}

function setCustomUserDashboard() {
	// Menus
}

function setCustomMonitor() {
	// Menus
}

function setCustomManage() {
	// Menus
}

function setCustomRegister() {
}

function setCustomSubscriptions() {
    var elem1 = document.getElementById("navbar-brand");

    // Add branding for subscriptions
    if(elem1) {
       elem1.innerHTML = "<img src='/images/logo.png'><span class='thick'>SMAP</span> Server";
    }

}

function setCustomEdit() {
}

function setCustomWebForms() {
	
}

function setCustomLogs() {

}

function setCustomBilling() {

}

function setCustomMainLogo() {
    $('.main_logo').attr("src", "/images/cropped-blog1.jpg");
}
