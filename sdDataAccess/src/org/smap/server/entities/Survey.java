/*****************************************************************************

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

 ******************************************************************************/

package org.smap.server.entities;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;

import org.smap.sdal.Utilities.GeneralUtilityMethods;
import org.smap.sdal.model.MetaItem;
import org.smap.server.utilities.UtilityMethods;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

/*
 * Class to store Survey objects
 */
public class Survey implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 2645224176464784459L;

	// Database Attributes
	private int id;
	
	private String name;
	
	private String ident;
	
	private String display_name;
	
	private String def_lang;
	
	private int p_id;
	
	private boolean deleted = false;
	
	private int version;
	
	private String manifest;
	
	private String instanceName;
	
	private boolean timingData;
	
	private boolean auditLocationData;
	
	private boolean trackChanges;
	
	private boolean hideOnDevice;
	
	private String surveyClass;
	
	private boolean loaded_from_xls = false;
	
	private String hrk = null;
	
	private ArrayList<MetaItem> meta = null;
	
	/*
	 * Constructor
	 */
	public Survey() {
	}
	
	/*
	 * Getters
	 */
	public int getId() {
		return id;
	}
	
	public String getIdent() {
		return ident;
	}
	
	public String getName() {
		return name;
	}
	
	public String getDisplayName() {
		return display_name;
	}
	
	public String getDefLang() {
		return def_lang;
	}
	
	public String getSurveyClass() {
		return surveyClass;
	}
	
	public boolean getLoadedFromXls() {
		return loaded_from_xls;
	}
	
	public int getProjectId() {
		return p_id;
	}
	
	public boolean getDeleted() {
		return deleted;
	}
	
	public int getVersion() {
		return version;
	}
	
	public String getManifest() {			// deprecated
		return manifest;
	}
	
	public String getInstanceName() {
		return instanceName;
	}
	
	public boolean getTimingData() {
		return timingData;
	}
	
	public boolean getAuditLocationData() {
		return auditLocationData;
	}
	
	public boolean getTrackChanges() {
		return trackChanges;
	}
	
	public boolean getHideOnDevice() {
		return hideOnDevice;
	}
	
	// Get the display name with any HTML reserved characters escaped
	public String getDisplayNameForHTML() {
		return GeneralUtilityMethods.esc(display_name);
	}
	
	public String getHrk() {
		return hrk;
	}
	
	public ArrayList<MetaItem> getMeta() {
		return meta;
	}
	
	/*
	 * Setters
	 */
	
	public void setId(int v) {
		id = v;
	}
	
	public void setName(String v) {
		name = v;
	}
	
	public void setDisplayName(String v) {
		display_name = v;
	}
	
	public void setIdent(String v) {
		ident = v;
	}
	
	public void setFileName(String v) {
		name = v;
	}
	
	public void setDefLang(String v) {
		def_lang = v;
	}
	
	public void setSurveyClass(String v) {
		surveyClass = v;
	}
	
	public void setLoadedFromXls(boolean v) {
		loaded_from_xls = v;
	}
	
	public void setProjectId(int v) {
		p_id = v;
	}
	
	public void setDeleted(boolean v) {
		deleted = v;
	}
	
	public void setVersion(int v) {
		version = v;
	}
	
	public void setManifest(String v) {
		manifest = v;
	}
	
	public void setInstanceName(String v) {
		instanceName = v;
	}
	
	public void setTimingData(boolean v) {
		timingData = v;
	}
	
	public void setAuditLocationData(boolean v) {
		auditLocationData = v;
	}
	
	public void setTrackChanges(boolean v) {
		trackChanges = v;
	}
	
	public void setHideOnDevice(boolean v) {
		hideOnDevice = v;
	}
	
	public void setHrk(String v) {
		hrk = v;
	}
	
	public void setMeta(String v) {
		if(v != null) {
			Gson gson = new GsonBuilder().disableHtmlEscaping().create();
			meta = gson.fromJson(v, new TypeToken<ArrayList<MetaItem>>() {}.getType());
		}
	}
	

}
