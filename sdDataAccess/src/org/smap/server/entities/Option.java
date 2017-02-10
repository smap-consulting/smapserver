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
import java.lang.reflect.Type;
import java.util.HashMap;
import org.smap.sdal.Utilities.GeneralUtilityMethods;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

public class Option implements Serializable{
	
	private static final long serialVersionUID = -6410553162606844278L;

	// Database attributes
	private int o_id;
	
	private int l_id;
	
	private String label = null;
	
	private String label_id = null;
	
	private String value = null;
	
	private String column_name = null;

	private String cascade_filters = null;
	
	private int seq;						// Order in which options should be displayed
	
	private boolean externalFile;			// Set true if this choice was created by loading an external file
	
	// Other attributes
	private String questionRef = null;	// Reference of the parent question

	private String listName = null;  // Reference to a cascade instance

	private HashMap<String, String> cascadeKeyValues = new HashMap<String, String>();
	
	public Option() {
		
	}
	
	public Option(Option anOption) {
		this.o_id = anOption.getId();
		this.l_id = anOption.getListId();
		this.label = anOption.getLabel();
		this.label_id = anOption.getLabelId();
		this.value = anOption.getValue();
		this.column_name = anOption.getColumnName();
		this.seq = anOption.getSeq();
		this.externalFile = anOption.getExternalFile();
		this.questionRef = anOption.getQuestionRef();
		this.listName = anOption.getListName();
		this.cascadeKeyValues = new HashMap<String, String> (anOption.getCascadeKeyValues());
	}

	/*
	 * Getters
	 */
	public int getId() {
		return o_id;
	}
	
	public int getListId() {
		return l_id;
	}
	
	public String getLabel() {
		return label;
	}
	
	public String getLabelId() {
		return label_id;
	}
	
	public String getValue() {
		return value;
	}
	
	public String getColumnName() {
		return column_name;
	}
	
	public String getCascadeFilters() {
		return cascade_filters;
	}

	public int getSeq() {
		return seq;
	}
	
	public boolean getExternalFile() {
		return externalFile;
	}
	
	public String getQuestionRef() {
		return questionRef;
	}
	
	public String getListName() {
		return listName;
	}
	
	public HashMap<String, String> getCascadeKeyValues() {
		return cascadeKeyValues;
	}
 
	/*
	 * Setters
	 */
    public void setId(int v) {
    	o_id = v;
    }
    
    public void setListId(int value) {
    	l_id = value;
    }
    
	public void setLabel(String label) {
		this.label = label;
	}

	public void setLabelId(String v) {
		label_id = v;
	}
	
	public void setValue(String v) {
		value = v;
		this.column_name = GeneralUtilityMethods.cleanName(v, false, false, false);
	}
	
	public void setColumnName(String v) {
		column_name = v;
	}
       
    public void setSeq(int sequence) {
    	seq = sequence ;
    }
    
    public void setExternalFile(boolean v) {
    	externalFile = v;
    }
    
    public void addCascadeKeyValue(String k, String v) {
    	cascadeKeyValues.put(k, v);
    }
    
    public void setQuestionRef(String qRef) {
    	questionRef = qRef ;
    }
    
    public void setListName(String v) {
    	listName = v;
    }
    
    // Set the filter text string from the list of key value pairs
    public void setCascadeFilters(String v) {
    	cascade_filters = v;
    }
    
    public void setCascadeFilters() {
    	
    	Gson gson=  new GsonBuilder().disableHtmlEscaping().setDateFormat("yyyy-MM-dd").create();
		cascade_filters = gson.toJson(cascadeKeyValues);
    	
    }
    
    // Set the key value pairs from the filter text string
    public void setCascadeKeyValues() {
    	
    	// Handle legacy case where a json string was not used to store cascade filters
    	if ((cascade_filters == null) || (cascade_filters.trim().length() == 0)) {
    		cascade_filters = "{}";
    	} 
    	if(cascade_filters.trim().startsWith("{") && cascade_filters.trim().endsWith("}")) {
    		// New JSON storage
	    	Type type = new TypeToken<HashMap<String, String>>(){}.getType();
			Gson gson=  new GsonBuilder().setDateFormat("yyyy-MM-dd").create();
			cascadeKeyValues = gson.fromJson(cascade_filters, type);
    	} else {
    		// Process cascade key values as space separated key values
        	cascadeKeyValues.clear();
        	
        	String [] kvs = cascade_filters.split(" ");
        	
        	for(int i = 0; i < kvs.length; i += 2) {
        		if((i + 1) < kvs.length) {
        			cascadeKeyValues.put(kvs[i], kvs[i+1]);
        		}
        	}
    	}
		
    }
    

}
