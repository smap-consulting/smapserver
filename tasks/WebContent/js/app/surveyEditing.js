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

define(['jquery', 'jquery_ui', 'tablesorter', 'localise'], function ($, ui, tablesorter, lang) {

    $(document).ready(function () {

        localise.setlang();		// Localise HTML
        if (typeof getVersion === "function") {
            getVersion();			// Update if the version on the server has changed
        }

        $("#fl").tablesorter({widgets: ['zebra']});
        $('#save_survey').button().click(function () {
            saveSurvey();
        });

        /*
         * Server Side Calculate Dialog
         */
        $('#add_ssc_popup').dialog(
            {
                autoOpen: false, closeOnEscape: true, draggable: true, modal: true,
                show: "drop",
                title: "Server Side Calculations",
                zIndex: 2000,
                buttons: [
                    {
                        text: "Cancel",
                        click: function () {
                            $(this).dialog("close");
                        }
                    },
                    {
                        text: "Save",
                        click: function () {
                            var fn = $('#ssc_function').val(),
                                name = $('#ssc_name').val(),
                                form = $('#ssc_form option:selected').val();
                            if (fn === "") {
                                alert("You must select a function");
                                return false;
                            }
                            if (name === "") {
                                alert("You must specify a name");
                                return false;
                            }
                            if (typeof form === "undefined" || form === "") {
                                alert("You must specify a form");
                                return false;
                            }
                            saveSSC();
                            $(this).dialog("close");
                        }
                    }
                ]
            }
        );

        $('#ssc_function').change(function () {
            var fn = $(this).val();
            $('#ssc_name').val(fn);		// Default name to name of function
            $("#ssc_form").empty();
            if (fn !== "") {
                populateFormList(fn);
                if (fn === "area") {
                    $('#ssc_units').html(
                        '<option value="hectares">Hectares</option>' +
                        '<option value="square meters">Square Meters</option>'
                    );
                } else if (fn === "length") {
                    $('#ssc_units').html(
                        '<option value="km">Kilometers</option>' +
                        '<option value="meters">Meters</option>'
                    );
                }
            } else {
                $('#ssc_units').html('');
            }
        });

        $('#add_ssc').button().click(function () {
            $('#ssc_name').val($('#ssc_function').val());		// Default name to name of function
            $('#add_ssc_popup').dialog("open");
        });

        $('.ssc_btn_rem').click(function () {
            var id = $(this).val(),
                sId = $('#s_id').val(),
                fn = "any";
            $.ajax({
                type: "DELETE",
                url: "/surveyKPI/ssc/" + sId + "/" + fn + "/delete/" + id,
                success: function (data, status) {
                    removeHourglass();
                    window.location.reload(true);
                }, error: function (xhr, textStatus, err) {
                    removeHourglass();
                    console.log(xhr);
                    alert("Failed to delete server side calculation: " + xhr.responseText);
                }
            });
        });

    });

    function populateFormList(fn) {
        var sId = $('#s_id').val(),
            i,
            h = [],
            idx = -1;

        addHourglass();
        $.ajax({
            url: "/surveyKPI/ssc/" + sId + "/" + fn + "/forms",
            dataType: 'json',
            cache: false,
            success: function (data) {
                removeHourglass();
                console.log("Forms");
                console.log(data);
                if (data.length === 0) {
                    alert("No forms found that can be used for function " + fn);
                } else {

                    for (i = 0; i < data.length; i++) {
                        h[++idx] = '<option value="';
                        h[++idx] = data[i].fId;
                        h[++idx] = '">';
                        h[++idx] = data[i].name;
                        h[++idx] = '</option>';
                    }
                    $("#ssc_form").html(h.join(''));
                }
            },
            error: function (xhr, textStatus, err) {
                removeHourglass();
                if (xhr.readyState == 0 || xhr.status == 0) {
                    return;  // Not an error
                } else {
                    alert("Error: Failed to get list of forms: " + err);
                    $('#add_ssc_popup').dialog("close");
                }
            }
        });
    }

    function saveSurvey() {

        var sName = $('#s_name').val(),
            sDefLang = $('#s_def_lang').val(),
            sId = $('#s_id').val();

        addHourglass();
        $.ajax({
            type: "POST",
            url: "/surveyKPI/survey/" + sId + "/rename",
            cache: false,
            data: {
                name: sName,
                def_lang: sDefLang
            },
            success: function (data, status) {
                removeHourglass();
                alert("Survey name updated");
            }, error: function (data, status) {
                removeHourglass();
                alert("Failed to update survey name");
            }
        });
    }

    function saveSSC() {

        var name = $('#ssc_name').val(),
            form = $('#ssc_form option:selected').val(),
            sId = $('#s_id').val(),
            fn = $('#ssc_function').val(),
            units = $('#ssc_units').val();

        addHourglass();
        $.ajax({
            type: "POST",
            url: "/surveyKPI/ssc/" + sId + "/" + fn + "/add",
            cache: false,
            data: {
                form: form,
                name: name,
                units: units
            },
            success: function (data, status) {
                removeHourglass();
                window.location.reload(true);
            }, error: function (xhr, textStatus, err) {
                removeHourglass();
                console.log(xhr);
                alert("Failed to add server side calculation: " + xhr.responseText);
            }
        });
    }

});
