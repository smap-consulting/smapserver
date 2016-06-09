/*
 * Copyright (C) 2013 Nafundi
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package com.nafundi.taskforce.codebook.logic;

import org.javarosa.core.model.Constants;
import org.javarosa.core.model.FormDef;
import org.javarosa.core.model.QuestionDef;
import org.javarosa.core.model.SelectChoice;
import org.javarosa.core.model.condition.EvaluationContext;
import org.javarosa.core.model.instance.TreeElement;
import org.javarosa.core.services.locale.Localizer;
import org.javarosa.model.xform.XFormsModule;
import org.javarosa.xform.parse.XFormParseException;
import org.javarosa.xform.util.XFormUtils;

import javax.swing.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Vector;

public class CodebookEngineSmap  {

    private final String filepath;
    private final String language;

    public CodebookEngineSmap(String filepath, String language) {
        this.filepath = filepath;
        this.language = language;
    }

    public ArrayList<CodebookEntry> getEntry() {

        new XFormsModule().registerModule();
        // needed to override rms property manager
        org.javarosa.core.services.PropertyManager.setPropertyManager(new PropertyManager(5));

        File xml = new File(filepath);
        String errorMsg = "";
        FormDef fd = null;

        try {
            FileInputStream fis = new FileInputStream(xml);
            fd = XFormUtils.getFormFromInputStream(fis);
            if (fd == null) {
                errorMsg = "Error reading XForm file";
            }

        } catch (FileNotFoundException e) {
            errorMsg = e.getMessage();
            e.printStackTrace();
        } catch (XFormParseException e) {
            errorMsg = e.getMessage();
            e.printStackTrace();
        } catch (Exception e) {
            errorMsg = e.getMessage();
            e.printStackTrace();
        }
        if (!"".equals(errorMsg)) {
            System.out.println(errorMsg);
            return null;
        }
        // new evaluation context for function handlers
        if (fd != null) {
            fd.setEvaluationContext(new EvaluationContext(null));
            fd.initialize(true);
        } else {
        	System.out.println("FormDef is null");
            return null;
        }

        HashMap<String, ArrayList<CodebookEntry>> entries = new HashMap<String, ArrayList<CodebookEntry>>();

        if (fd.getLocalizer() == null) {
            fd.setLocalizer(new Localizer());
        }

        if(!language.toLowerCase().equals("default")) {
        	try {
        		fd.getLocalizer().setLocale(language);
        	} catch (Exception e) {
        		// ignore exceptions to language
        		// Probably this is an old survey that had default language set to eng
        	}
        }
        ArrayList<CodebookEntry> entry = new ArrayList<CodebookEntry>();
        populateEntries(fd.getInstance().getRoot(), fd, entry);
          
        return entry;

    }

    private void populateEntries(TreeElement t, FormDef fd, ArrayList<CodebookEntry> entries) {

        Localizer localizer = fd.getLocalizer();

        for (int i = 0; i < t.getNumChildren(); i++) {
            TreeElement t1 = t.getChildAt(i);
            CodebookEntry ce = new CodebookEntry();
            String ref = t1.getRef().toString(false);

            // get rid of the leading path
            ce.setVariable(ref.substring(ref.lastIndexOf("/") + 1));

            QuestionDef qd = FormDef.findQuestionByRef(t1.getRef(), fd);

            if (qd != null) {
                StringBuilder questions = new StringBuilder();
                StringBuilder values = new StringBuilder();

                // add question text
                String questionText = getLocalizedLabel(qd.getTextID(), qd.getLabelInnerText(), localizer);
                questions.append(questionText);

                // populate questions and values appropriately
                switch (qd.getControlType()) {
                    case Constants.CONTROL_INPUT:
                        switch (t1.dataType) {
                            case Constants.DATATYPE_DATE_TIME:
                                values.append("User selected date and time");
                                break;
                            case Constants.DATATYPE_DATE:
                                values.append("User selected date");
                                break;
                            case Constants.DATATYPE_TIME:
                                values.append("User selected time");
                                break;
                            case Constants.DATATYPE_DECIMAL:
                                values.append("User entered decimal");
                                break;
                            case Constants.DATATYPE_INTEGER:
                                values.append("User entered integer");
                                break;
                            case Constants.DATATYPE_GEOPOINT:
                                values.append("User captured location coordinates");
                                break;
                            case Constants.DATATYPE_BARCODE:
                                values.append("User captured barcode");
                                break;
                            case Constants.DATATYPE_TEXT:
                                values.append("User entered text");
                                break;
                        }
                        break;
                    case Constants.CONTROL_IMAGE_CHOOSE:
                        values.append("User captured image");
                        break;
                    case Constants.CONTROL_AUDIO_CAPTURE:
                        values.append("User captured audio");
                        break;
                    case Constants.CONTROL_VIDEO_CAPTURE:
                        values.append("User captured video");
                        break;
                    case Constants.CONTROL_SELECT_ONE:
                    case Constants.CONTROL_SELECT_MULTI:
                        Vector<SelectChoice> choices = qd.getChoices();
                        if(choices != null) {
                            questions.append("|");
                            for (SelectChoice choice : choices) {
                                values.append(getLocalizedLabel(choice.getTextID(), choice.getLabelInnerText(), localizer) + "\t" + choice.getValue() + "\n");
                            }
                        } else {
                        	System.out.println("Choices is null");
                        }
 
                        break;
                    default:
                        break;
                }

                ce.setQuestion(questions.toString());
                ce.setValue(values.toString());
            } else {
                // if it's null, it's a preloader or a group
                ce.setQuestion("Hidden from user");
                ce.setValue(getValues(t1));
            }

            entries.add(ce);
            // recurse
            if (t1.getNumChildren() > 0) {
                populateEntries(t1, fd, entries);
            }
        }
    }

    private String getValues(TreeElement t) {
        String params = t.getPreloadParams();
        if (params == null) {
            // this was probably a group, so just return an empty string
            return "";
        }

        if ("start".equalsIgnoreCase(params)) {
            return "Timestamp of form open";
        } else if ("end".equalsIgnoreCase(params)) {
            return "Timestamp of form save";
        } else if ("today".equalsIgnoreCase(params)) {
            return "Today's date";
        } else if (PropertyManager.DEVICE_ID_PROPERTY.equalsIgnoreCase(params) || PropertyManager.OR_DEVICE_ID_PROPERTY.equalsIgnoreCase(params)) {
            return "Device ID (IMEI, Wi-Fi MAC, Android ID) ";
        } else if (PropertyManager.SUBSCRIBER_ID_PROPERTY.equalsIgnoreCase(params) || PropertyManager.OR_SUBSCRIBER_ID_PROPERTY.equalsIgnoreCase(params)) {
            return "Subscriber ID (IMSI)";
        } else if (PropertyManager.SIM_SERIAL_PROPERTY.equalsIgnoreCase(params) || PropertyManager.OR_SIM_SERIAL_PROPERTY.equalsIgnoreCase(params)) {
            return "Serial number of SIM";
        } else if (PropertyManager.PHONE_NUMBER_PROPERTY.equalsIgnoreCase(params) || PropertyManager.OR_PHONE_NUMBER_PROPERTY.equalsIgnoreCase(params)) {
            return "Phone number of SIM";
        } else if (PropertyManager.USERNAME.equalsIgnoreCase(params) || PropertyManager.OR_USERNAME.equalsIgnoreCase(params)) {
            return "Username on device";
        } else if (PropertyManager.EMAIL.equalsIgnoreCase(params) || PropertyManager.OR_EMAIL.equalsIgnoreCase(params)) {
            return "Google account on device";
        } else {
            return "Unknown preloader";
        }

    }

    // TODO prompts with file paths (e.g., images as label) don't work.
    private String getLocalizedLabel(String textId, String labelText, Localizer l) {

        if (textId == null || textId == "") return labelText;

        //otherwise check for 'long' form of the textID, then for the default form and return
        String returnText;
        returnText = getIText(textId, "long", l);
        if (returnText == null) returnText = getIText(textId, null, l);

        return returnText;
    }

    private String getIText(String textID, String form, Localizer localizer) {
        String returnText = null;
        if (textID == null || textID.equals("")) return null;
        if (form != null && !form.equals("")) {
            try {
                returnText = localizer.getRawText(localizer.getLocale(), textID + ";" + form);
            } catch (NullPointerException npe) {
            }
        } else {
            try {
                returnText = localizer.getRawText(localizer.getLocale(), textID);
            } catch (NullPointerException npe) {
            }
        }
        return returnText;
    }



}

