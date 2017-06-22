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

            var p,
                series;

            config.graph.setMargins(80, 50, 20, 80);

            if(chart.fn === "count") {
                p = config.graph.addMeasureAxis("p", "count");
                series = config.graph.addSeries(chart.groupDataNames[0], dimple.plot.pie);
            } else {

                if(chart.groups[0].type === "duration") {
                    p = config.graph.addMeasureAxis("p", "_duration");
                    series = config.graph.addSeries(chart.groupDataNames[1], dimple.plot.pie);

                } else {
                    p = config.graph.addMeasureAxis("p", chart.groupDataNames[0]);
                    series = config.graph.addSeries(chart.groupDataNames[1], dimple.plot.pie);
                }
            }

            if(chart.fn === "average") {
                series.aggregate = dimple.aggregateMethod.avg;
            } else if(chart.fn === "max") {
                series.aggregate = dimple.aggregateMethod.max;
            } else if(chart.fn === "min") {
                series.aggregate = dimple.aggregateMethod.min;
            } else if(chart.fn === "sum") {
                series.aggregate = dimple.aggregateMethod.sum;
            }

            p.title = chart.dataLabels.join(' / ') + " (" + chart.fn + ")";

        }

        /*
         * Redraw
         */
        function redraw(config, data) {

            config.graph.data = data;

        }

    });