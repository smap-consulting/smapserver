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

"use strict";

define([
        'jquery',
        'modernizr',
        'localise',
        'globals',
        'app/mapOL3'],
    function ($, modernizr, lang, globals, map) {

        return {
            showEditRecordForm: showEditRecordForm,
            addCellMarkup: addCellMarkup,
            addCellMap: addCellMap,
            initialiseDynamicMaps: initialiseDynamicMaps
        };

        /*
         * Add HTML to show a form to edit a record
         */
        function showEditRecordForm(record, columns, $editForm, $surveyForm, editable) {
            var
                h = [],
                idx = -1,
                m = [],
                cnt = -1,
                i,
                configItem,
                first = true;

            //gTasks.gCurrentIndex = index;
            globals.gRecordMaps = [];     // Initialise the list of maps we are going to show
            gTasks.gPriKey = record["prikey"];

            // Clear the update array
            gTasks.gUpdate = [];
            $('#saveRecord').prop("disabled", true);

            for (i = 0; i < columns.length; i++) {
                configItem = columns[i];

                if (configItem.mgmt) {
                    h[++idx] = getEditMarkup(configItem, i, first, record, columns, editable);
                } else {
                    m[++cnt] = getEditMarkup(configItem, i, first, record, columns, editable);
                }
                if (!configItem.readonly) {
                    first = false;
                }
            }

            $editForm.html(h.join(''));
            $surveyForm.html(m.join(''));

            // Set up date fields
            $editForm.find('.date').datetimepicker({
                locale: gUserLocale || 'en',
                useCurrent: false,
                showTodayButton: true
            });

            // Set up the map fields
            initialiseDynamicMaps(globals.gRecordMaps);

            // Respond to changes in the data by creating an update object
            $editForm.find('.form-control, select').bind("click propertychange paste change keyup input", function () {
                var $this = $(this);
                var config = {
                    itemIndex: $this.data("item"),
                    value: $this.val()
                }
                dataChanged(config);
            });
            $editForm.find('.date').on("dp.change", function () {
                var $this = $(this).find('input');
                var config = {
                    itemIndex: $this.data("item"),
                    value: $this.val()
                }
                dataChanged(config);
            });
            $('#editRecordForm').on("smap::geopoint", function (event, config) {
                console.log("New geopoint");
                dataChanged(config);
            });

            // Set focus to first editable data item
            $editForm.find('[autofocus]').focus();
        }

        /*
         * Get the markup to edit the record
         */
        function getEditMarkup(configItem, itemIndex, first, record, columns, editable) {

            var h = [],
                idx = -1;

            // Add form group and label
            h[++idx] = '<div class="form-group row"><label class="col-md-4 control-label">';
            h[++idx] = configItem.displayName;
            h[++idx] = '</label>';

            // Add Data
            h[++idx] = ' <div class="col-md-8">';

            if(configItem.type === 'geopoint' || configItem.type === 'geoshape' || configItem.type === 'geotrace') {
                h[++idx] = addCellMap(
                    configItem.readonly || !editable,
                    'record_maps_',
                    globals.gRecordMaps,
                    configItem, record[configItem.displayName],
                    undefined,
                    itemIndex);
            } else if (configItem.readonly || !editable) {		// Read only text
                h[++idx] = addCellMarkup(record[configItem.displayName]);
            } else {
                h[++idx] = addEditableColumnMarkup(configItem, record[configItem.displayName], itemIndex, first, columns, record);
                first = false;
            }
            h[++idx] = '</div>';

            // Close form group
            h[++idx] = '</div>';

            return h.join('');
        }

        /*
         * Add the markup for an editable column
         */
        function addEditableColumnMarkup(column, value, itemIndex, first, columns, record) {
            var h = [],
                idx = -1,
                i,
                sourceColumn;

            // Check for a source column
            if(column.parameters && column.parameters.source) {
                var sourceColumn = getColumn(column.parameters.source, columns);
            }

            if(sourceColumn) {
                h[++idx] = addSourceQuestion(sourceColumn, record, column.parameters.rows);
            }

            if (column.type === "text") {
                if(column.parameters && column.parameters.rows) {
                    h[++idx] = ' <textarea rows=';
                    h[++idx] = column.parameters.rows;
                    h[++idx] = ' class="form-control editable" ';
                    h[++idx] = '" data-item="';
                    h[++idx] = itemIndex;
                    if (first) {
                        h[++idx] = '" autofocus>';
                    } else {
                        h[++idx] = '">';
                    }
                    h[++idx] = value;
                    h[++idx] = '</textarea>';
                } else {
                    h[++idx] = ' <input type="text"';
                    h[++idx] = '" class="form-control editable" value="';
                    h[++idx] = value;
                    h[++idx] = '" data-item="';
                    h[++idx] = itemIndex;
                    if (first) {
                        h[++idx] = '" autofocus/>';
                    } else {
                        h[++idx] = '"/>';
                    }
                }
            } else if (column.type === "decimal" || column.type === "integer") {
                h[++idx] = ' <input type="number"';
                h[++idx] = '" class="form-control editable" value="';
                h[++idx] = value;
                h[++idx] = '" data-item="';
                h[++idx] = itemIndex;
                if (first) {
                    h[++idx] = '" autofocus/>';
                } else {
                    h[++idx] = '"/>';
                }
            } else if (column.type === "date") {
                h[++idx] = '<div class="input-group date" data-container="body">';
                h[++idx] = '<input type="text" class="form-control editable" data-date-format="YYYY-MM-DD" value="';
                h[++idx] = value;
                h[++idx] = '" data-item="';
                h[++idx] = itemIndex;
                if (first) {
                    h[++idx] = '" autofocus/>';
                } else {
                    h[++idx] = '"/>';
                }
                h[++idx] = '<span class="input-group-addon"><span class="glyphicon glyphicon-calendar"></span></span>';
                h[++idx] = '</div>';
            } else if (column.type === "select_one") {
                h[++idx] = ' <select class="form-control editable" ';
                h[++idx] = '" data-item="';
                h[++idx] = itemIndex;
                h[++idx] = '">';
                if (column.choices) {
                    for (i = 0; i < column.choices.length; i++) {
                        h[++idx] = '<option';
                        if (column.choices[i].k === value) {
                            h[++idx] = ' selected="selected"'
                        }
                        h[++idx] = ' value="';
                        h[++idx] = column.choices[i].k;
                        h[++idx] = '">';
                        h[++idx] = column.choices[i].v;
                        h[++idx] = '</option>';
                    }
                }
                h[++idx] = '</select>';

            } else {
                h[++idx] = value;
            }

            return h.join('');
        }

        /*
         * Add the markup for a map
         */
        function addCellMap(readOnly, idbase, maps, column, currentValue, oldValue, itemIndex) {
            var h = [],
                idx = -1;

            // Make sure values are JSON objects
            if(typeof currentValue === "string") {
                try {
                    currentValue = JSON.parse(currentValue);
                } catch(err) {

                }
            }
            if(typeof oldValue === "string") {
                try {
                    oldValue = JSON.parse(oldValue);
                } catch (err) {

                }
            }

            var config = {
                readOnly: readOnly,
                id: idbase + maps.length,
                currentValue: currentValue,
                oldValue: oldValue
            }

            h[++idx] = '<div id="';
            h[++idx] = config.id;
            h[++idx] = '" data-item="';
            h[++idx] = itemIndex;
            h[++idx] = '" class="small_map">';
            h[++idx] = '<div id="tooltip_';
            h[++idx] = config.id;
            h[++idx] = '"></div>';
            h[++idx] = '</div>';

            maps.push(config);

            return h.join('');
        }

        function getColumn(qname, columns) {
            var i,
                col;
            for(i = 0; i < columns.length; i++) {
                // Hack.  For forms the human name is the question name that has not been modified to act as a database column
                if((columns[i].mgmt && qname === columns[i].name) || (!columns[i].mgmt && qname === columns[i].displayName)) {
                    col = columns[i];
                    break;
                }
            }
            return col;

        }


        function addSourceQuestion(column, record, ref_rows) {
            var name = column.mgmt ? column.name : column.displayName;        // Name hack
            var v = addAnchors(record[name])[0];
            var h = [];
            var idx = -1;

            if(v.indexOf('<') == 0) {
                h[++idx] = v;
            } else {
                if(!ref_rows || ref_rows <= 1) {
                    ref_rows = 1;
                }
                h[++idx] = ' <textarea readonly style="overflow-y:scroll;" rows=';
                h[++idx] = ref_rows;
                h[++idx] = ' class="form-control">';
                h[++idx] = v;
                h[++idx] = '</textarea>';
            }

            return h.join('');
        }

        /*
         * User has changed a managed value
         */
        function dataChanged(config) {

            var
                itemIndex = config.itemIndex,
                value = config.value,
                record = gTasks.gSelectedRecord,
                columns = gTasks.cache.currentData.schema.columns,
                currentValue,
                column_name = columns[itemIndex].column_name,
                i,
                foundExistingUpdate;

            currentValue = record[columns[itemIndex].displayName];
            if (typeof currentValue === "undefined") {
                currentValue = "";
            }
            if(typeof currentValue === "object") {
                currentValue = JSON.stringify(currentValue);
            }
            if(typeof value === "object") {
                value = JSON.stringify(value);
            }

            if (currentValue !== value) {
                // Add new value to array, or update existing
                foundExistingUpdate = false;
                for (i = 0; i < gTasks.gUpdate.length; i++) {
                    if (gTasks.gUpdate[i].name === column_name) {
                        foundExistingUpdate = true;
                        gTasks.gUpdate[i].value = value;
                        break;
                    }
                }

                if (!foundExistingUpdate) {
                    // Add new value
                    gTasks.gUpdate.push({
                        name: column_name,                 // Update name is the column name
                        value: value,
                        currentValue: currentValue,
                        prikey: gTasks.gPriKey
                    });
                }

            } else {
                // Delete value from array of updates
                for (i = 0; i < gTasks.gUpdate.length; i++) {
                    if (gTasks.gUpdate[i].name === column_name) {
                        gTasks.gUpdate.splice(i, 1);
                        break;
                    }
                }
            }

            if (gTasks.gUpdate.length > 0) {
                $('#saveRecord').prop("disabled", false);
            } else {
                $('#saveRecord').prop("disabled", true);
            }
        }

        /*
         * Add markup for a single cell
         */
        function addCellMarkup(input) {
            var v = addAnchors(input)[0],
                h = [],
                idx = -1;
            if(v && v.indexOf('<') == 0) {
                h[++idx] = v;
            } else {
                h[++idx] = ' <textarea readonly style="overflow-y:scroll;" rows=1';
                h[++idx] = ' class="form-control">';
                h[++idx] = v;
                h[++idx] = '</textarea>';
            }
            return h.join('');

        }

        /*
	     * Initialise maps
	     */
        function initialiseDynamicMaps(maps, mapId) {
            var i;

            for(i = 0; i < maps.length; i++) {
                if(!mapId || mapId === maps[i].id) {
                    map.initDynamicMap(maps[i]);
                }
            }

        }
    });