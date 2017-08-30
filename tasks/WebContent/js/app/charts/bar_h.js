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
                x, y,
                series;

            config.graph.setMargins(80, 50, 20, 80);    // left, top, right, bottom
            if(chart.fn === "count") {
                x = config.graph.addMeasureAxis("x", "count");
            } else {

                if(chart.groups[0].type === "duration") {
                    x = config.graph.addMeasureAxis("x", "_duration");
                } else {
                    x = config.graph.addMeasureAxis("x", chart.groupDataNames[0]);
                }
            }

            if(chart.tSeries) {
                labels = chart.groupDataNames.unshift("date");
                y = config.graph.addCategoryAxis("y", ["date", "group"]);
                y.addOrderRule("Date");
                config.graph.addSeries("group", dimple.plot.bar);
            } else {
                if(chart.fn === "count") {
                    if(chart.groupDataNames.length === 1) {
                        y = config.graph.addCategoryAxis("y", chart.groupDataNames[0]);
                        series = config.graph.addSeries(null, dimple.plot.bar);
                    } else {
                        y = config.graph.addCategoryAxis("y", chart.groupDataNames );
                        series = config.graph.addSeries(chart.groupDataNames[1] , dimple.plot.bar);
                    }
                } else {

                    if(chart.groupDataNames.length === 1) {
                        y = config.graph.addCategoryAxis("y", chart.fn);
                        series = config.graph.addSeries(null, dimple.plot.bar);
                    } else {
                        y = config.graph.addCategoryAxis("y", chart.groupDataNames[1]);
                        series = config.graph.addSeries(chart.groupDataNames[1], dimple.plot.bar);
                    }
                }
                //y = config.graph.addCategoryAxis("y", chart.groupDataNames[0]);
                //config.graph.addSeries(null, dimple.plot.bar);
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

            y.title = chart.dataLabels.join(' / ');
            x.title = localise.set[chart.fn];

        }

        /*
         * Update a bar chart
         */
        function redraw(config, data) {

            config.graph.data = data;

        }


    });