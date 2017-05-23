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

            var labels,
                x, y,
                series,
                units = "";

            config.graph.setMargins(80, 50, 20, 80);
            if(chart.fn === "count") {
                y = config.graph.addMeasureAxis("y", "count");
            } else {

                if(chart.groups[0].type === "duration") {
                    y = config.graph.addMeasureAxis("y", "_duration");
                } else {
                    y = config.graph.addMeasureAxis("y", chart.groupLabels[0]);
                }
            }

            if(chart.tSeries) {
                labels = chart.groupLabels.unshift("date");
                x = config.graph.addCategoryAxis("x", ["date", "group"]);
                x.addOrderRule("Date");
                series = config.graph.addSeries("group", dimple.plot.bar);
            } else {

                if(chart.fn === "count") {
                    if(chart.groupLabels.length === 1) {
                        x = config.graph.addCategoryAxis("x", chart.groupLabels[0]);
                        series = config.graph.addSeries(null, dimple.plot.bar);
                    } else {
                        x = config.graph.addCategoryAxis("x", chart.groupLabels );
                        series = config.graph.addSeries(chart.groupLabels[1] , dimple.plot.bar);
                    }
                } else {

                    if(chart.groupLabels.length === 1) {
                        x = config.graph.addCategoryAxis("x", chart.fn);
                        series = config.graph.addSeries(null, dimple.plot.bar);
                    } else {
                        x = config.graph.addCategoryAxis("x", chart.groupLabels[1]);
                        series = config.graph.addSeries(chart.groupLabels[1], dimple.plot.bar);
                    }
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

            y.title = localise.set[chart.fn] + units;


        }

        /*
         * Update
         */
        function redraw(config, data) {

            config.graph.data = data;

        }


    });