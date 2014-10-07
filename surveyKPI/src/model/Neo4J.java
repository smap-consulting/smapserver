package model;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;

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

/*
 * Data object for Neo4J / thingsat export
 */
public class Neo4J  {
	
	// Data values for node
	public String name;		// Must be unique within a model across relations and nodes
	public Property label;
	public ArrayList<Property> properties;
	
	// Additional data values for link
	public int source;		// The index in the node list of the from node
	public int target;
	
	// Temporary data not stored in database
	File f;
	PrintWriter w;
	HashMap <String, String> keys;					// Contains keys already written to the file
	HashMap <String, String> multipleChoiceKeys;	// Contains keys for each option where there is a multiple choice question in the properties
	String currentKey;
	int numberKeys;	
	public boolean isRelation;
	
	public Neo4J(Neo4JDO elem, boolean isRelation) {
		
		this.isRelation = isRelation;
		this.source = elem.source;
		this.target = elem.target;
		
		name = elem.label.value;
		label = elem.label;

		properties = elem.properties;
		keys = new HashMap<String, String> ();

	}
	

	public void createDataFile(String filepath) throws FileNotFoundException {
		f = new File(filepath, name + ".csv");
		w = new PrintWriter(f);
	}
	
	/*
	 * Write the data to the file
	 * If the key is not null check that we have not already written this data record
	 */
	
	public void writeOutput(String key, String output) {
		
		// Check that this is new
		if(key != null && key.trim().length() > 0) {
			if(keys.get(key) != null) {
				return;
			} else {
				keys.put(key, output);
			}
			
		} else {
			key ="";
		}
		
		w.println("\"" + key + "\"" + output);
		
			
	}
	
	public String getLoaderText() {
		String text = null;
		text = "load csv with headers from \"" + f.getAbsolutePath() + "\"";
		return text;
		
	}
	
	public void closeFile() {
		w.close();
	}
	
	public void debug() {
		System.out.println("::" + label.value);
		if(isRelation) {
			System.out.println("  " + source + " to " + target);
		}

		for(int i = 0; i < properties.size(); i++) {
			properties.get(i).debug();
		}
		
	}
	
	/*
	 * Write the header of the csv file for a node or relation
	 */
	void writeHeader() {
		
		StringBuffer output = new StringBuffer();
		output.append("id");

		if(isRelation) {
			output.append(",from,to");
		}
		
		for(Property p : properties) {
			output.append(",");
			output.append("\"");
			String val = p.key;
			if(val == null) {
				val = p.colName;
			}
			output.append(val);
			output.append("\"");		
		}
		
		w.println(output.toString());

	}
	
	public void writeData(ResultSet rs, Thingsat things) throws SQLException {
		
		

		if(isRelation) {
			System.out.println("======================= Is Relation =================");
			System.out.println("    " + name);
			Property sourceSelectMultipleProperty = things.nodes.get(source).getSelectMultiple();
			Property targetSelectMultipleProperty = things.nodes.get(target).getSelectMultiple();
			
			if(sourceSelectMultipleProperty != null && targetSelectMultipleProperty != null) {
				System.out.println("    Target and Source have select multiple");
				for(String sourceOption : sourceSelectMultipleProperty.optionValues) {
					for(String targetOption : targetSelectMultipleProperty.optionValues) {
						writeRecord(rs, things, null, sourceOption, targetOption);
					}
				}
			} else if(sourceSelectMultipleProperty != null) {
				System.out.println("    Source has select multiple");
				for(String sourceOption : sourceSelectMultipleProperty.optionValues) {
					writeRecord(rs, things, null, sourceOption, null);
				}
			} else if(targetSelectMultipleProperty != null) {
				System.out.println("    Target has select multiple");
				for(String targetOption : targetSelectMultipleProperty.optionValues) {
					System.out.println("    Writing relation for targetOption: " + targetOption);
					writeRecord(rs, things, null, null, targetOption);
				}
			} else  {
				System.out.println("    No Select Multiples");
				writeRecord(rs, things, null, null, null);
			}
			
		} else {
			Property selectMultipleProperty = getSelectMultiple();
			multipleChoiceKeys = new HashMap<String,String>();
			if(selectMultipleProperty != null) {
				for(String option : selectMultipleProperty.optionValues) {
					writeRecord(rs, things, option, null, null);
				}
			} else {
				writeRecord(rs, things, null, null, null);
			}
		}

	}
	
	private Property getSelectMultiple() {
		for(Property p : properties) {
			if(p.q_type != null && p.q_type.equals("select")) {
				return p;
			}
		}
		return null;
	}
	
	private void writeRecord(ResultSet rs, Thingsat things, String option, 
			String sourceOption,
			String targetOption) throws SQLException {
		

		StringBuffer output = new StringBuffer();
		StringBuffer key = new StringBuffer();
		
		if(isRelation) {
			String sourceKey = null; 
			String targetKey = null;
			if(sourceOption != null) {
				sourceKey = things.nodes.get(source).multipleChoiceKeys.get(sourceOption);
			} else {
				sourceKey = things.nodes.get(source).currentKey;
			}
			if(targetOption != null) {
				targetKey = things.nodes.get(target).multipleChoiceKeys.get(targetOption);
			} else {
				targetKey = things.nodes.get(target).currentKey;
			}
			
			if(sourceKey == null || targetKey == null) {
				return;		// Missing end point, can happen with select multiple where an option was not selected
			}
			System.out.println("CurrentKey : " + currentKey + " sourceKey: " + sourceKey + " targetKey: " + targetKey);
			output.append(",\"");
			output.append(sourceKey);
			output.append("\",");
			output.append("\"");
			output.append(targetKey);
			output.append("\"");
		}
		
		for(Property p : properties) {
			String v = "";
			
			if(p.value_type == null) {	// Allow for bad data during development, TODO remove
				p.value_type = "record";
			}
			
			if(p.value_type.equals("record")) {
				String colName = p.colName;
				if(option != null && p.q_type.equals("select")) {
					colName = colName + "__" + option;
					boolean selected = rs.getBoolean(colName);
					if(selected) {
						v = option;
					} else {
						return;			// Multi select option not selected, don't create the node or relation
					}
				} else {
					System.out.println("###### question type: " + p.q_type);
					
					v = rs.getString(colName);
				}
			} else if(p.value_type.equals("constant")) {
				v = p.value;
			} else if(p.value_type.equals("record_suid")) {
				v = rs.getString("_record_suid");
			}
			if(p.unique) {
				key.append(v);
			} 
			output.append(",\"");		
			output.append(v);
			output.append("\"");
		}
		
		String fullKey = key.toString();
		if(option != null) {
			fullKey += option;
		}
		if(sourceOption != null) {
			fullKey += sourceOption;
		}
		if(targetOption != null) {
			fullKey += targetOption;
		}
		writeOutput(fullKey, output.toString());
		if(option != null) {
			multipleChoiceKeys.put(option, fullKey);
		} else {
			currentKey = fullKey.toString();
		}
	}
	
}
