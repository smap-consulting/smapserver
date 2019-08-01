package org.smap.model;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.smap.server.entities.MissingSurveyException;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class SurveyInstance {
	
	private static Logger log =
			 Logger.getLogger(SurveyInstance.class.getName());
	
	private String templateName = null;
	private String displayName = null;
	private String surveyGeopoint = null;		// A location that can be used as the location of the survey (Used by monitor, some analysis programs)
	private String imei = null;
	
	private String uuid = null;
	
	private int version = 1;
	
	private IE topInstanceElement = null;
	private List<IE> forms = new ArrayList <IE> ();
	//private boolean hasNonTemplateLocation = false;

	public SurveyInstance(InputStream is) throws Exception, MissingSurveyException {
		
		DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
		DocumentBuilder b = dbf.newDocumentBuilder();
		
		Document surveyDocument = b.parse(is);
		Element rootElement = surveyDocument.getDocumentElement();  
		
		// Get the template name
		templateName = rootElement.getAttributeNode("id").getValue();
		if(templateName == null) {
			throw new MissingSurveyException("Error: Survey Template name not found.");
		}
		
		// Get the version
		String versionStr = null;
		Attr versionAttr = rootElement.getAttributeNode("version");
		if(versionAttr != null) {
			versionStr = versionAttr.getValue();
		}
		if(versionStr == null) {
			version = 1;
		} else {
			version = Integer.parseInt(versionStr);
		}
		
		// Create instance elements
		topInstanceElement = new IE(rootElement.getNodeName(), rootElement.getTextContent());
		String path = "/" + rootElement.getNodeName();
		//String path = "/main";				// SMAP assumes path always starts with /main
		topInstanceElement.setPath(path);
		
		processElement(rootElement, topInstanceElement, path);   	
 
	}
	
	public String getTemplateName() {
		return templateName;
	}
	
	public int getVersion() {
		return version;
	}
	
	public String getDisplayName() {
		return displayName;
	}
	
	public String getSurveyGeopoint() {
		return surveyGeopoint;
	}
	
	public String getImei() {
		return imei;
	}
	
	public String getUuid() {
		return uuid;
	}
	
	public IE getTopElement() {
		return topInstanceElement;
	}
	
	public void setDisplayName(String name) {
		displayName = name;
	}
	
	public void setOverallLocation(String ref) {
		List<IE> matches = topInstanceElement.getMatchingElements(ref);
		for(IE match : matches) {
			surveyGeopoint = match.getValue();
			break;
		}
	}
	
	public String getValue(String ref) {
		String value = null;
		List<IE> matches = topInstanceElement.getMatchingElements(ref);
		if(matches.size() > 0) {
			value = matches.get(0).getValue();
		}
		
		return value;
	}
	
	public void setForm(String ref, String tableName, String formType, boolean reference) {
		List<IE> matches = topInstanceElement.getMatchingElements(ref);
		for(IE match : matches) {
			if(reference) {
				match.setType("ref_form");
			} else {
				match.setType("form");
			}
			match.setTableName(tableName);
			match.setQType(formType);
			forms.add(match);
		}
	}
	
	public void setQuestion(String ref, String qType, String qname, boolean phoneOnly, 
			String columnName, String dataType, boolean compressed) {
		List<IE> matches = topInstanceElement.getMatchingElements(ref);
		if(matches.size() == 0 && ref.endsWith("meta/instanceID")) {
			// Also check for _instanceid
			int idx = ref.indexOf("meta/instanceID");
			ref = ref.substring(0, idx) + "_instanceid";
			matches = topInstanceElement.getMatchingElements(ref);
		}
		for(IE match : matches) {
			if(match != null) {
				// If an element has been identified as a form then it is the parent question for a form and should be ignored
				boolean isForm = false;
				if(match.getType() != null && match.getType().equals("form")) {  
					isForm = true;
				}
				if(!isForm) {  
					match.setType("question");
					match.setName(qname);
					match.setColumnName(columnName);
					match.setQType(qType);
					match.setDataType(dataType);
					match.setPhoneOnly(phoneOnly);
					match.setCompressed(compressed);
				}
			}
		}
	}
	
	public void setOption(String qRef, String oName, String oValue, int seq, String columnName) {

		List<IE> matches = topInstanceElement.getMatchingElements(qRef);
		for(IE match : matches) {		
			if(match != null) {
				String [] setOptions = match.getValue().split(" ");
				boolean selected = false;
				for(int i = 0; i < setOptions.length; i++) {
					if(setOptions[i].equals(oValue)) {
						selected = true;
					}
				}
				IE ie = new IE(oName, selected ? "1" : "0");		// Set to "1" or "0" rather than boolean as per issue 35
		   		String oPath = qRef + "/" + oName;
		   		ie.setPath(oPath);
		   		ie.setType("option");
		   		ie.setSeq(seq);
		   		ie.setColumnName(columnName);
				match.addChild(ie);
			}
		}
	}
	
	public List <IE> getForms() {
		return forms;
	}

	/*
	 * Debug method
	 */
	public void printSurveyInstance(String indent) {
		topInstanceElement.printIEModel(indent);
	}

    private void processElement(Node root, IE parent, String path) {
    	
    	NodeList eList = root.getChildNodes();
		if (eList != null) {
			for (int i = 0; i < eList.getLength(); i++) {
				Node n = eList.item(i);			
				if(n.getNodeType() == Node.ELEMENT_NODE) {
					// Discard template elements
					NamedNodeMap  node_map = n.getAttributes();
					Node temp_att = null;
					if(node_map != null) {
						temp_att = node_map.getNamedItem("template");	// enketo returns just template (no jr:) fieldTask/odkCollect do not return the template
					}
					if(temp_att == null) {
						IE ie = new IE(n.getNodeName(), n.getTextContent());
	
						// Save the device name so it can be recorded by the survey upload monitor
						if(n.getNodeName().equals("_device")) {
							imei = ie.getValue();
						} 
						// Save the uuid
						if(n.getNodeName().equals("_instanceid") || 
								(n.getNodeName().equals("instanceID") && parent.getName().equals("meta"))) {
							uuid = ie.getValue();
						} 
						
						parent.addChild(ie);
				   		String childPath = path + "/" + n.getNodeName();
				   		ie.setPath(childPath);
						processElement(n, ie, childPath);	// Recursively process the XML tree
					} else {
						log.info("Ignoring template node: " + n.getTextContent());
					}
				}
			}
		}
    }
    

}
