
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
 * Set page themes
 */
var navbarColor = localStorage.getItem("navbar_color");
if(navbarColor) {
    var head = document.getElementsByTagName('head')[0];
    var style = document.createElement('style');
    style.setAttribute("id", "navbar_color");

    // header.navbar-default legacy WB banner
    // #header legacy jquery UI banner
    // Other elements are for current navbar
    style.innerHTML = 'header.navbar-default, #header '
        + '{ background-color: ' + navbarColor + '; background: ' + navbarColor + '};'

    head.appendChild(style);
}


