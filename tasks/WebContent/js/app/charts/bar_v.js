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
 * Drawing vertical bar charts
 */

"use strict";

define([
        'jquery',
        'modernizr',
        'localise',
        'globals',
        'localise'],
    function ($, modernizr, lang, globals, localise) {


        return {
            add: add,
            redraw: redraw
        };


        /*
         * Add
         */
        function add(chart, config) {

            config.graph.setMargins(80, 50, 20, 80);
            var y = config.graph.addMeasureAxis("y", "value");
            var x = config.graph.addCategoryAxis("x",  "key");
            x.title = localise.set[chart.fn];
            y.title = chart.dataLabel;
            config.graph.addSeries(null, dimple.plot.bar);

        }

        /*
         * Update
         */
        function redraw(config, data) {

            config.graph.data = data;

        }


    });