package org.smap.server.utilities;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

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
			int startIdx = path.lastIndexOf('/');
			return path.substring(0, startIdx);
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
	
}
