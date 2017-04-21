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
 * Drawing a line chart
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
        function add(chart, config, data, width, height, margin) {

            config.x = d3.scaleTime().rangeRound([0, width]);
            config.y = d3.scaleLinear().rangeRound([height, 0]);

            config.parseTime = d3.timeParse("%Y-%m-%d");

            data.forEach(function (d) {
                d.date = config.parseTime(d.key);
                d.value = +d.value;
            });

            config.xAxis = d3.axisBottom(config.x);
            config.yAxis = d3.axisLeft(config.y);
            config.x.domain(d3.extent(data, function (d) {
                return d.date;
            }));
            config.y.domain(d3.extent(data, function (d) {
                return d.value;
            }));

            config.line = d3.line()
                .x(function (d) {
                    return config.x(d.date);
                })
                .y(function (d) {
                    return config.y(d.value);
                });

            config.g = config.svg.append("g")
                .attr("transform", "translate(" + margin.left + "," + margin.top + ")");

            config.g.append("g")
                .attr("class", "axis axis--x")
                .attr("transform", "translate(0," + height + ")")
                .call(config.xAxis);

            config.g.append("g")
                .attr("class", "axis axis--y")
                .call(config.yAxis)
                .append("text")
                .attr("transform", "rotate(-90)")
                .attr("y", 6)
                .attr("dy", "0.71em")
                .attr("text-anchor", "end")
                .text("Count");

        }

        /*
         * Update
         */
        function redraw(chartId, chart, config, data, width, height, margin) {

            console.log("Refresh line chart");

            data.forEach(function (d) {
                d.date = config.parseTime(d.key);
                d.value = +d.value;
            });

            config.x.domain(d3.extent(data, function (d) {
                return d.date;
            }));
            config.y.domain(d3.extent(data, function (d) {
                return d.value;
            }));

            // Update axes
            config.svg.select(".axis--y")
            //.transition()
            //.duration(500)
                .call(config.yAxis.ticks(5, ""));
            config.svg.select(".axis--x")
            //.transition()
            //.duration(500)
                .call(config.xAxis);

            var lines = config.g.append("path")
                .datum(data)
                .attr("class", "line")
                .attr("d", config.line);


        }


    });