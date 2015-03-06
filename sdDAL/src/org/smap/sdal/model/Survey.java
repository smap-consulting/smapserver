package org.smap.sdal.model;

import java.util.ArrayList;
import java.util.HashMap;

/*
 * Survey Class
 * Used for survey editing
 */
public class Survey {
	public int id;
	public int o_id;
	public int p_id;
	public String pName;
	public String name;
	public String ident;
	public String displayName;
	public String def_lang;
	public String surveyClass;
	public boolean deleted;
	public boolean blocked;
	public boolean hasManifest;
	public ArrayList<Form> forms = new ArrayList<Form> ();
	public ArrayList<Form> forms_orig = new ArrayList<Form> ();		// Original values so that the client can track changes while editing
	public HashMap<String, ArrayList<Option>> optionLists = new HashMap<String, ArrayList<Option>> ();
	public HashMap<String, ArrayList<Option>> optionLists_orig = new HashMap<String, ArrayList<Option>> ();
	public ArrayList<String> languages = new ArrayList<String> (); 
	public ArrayList<Group> groups  = new ArrayList<Group> ();
	public ArrayList<ServerSideCalculate> sscList  = new ArrayList<ServerSideCalculate> ();
	public ArrayList<ManifestValue> surveyManifest  = new ArrayList<ManifestValue> ();
	public ArrayList<ChangeItem> changes  = new ArrayList<ChangeItem> ();
	
	// Getters
	public int getId() {return id;}; 
	public int getPId() {return p_id;};
	public String getPName() {return pName;}; 
	public String getName() {return name;}; 
	public String getIdent() {return ident;};
	public String getDisplayName() {return displayName;}; 
	public boolean getDeleted() { return deleted;};
	public boolean getBlocked() { return blocked;};
	public boolean hasManifest() { return hasManifest;};
	
	// Setters
	public void setId(int v) { id = v;};
	public void setPId(int v) { p_id = v;};
	public void setPName(String v) { pName = v;};
	public void setName(String v) { name = v;};
	public void setIdent(String v) { ident = v;};
	public void setDisplayName(String v) { displayName = v;};
	public void setDeleted(boolean v) { deleted = v;};
	public void setBlocked(boolean v) { blocked = v;};
	public void setHasManifest(boolean v) { hasManifest = v;};
	
	// Get the display name with any HTML reserved characters escaped
	public String getDisplayNameForHTML() {
		return esc(displayName);
	}
	
	// Remove characters reserved for HTML
	private String esc(String in) {
		String out = in;
		if(out != null) {
			out = out.replace("&", "&amp;");
			out = out.replace("<", "&lt;");
			out = out.replace(">", "&gt;");
		}
		return out;
	}
}
