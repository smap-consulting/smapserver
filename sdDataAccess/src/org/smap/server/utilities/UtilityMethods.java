package org.smap.server.utilities;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Pattern;

import org.smap.model.IE;
import org.smap.server.entities.Option;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class UtilityMethods {

	public static String getLastFromPath(String path) {
		if(path != null) {
			int startIdx = path.lastIndexOf('/') + 1;
			return path.substring(startIdx);
		} else {
			return null;
		}
	}
	
	public static String getGroupFromPath(String path) {
		if(path != null) {
			int startIdx = 0;
			if(path.startsWith("/main")) {
				startIdx = 5;
			}
			
			int endIdx = path.lastIndexOf('/');
			if(endIdx > startIdx) {
				return path.substring(startIdx, endIdx);
			} else {
				return null;
			}
		} else {
			return null;
		}
	}
	
	static String getFirstFromPath(String path) {
		String firstForm = null;
		
		try {
			int startIdx = path.indexOf('/') + 1;
			int lastIdx = path.indexOf('/', startIdx);
			firstForm = path.substring(startIdx, lastIdx);
		} catch (StringIndexOutOfBoundsException e) {		
			
		}
		
		return firstForm; 
	}
	
	static String getQuotedText(String text) {
		int startIdx = text.indexOf('\'') + 1;
    	int endIdx = text.lastIndexOf('\'');
    	return text.substring(startIdx, endIdx);
	}
	
	// XML Parsing methods
	
    /*
     * Method to get the first child element
     * 
     * @param n the node
     * @return Node the first element node
     */
    static Node getFirstElement(Node n) {
    	Node firstElement = null;
    	NodeList children = n.getChildNodes();
    	for(int i = 0; i < children.getLength(); i++) {
    		if(children.item(i).getNodeType() == Node.ELEMENT_NODE) {
    			firstElement = children.item(i);
    		}
    	}
    	return firstElement;
    }
    
    static boolean hasChildElement(Node n) {
    	boolean result = false;
    	
    	NodeList eList = n.getChildNodes();
    	if (eList != null) {
			for (int i = 0; i < eList.getLength(); i++) {
				if(eList.item(i).getNodeType() == Node.ELEMENT_NODE) {
					result = true;
					break;
				}
			}
		}
    	
    	return result;
    }   
	
	/*
	 * TODO replace the next two functions by a single function that sorts any object that
	 * implements the appropriate interface
	 */
	static public void sortElements(List <IE> in) {
		
		java.util.Collections.sort(in, new Comparator<IE>() {
			@Override
			public int compare(IE object1, IE object2) {
				if (object1.getSeq() < object2.getSeq())
					return -1;
				else if (object1.getSeq() == object2.getSeq())
					return 0;
				else
					return 1;
			}
		});
	}
	
	static public void sortOptions(List <Option> in) {
		
		java.util.Collections.sort(in, new Comparator<Option>() {
			@Override
			public int compare(Option object1, Option object2) {
				if (object1.getSeq() < object2.getSeq())
					return -1;
				else if (object1.getSeq() == object2.getSeq())
					return 0;
				else
					return 1;
			}
		});
	}
	
	/*
	 * Convert names in xls format ${ } to xPath
	 */
	public static String convertAllxlsNames(
			String input, 
			boolean forLabel,
			HashMap<String, String> questionPaths,
			int f_id) throws Exception {
		
		String frag = null;
		
		if(input == null) {
			return input;
		}
		
		StringBuffer output = new StringBuffer("");
		
		Pattern pattern = Pattern.compile("\\$\\{.+?\\}");
		java.util.regex.Matcher matcher = pattern.matcher(input);
		int start = 0;
		while (matcher.find()) {
			
			String matched = matcher.group();
			String qname = matched.substring(2, matched.length() - 1);
			
			// Add any text before the match
			int startOfGroup = matcher.start();
			frag = input.substring(start, startOfGroup);
			frag = frag.replaceAll("<", "&lt;");	// Escape angled brackets
			frag = frag.replaceAll(">", "&gt;");	// Escape angled brackets
			output.append(frag);
			
			// If for a label, add the wrapping html
			if(forLabel) {
				output.append("<output value=\"");
			}
			
			// Make sure there is a space before the match
			if(output.length() > 0 && output.charAt(output.length() - 1) != ' ') {
				output.append(' ');
			}
			
			// Add the question path
			String searchName = qname;
			if(searchName.equals("the_geom")) {
				searchName = f_id + searchName;
			}
			String qPath = questionPaths.get(qname);
			if(qPath == null) {
				if(qname.equals("the_geom")) {
					// Try and find any geometry in the survey
					for (String key : questionPaths.keySet()) {
					    if(key.endsWith("the_geom")) {
					    	qPath = questionPaths.get(key);
					    	break;
					    }
					}
				}
				
				if(qPath == null) {
					throw new Exception("Question path not found for question: " + qname);
				}
			}
			output.append(qPath);

			
			// If for a label close the wrapping html
			if(forLabel) {
				output.append(" \"/>");
			}
			
			// Reset the start
			start = matcher.end();

			// Make sure there is a space after the match or its the end of the string
			if(start < input.length()) {
				if(input.charAt(start) != ' ') {
					output.append(' ');
				}
			}
						
		}
		
		// Get the remainder of the string
		if(start < input.length()) {
			frag = input.substring(start);
			frag = frag.replaceAll("<", "&lt;");	// Escape angled brackets
			frag = frag.replaceAll(">", "&gt;");	// Escape angled brackets
			output.append(frag);	
		}
		
		return output.toString().trim();
	}
	
}
