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
 * Chart functions
 */

"use strict";

define([
        'jquery',
        'modernizr',
        'localise',
        'globals'],
    function ($, modernizr, lang, globals) {

        return {
            showEditRecordForm: showEditRecordForm
        };

        /*
         * Add HTML to show a form to edit a record
         */
        function showEditRecordForm(record, columns, $editForm, $surveyForm) {
            var
                h = [],
                idx = -1,
                m = [],
                cnt = -1,
                i,
                configItem,
                first = true;

            //gTasks.gCurrentIndex = index;
            gTasks.gPriKey = record["prikey"];

            // Clear the update array
            gTasks.gUpdate = [];
            $('#saveRecord').prop("disabled", true);

            for (i = 0; i < columns.length; i++) {
                configItem = columns[i];

                if (configItem.mgmt) {
                    h[++idx] = getEditMarkup(configItem, i, first, record);
                } else {
                    m[++cnt] = getEditMarkup(configItem, i, first, record);
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

            // Respond to changes in the data by creating an update object
            $editForm.find('.form-control').bind("click propertychange paste change keyup input", function () {
                dataChanged($(this));
            });
            $editForm.find('.date').on("dp.change", function () {
                dataChanged($(this).find('input'));
            });
            $editForm.find('select').change(function () {
                dataChanged($(this));
            });

            // Set focus to first editable data item
            $editForm.find('[autofocus]').focus();
        }

        /*
         * Get the markup to edit the record
         */
        function getEditMarkup(configItem, itemIndex, first, record) {

            var h = [],
                idx = -1;

            // Add form group and label
            h[++idx] = '<div class="form-group"><label class="col-md-4 control-label">';
            h[++idx] = configItem.humanName;
            h[++idx] = '</label>';

            // Add Data
            h[++idx] = ' <div class="col-md-8">';
            if (configItem.readonly) {		// Read only text
                h[++idx] = '<input type="text" disabled="" class="form-control" value="';
                h[++idx] = record[configItem.name];
                h[++idx] = '">';
            } else {
                h[++idx] = addEditableColumnMarkup(configItem, record[configItem.name], itemIndex, first);
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
        function addEditableColumnMarkup(column, value, itemIndex, first) {
            var h = [],
                idx = -1,
                i;

            if (column.type === "text" || column.type === "decimal" || column.type === "integer") {
                h[++idx] = ' <input type="';
                if (column.type === "text") {
                    h[++idx] = "text";
                } else if (column.type === "decimal") {
                    h[++idx] = "number";
                } else if (column.type === "integer") {
                    h[++idx] = "number";
                }
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
                        h[++idx] = '">'
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
    });