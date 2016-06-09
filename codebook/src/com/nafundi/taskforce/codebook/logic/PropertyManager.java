/*
 * Copyright (C) 2009 University of Washington
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

import org.javarosa.core.services.IPropertyManager;
import org.javarosa.core.services.properties.IPropertyRules;

import java.util.HashMap;
import java.util.Locale;
import java.util.Vector;

/**
 * Used to return device properties to JavaRosa
 * 
 * @author Yaw Anokwa (yanokwa@gmail.com)
 */

class PropertyManager implements IPropertyManager {

    private final HashMap<String, String> mProperties;

    public final static String DEVICE_ID_PROPERTY = "deviceid"; // imei
    public final static String SUBSCRIBER_ID_PROPERTY = "subscriberid"; // imsi
    public final static String SIM_SERIAL_PROPERTY = "simserial";
    public final static String PHONE_NUMBER_PROPERTY = "phonenumber";
    public final static String USERNAME = "username";
    public final static String EMAIL = "email";

    public final static String OR_DEVICE_ID_PROPERTY = "uri:deviceid"; // imei
    public final static String OR_SUBSCRIBER_ID_PROPERTY = "uri:subscriberid"; // imsi
    public final static String OR_SIM_SERIAL_PROPERTY = "uri:simserial";
    public final static String OR_PHONE_NUMBER_PROPERTY = "uri:phonenumber";
    public final static String OR_USERNAME = "uri:username";
    public final static String OR_EMAIL = "uri:email";

    public PropertyManager(int unused) {
        mProperties = new HashMap<String, String>();
        mProperties.put(DEVICE_ID_PROPERTY, "noid");
        mProperties.put(OR_DEVICE_ID_PROPERTY, "noORid");

        mProperties.put(SUBSCRIBER_ID_PROPERTY, "default_subscriber_id");
        mProperties.put(OR_SUBSCRIBER_ID_PROPERTY, "imsi:" + "default_subscriber_id_or");

        mProperties.put(SIM_SERIAL_PROPERTY, "default_serial");
        mProperties.put(OR_SIM_SERIAL_PROPERTY, "simserial:" + "or_default_serial");

        mProperties.put(PHONE_NUMBER_PROPERTY, "default_phone");
        mProperties.put(OR_PHONE_NUMBER_PROPERTY, "tel:" + "or_default_phonen");

        mProperties.put(USERNAME, "default_username");
        mProperties.put(OR_USERNAME, "username:" + "or_default_username");

        mProperties.put(EMAIL, "email");
        mProperties.put(OR_EMAIL, "or_default_email");

    }

    @Override
    public Vector<String> getProperty(String propertyName) {
        return null;
    }

    @Override
    public String getSingularProperty(String propertyName) {
        // for now, all property names are in english...
        return mProperties.get(propertyName.toLowerCase(Locale.ENGLISH));
    }

    @Override
    public void setProperty(String propertyName, String propertyValue) {
    }

    @Override
    public void setProperty(String propertyName, @SuppressWarnings("rawtypes")
    Vector propertyValue) {

    }

    @Override
    public void addRules(IPropertyRules rules) {

    }

    @Override
    public Vector<IPropertyRules> getRules() {
        return null;
    }

}
