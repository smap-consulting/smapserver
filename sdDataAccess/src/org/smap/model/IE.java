	package org.smap.model;

/*
 * Instance Element Class
 */
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

public class IE {
	private String name = null;
	private String tableName = null;
	private String value = null;
	private String type = null;	
	private String fType = null;
	private String qType = null;
	private int seq = 0;
	private String path = null;	// The path by which this element is known in the XML template
	private ArrayList <IE> children = new ArrayList <IE> ();
	
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
	
	public void setTableName(String tableName) {
		this.tableName = tableName;
	}
	
	public void setType(String type) {
		this.type = type;
	}
	
	public void setQType(String qType) {
		this.qType = qType;
	}
	
	public void setSeq(int seq) {
		this.seq = seq;
	}
	
	public void setPath(String path) {
		this.path = path;
	}
	
	/*
	 * Other public methods
	 */
	public void addChild(IE child) {
		children.add(child);
	}
	
	/*
	 * Get the key for this record.  Only set if this survey is being updated
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
								|| qt.equals("date")
								|| qt.equals("decimal")
								|| qt.equals("select")
								|| qt.equals("select1")
								|| qt.equals("geopoint")
								|| qt.equals("audio")
								|| qt.equals("image")
								|| qt.equals("video")
								|| qt.equals("dateTime")
								|| qt.equals("time")
								|| qt.equals("barcode")
								|| qt.equals("geoshape")
								|| qt.equals("geotrace")
								|| qt.equals("begin group"))
								{	
							questions.add(child);
							
						} else if (qt.equals("geopolygon")		// Complex questions
								|| qt.equals("geolinestring"))
							{
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
							System.out.println("Warning question ignored, type:" + child.getQType());
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
		if(type.equals("question") && !qType.equals("begin group")) {
			System.out.println(indent + path + ":" + type + ":" + name + "(" + qType + "):" + value);
		}
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
