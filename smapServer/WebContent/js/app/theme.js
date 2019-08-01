
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
try {
    var navbarColor = localStorage.getItem("navbar_color");
    var navbarLight = LightenDarkenColor(navbarColor, 20);
    if(navbarColor) {
        var head = document.getElementsByTagName('head')[0];
        var style = document.createElement('style');
        style.setAttribute("id", "navbar_color");

        // header.navbar-default legacy WB banner
        // #header legacy jquery UI banner
        // Other elements are for current navbar
        style.innerHTML = 'header.navbar-default, #header, body, .mini-navbar, .nav-second-level, .nav > li.active, .navbar-default .nav > li > a:focus '
            + '{ background-color: ' + navbarColor + '; background: ' + navbarColor + '}'
            + ' .navbar-default .nav > li > a:hover, ul.nav-second-level, .canvas-menu.mini-navbar .nav-second-level '
            + '{ background-color: ' + navbarLight + '; background: ' + navbarLight + '}';

        head.appendChild(style);
    }
} catch (e) {

}


// From https://css-tricks.com/snippets/javascript/lighten-darken-color/
function LightenDarkenColor(col, amt) {

    var usePound = false;

    if (col[0] == "#") {
        col = col.slice(1);
        usePound = true;
    }

    var num = parseInt(col,16);

    var r = (num >> 16) + amt;

    if (r > 255) r = 255;
    else if  (r < 0) r = 0;

    var b = ((num >> 8) & 0x00FF) + amt;

    if (b > 255) b = 255;
    else if  (b < 0) b = 0;

    var g = (num & 0x0000FF) + amt;

    if (g > 255) g = 255;
    else if (g < 0) g = 0;

    return (usePound?"#":"") + (g | (b << 8) | (r << 16)).toString(16);

}


