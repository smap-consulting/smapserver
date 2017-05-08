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
 * Drawing bar charts
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

            var labels,
                x, y;

            config.graph.setMargins(80, 50, 20, 80);
            x = config.graph.addMeasureAxis("x", "count");

            if(chart.tSeries) {
                labels = chart.groupLabels.unshift("date");
                y = config.graph.addCategoryAxis("y", ["date", "group"]);
                y.addOrderRule("Date");
                config.graph.addSeries("group", dimple.plot.bar);
            } else {
                y = config.graph.addCategoryAxis("y", chart.groupLabels[0]);
                config.graph.addSeries(null, dimple.plot.bar);
            }

            x.title = localise.set[chart.fn];
            config.graph.addLegend(65, 10, 510, 20, "right");

        }

        /*
         * Update a bar chart
         */
        function redraw(config, data) {

            config.graph.data = data;

        }


    });