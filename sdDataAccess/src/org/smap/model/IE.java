	package org.smap.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Logger;

public class IE {
	
	private static Logger log =
			 Logger.getLogger(IE.class.getName());
	
	private String name = null;
	private String tableName = null;
	private String columnName = null;
	private String value = null;
	private String type = null;	
	private String qType = null;
	private String dataType = null;
	private boolean phoneOnly = false;		// Security setting to prevent sensitive data being stored on server
	private int seq = 0;
	private String path = null;	// The path by which this element is known in the XML template
	private ArrayList <IE> children = new ArrayList <IE> ();
	private boolean compressed = false;		// Set true for select multiples to stores data in a single column
	
	public IE(String name, String value) {
		this.name = name;
		this.value = value;
	}

	/*
	 * Getters
	 */
	public String getName() {
		return name;
	}
	
	public String getColumnName() {
		return columnName;
	}
	
	public String getTableName() {
		return tableName;
	}
	
	public String getValue() {
		return value;
	}

	/*
	 * type can be
	 * 	form
	 *  question
	 *  option
	 */
	public String getType() {
		return type;
	}
	
	public String getQType() {
		return qType;
	}
	
	public String getDataType() {
		return dataType;
	}
	
	public boolean isPhoneOnly() {
		return phoneOnly;
	}
	
	public boolean isCompressed() {
		return compressed;
	}
	
	public int getSeq() {
		return seq;
	}
	
	public String getPath() {
		return path;
	}
	
	
	/*
	 * Setters
	 */
	public void setName(String v) {
		this.name = v;
	}
	
	public void setTableName(String v) {
		this.tableName = v;
	}
	
	public void setColumnName(String v) {
		this.columnName = v;
	}
	
	public void setType(String v) {
		this.type = v;
	}
	
	public void setQType(String v) {
		this.qType = v;
	}
	
	public void setDataType(String v) {
		this.dataType = v;
	}
	
	public void setPhoneOnly(boolean v) {
		this.phoneOnly = v;
	}
	
	public void setCompressed(boolean v) {
		this.compressed = v;
	}
	
	public void setSeq(int v) {
		this.seq = v;
	}
	
	public void setPath(String v) {
		this.path = v;
	}
	
	/*
	 * Other public methods
	 */
	public void addChild(IE child) {
		children.add(child);
	}
	
	/*
	 * Get the key for this record.  Only set if this survey is being updated
	 * Deprecated
	 */
	public String getKey() {
		String qn = null;
		
		// Check that this is a form
		if(type.equals("form")) {
			for(IE child : children) {
				qn = child.getName();
				if(qn != null) {
					if(qn.equals("_task_key")) { 		// The key element
						String key = child.getValue();
						if(key != null) {
							if(key.trim().length() == 0) {
								key = null;
							}
						}
						return key;
						
					} 
				}
			}
		}
		
		
		return null;		
	}
	
	
	public List<IE> getQuestions() {
		List<IE> questions = new ArrayList<IE> ();
		String qt = null;
		HashMap <String, IE> complexQuestions = new HashMap <String, IE> ();
		
		// Check that this is a form or a non repeating group
		if(type.equals("form") || getQType().equals("begin group") 
				|| getQType().equals("geopolygon")
				|| getQType().equals("geolinestring")) {
			for(IE child : children) {
				if(child.getType() != null) {
					qt = child.getQType();
					if(qt != null) {
						if(qt.equals("string") 		// simple questions
								|| qt.equals("int")
								|| qt.equals("note")
								|| qt.equals("date")
								|| qt.equals("decimal")
								|| qt.equals("select")
								|| qt.equals("select1")
								|| qt.equals("geopoint")
								|| qt.equals("audio")
								|| qt.equals("image")
								|| qt.equals("video")
								|| qt.equals("file")
								|| qt.equals("dateTime")
								|| qt.equals("range")
								|| qt.equals("time")
								|| qt.equals("barcode")
								|| qt.equals("geoshape")
								|| qt.equals("geotrace")
								|| qt.equals("acknowledge")
								|| qt.equals("calculate")
								|| qt.equals("rank")
								|| qt.equals("odk:rank")
								|| qt.equals("begin group")) {
							questions.add(child);
							
						} else if (qt.equals("geopolygon")		// Complex questions
								|| qt.equals("geolinestring")) {
							// Get the constructed complex question
							String name = child.getName();
							IE complex = complexQuestions.get(name);
							if(complex == null) {	// a new one
								complex = new IE(name, "");
								complex.setType("question");
								complex.setQType(qt);
								complexQuestions.put(name, complex);
							}
							
							// Get any geopoints that are members of this complex question and add them
							List <IE> attributes = child.getChildren();
							for(IE att : attributes) {
								if(att.getQType() != null && att.getQType().equals("geopoint")) {
									complex.addChild(att);
								}
										
							}
							
						} else {
							if(child.getQType() != null && !child.getQType().equals("form") && !child.getQType().equals("note")) {
								log.info("Warning question ignored, type:" + child.getQType());
							}
						}
					}
				}
			}
		}
		
		// Add the constructed complex questions
		List<IE> cList = new ArrayList<IE> (complexQuestions.values());
		for(IE item : cList) {
			questions.add(item);
		}
		
		return questions;
	}
	
	public List<IE> getChildren() {
		return children;
	}

	
	public String getQValue(String name) {
		String value= null;
		
		List<IE> questions = getQuestions();
		for(IE q : questions) {
			if(q.name.equals(name)) {	
				value = q.value;
				break;
			}
		}
		
		return value;
	}
	


	/*
	 * Get all questions in the survey including from child forms
	 */
	public List<String> getMedia(String type) {
		List<String> media = new ArrayList<String>();
		
		List<IE> questions = getQuestions();
		for(IE q : questions) {
			if(q.getType().equals("question") && q.getQType().equals(type)) {
				media.add(q.getValue());
			}
		}
		
		List<IE> children = getChildren();
		for(IE child : children) {
			media.addAll(child.getMedia(type));
		}
		
		return media;
	}
	
	
	/*
	 * Debug methods
	 */
	public void printIEModel(String indent) {
		//if(type.equals("question") && !qType.equals("begin group")) {
			System.out.println(indent + path + ":" + type + ":" + name + "(" + qType + "):" + value);
		//}
		for(IE child : children) {
			child.printIEModel(indent + "    ");
		}
	}
	
	public List<IE> getMatchingElements(String aPath) {
		List<IE> matches = new ArrayList<IE> (); 
		
		if(path.equals(aPath)) {
			matches.add(this);
		}
		
		for(IE child : children) {
			List<IE> newMatches = child.getMatchingElements(aPath);
			matches.addAll(newMatches);			
		}
	
		return matches;
	}
	

}
