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
 * Drawing a pie chart
 */

"use strict";

define([
        'jquery',
        'modernizr',
        'localise',
        'globals'],
    function ($, modernizr, lang, globals) {

        return {
            add: add,
            redraw: redraw
        };

        /*
         * Add
         */
        function add(chart, config) {

            config.graph.setMargins(80, 50, 20, 80);
            var p = config.graph.addMeasureAxis("p", "value");
            config.graph.addSeries("key", dimple.plot.pie);
            p.title = chart.dataLabel;
            config.graph.addLegend(500, 20, 90, 300, "left");

        }

        /*
         * Redraw
         */
        function redraw(config, data) {

            config.graph.data = data;

        }

    });