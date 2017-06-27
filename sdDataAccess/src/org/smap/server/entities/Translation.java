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

import org.smap.server.utilities.UtilityMethods;

public class Translation implements Serializable{
	

	private static final long serialVersionUID = 2497891756771818289L;

	// Database attributes
	private int t_id;
	
	private int s_id;
	
	private String language = "default";	// Language Identifier
	
	private String text_id;
	
	private String type;
	
	private String value = null;	// The translated text
	
	// Other attributes
	private boolean enabled = false; // Unique reference to this question

	public Translation() {
		
	}
	
	// Copy constructor
	public Translation(Translation o) {
		this.setSurveyId(o.getSurveyId());
		this.setEnabled(o.getEnabled());
		this.setLanguage(o.getLanguage());
		this.setTextId(o.getTextId());
		this.setType(o.getType());
		this.setValue(o.getValue());
	}

	/*
	 * Getters
	 */
	public int getId() {
		return t_id;
	}
	
	public int getSurveyId() {
		return s_id;
	}
	
	public String getLanguage() {
		return language;
	}
	
	public String getTextId() {
		return text_id;
	}
	
	public String getType() {
		return type;
	}
	
	public String getValue() {
		return value;
	}
	
	// Return the value with XLS names converted into paths
	public String getValueXML(HashMap<String, String> questionPaths, int f_id) throws Exception {
		
		/*
		 * Do our own escaping here
		 * This method should only be called for labels where the output is stored as content inside an
		 * XML element. That is not an attribute so we should only need to escape <>&
		 * The reason for doing this is that ${xlsname} elements are written without escaping whereas all 
		 * styling elements need to be escaped for use in odk.  Hence we escape before converting the xls name to an 
		 * output element.
		 */
		String frag = value;
		frag = frag.replaceAll("&", "&amp;");	// Escape angled brackets
		frag = frag.replaceAll("<", "&lt;");	// Escape angled brackets
		frag = frag.replaceAll(">", "&gt;");	// Escape angled brackets

		return "<t>" + UtilityMethods.convertAllxlsNames(frag, true, questionPaths, f_id) + "</t>";
	
	}
	
	public boolean getEnabled() {
		return enabled;
	}
	 
	/*
	 * Setters
	 */
	public void setId(int v) {
		t_id = v;
	}
	
	public void setSurveyId(int value) {
		s_id = value;
	}

    public void setLanguage(String value) {
    	language = value;
    }
	
	public void setTextId(String id) {
		text_id = id;
	}
	
	public void setType(String type) {
		this.type = type;
	}
	
	public void setValue(String value) {
		// Remove any escaping of XML if this came from an xForm
		value = value.replaceAll("&lt;", "<");
		value = value.replaceAll("&gt;", ">");
		this.value = value;
	}
	
	public void setEnabled(boolean v) {
		this.enabled = v;
	}
	
}
