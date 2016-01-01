package org.smap.server.utilities;

import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Stack;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Result;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.smap.model.CascadeInstance;
import org.smap.model.SurveyTemplate;
import org.smap.model.Results;
import org.smap.sdal.Utilities.ApplicationException;
import org.smap.sdal.Utilities.ResultsDataSource;
import org.smap.server.entities.Form;
import org.smap.server.entities.Option;
import org.smap.server.entities.Question;
import org.smap.server.entities.Survey;
import org.smap.server.entities.Translation;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;


/*
 * Return an XForm built from a survey defined in the database
 * 
 * Translations are stored as a hierarchy of values from language down to element type (as below)
 *   languages (HashMap)-> translation id's (HashMap)-> translation types (HashMap) -> translation element (String)
 */
public class GetXForm {
	private int INSTANCE = 1;		// Identifies which part of the XML document is being updated with form details
	private int BIND = 2;
	private int BODY = 3;
	private Form firstForm;
	SurveyTemplate template = null;
	private String gInstanceId = null;
	private String gSurveyClass = null;
	private ArrayList<String> gFilenames;

	private static Logger log =
			 Logger.getLogger(GetXForm.class.getName());
	
	public GetXForm() {

	}

    /*
     * Get the XForm as a string
     */
    public String get(SurveyTemplate template) {
     	   
       	String response = null;
       	this.template = template;
  	    	
    	try {
    		log.info("Getting survey as XML-------------------------------");
    		// Create a new XML Document
    		DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
    		DocumentBuilder b = dbf.newDocumentBuilder();    		
    		Document outputXML = b.newDocument(); 
    		
           	Writer outWriter = new StringWriter();
           	Result outStream = new StreamResult(outWriter);
           	
           	// Get the first form
           	String firstFormRef = template.getFirstFormRef();
           	if(firstFormRef == null) {
           		log.log(Level.SEVERE, "Error: First Form Reference is null");
           	}
           	firstForm = template.getForm(firstFormRef);
  		
    		Element parent;
        	parent = populateRoot(outputXML);
        	populateHead(outputXML, b, parent);
        	populateBody(outputXML, parent);

    		// Write the survey to a string and return it to the calling program
        	Transformer transformer = TransformerFactory.newInstance().newTransformer();
        	transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        	transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        	transformer.setOutputProperty(OutputKeys.METHOD, "xml");
        	
        	DOMSource source = new DOMSource(outputXML);
        	transformer.transform(source, outStream);
        	
        	response = outWriter.toString();
        	
	
    	} catch (Exception e) {
    		response = e.getMessage();
    		e.printStackTrace();
    	}

    	return response;
 
    }
    
    /*
     * Create the root element
     * @param outputDoc
     */
    public Element populateRoot(Document outputDoc) {
   	   	
    	Element rootElement = outputDoc.createElement("h:html");
    	rootElement.setAttribute("xmlns", "http://www.w3.org/2002/xforms");
    	rootElement.setAttribute("xmlns:ev", "http://www.w3.org/2001/xml-events");
    	rootElement.setAttribute("xmlns:h", "http://www.w3.org/1999/xhtml");
    	rootElement.setAttribute("xmlns:jr", "http://openrosa.org/javarosa");
    	rootElement.setAttribute("xmlns:xsd", "http://www.w3.org/2001/XMLSchema");
    	outputDoc.appendChild(rootElement);  
    	
    	return rootElement;
    }

    /*
     * Populate the head element
     * @param outputXML
     */
    public void populateHead(Document outputDoc, DocumentBuilder documentBuilder, Element parent) {

    	Survey s = template.getSurvey();
    	
    	// Create Elements
    	Element headElement = outputDoc.createElement("h:head");
    	parent.appendChild(headElement);
    	
    	Element titleElement = outputDoc.createElement("h:title");
    	titleElement.setTextContent(s.getDisplayName());		
    	headElement.appendChild(titleElement);
    	
    	Element modelElement = outputDoc.createElement("model");
    	headElement.appendChild(modelElement);
    	
    	Element itextElement = outputDoc.createElement("itext");
    	modelElement.appendChild(itextElement);
    	populateItext(outputDoc, documentBuilder, itextElement);
    	
    	Element instanceElement = outputDoc.createElement("instance");
    	modelElement.appendChild(instanceElement); 	
    	populateInstance(outputDoc, instanceElement);
    	
    	if(template.hasCascade()) {
    		
    		List<CascadeInstance> cis = template.getCascadeInstances();
    		for(int i = 0; i < cis.size(); i++) {
    		   	Element cascadeInstanceElement = outputDoc.createElement("instance");
				cascadeInstanceElement.setAttribute("id", cis.get(i).name);
    	    	modelElement.appendChild(cascadeInstanceElement); 
    	    	Element rootElement = outputDoc.createElement("root");
    	    	cascadeInstanceElement.appendChild(rootElement);
    	    	populateCascadeOptions(outputDoc, rootElement, cis.get(i));
    		}
    	}
    	// Add forms to bind elements
    	if(firstForm != null) {		
    		populateForm(outputDoc, modelElement, BIND, firstForm);
    	}

    	
    	   	
    }

    /*
     * Populate the itext element with language translations
     */
    public void populateItext(Document outputDoc, DocumentBuilder builder, Element parent) {
    	
       	Survey s = template.getSurvey();
    	enableTranslationElements(firstForm);	// Enable the translations that are actually used
    	
    	HashMap<String, HashMap<String, HashMap<String, Translation>>> translations = template.getTranslations();
		// Write the translation objects
		Collection<HashMap<String, HashMap<String, Translation>>> c = translations.values();
		Iterator<HashMap<String, HashMap<String, Translation>>> itr = c.iterator();
		while (itr.hasNext()) {
			HashMap<String, HashMap<String, Translation>> aLanguageTranslation = itr.next();	// A single language
			Collection<HashMap<String, Translation>> l = aLanguageTranslation.values();
			Iterator<HashMap<String, Translation>> itrL = l.iterator();
			Element languageElement = null;
			while(itrL.hasNext()) {									// ID of a question or label
				HashMap<String,Translation> types = (HashMap<String, Translation>) itrL.next();
				
				Collection<Translation> t = types.values();
				Iterator<Translation> itrT = t.iterator();

				Element textElement = null;
				while(itrT.hasNext()) {

					Translation trans = (Translation) itrT.next();
					
					if(trans.getEnabled()) {
					
						if(languageElement == null) {
							languageElement = outputDoc.createElement("translation");	
							languageElement.setAttribute("lang", trans.getLanguage());
							if(s.getDefLang() != null && s.getDefLang().equals(trans.getLanguage())) {
								languageElement.setAttribute("default", "true()");	// set default language
							}
							parent.appendChild(languageElement);
						}
						
						if(textElement == null) {
							textElement = outputDoc.createElement("text");
							textElement.setAttribute("id", trans.getTextId());
							languageElement.appendChild(textElement);
						}
	
						Element valueElement = outputDoc.createElement("value");
	
						/*
						 * Add the translation XML fragment to the output 
						 */
						String type = trans.getType().trim();
						Document xfragDoc;
						if(type.equals("image") || type.equals("video") || type.equals("audio")) {
							String base = type;
							if(type.equals("image")) {
								base = "images";
							}
							String fileLocn = trans.getValue();	// Location of file on disk, only file name is used by fieldTask
							String filename = "";
							if(fileLocn != null) {
								int idx = fileLocn.lastIndexOf('/');
								if(idx > 0) {
									filename = fileLocn.substring(idx+1);
								} else {
									filename = fileLocn;
								}
							}
							
							valueElement.setTextContent("jr://" + base + "/" + filename);
							
						} else {
							// The text could be an xml fragment
							try {
								xfragDoc = builder.parse(new InputSource(new StringReader(trans.getValueXML())));
								Element rootFrag = xfragDoc.getDocumentElement();
								addXmlFrag(outputDoc, valueElement, rootFrag);
							} catch (Exception e) {
								e.printStackTrace();
							}
						}
	
						if(!type.equals("none")) {
							valueElement.setAttribute("form", type);
						}
			
						textElement.appendChild(valueElement);
					}
				}
			}
		}
    }
    
    /*
     * Method to add an XML fragment to another XML element
     */
    private void addXmlFrag(Document outputDoc, Element main, Element frag) {
    	NodeList fragList = frag.getChildNodes();
		if (fragList != null) {
			for (int i = 0; i < fragList.getLength(); i++) {
				main.appendChild(outputDoc.importNode(fragList.item(i), true));
			}
		}
    }
    
    /*
     * Enable the translation elements if there is an enabled question that references the element
     * This function is required because not all questions in a survey are enabled
     *  and it would be inefficient to download the iText for disabled questions
     */
    public void enableTranslationElements(Form f) {
       	if(firstForm != null) {
   
       	   	HashMap<String, HashMap<String, HashMap<String, Translation>>> translations = template.getTranslations();
       	   	Collection<HashMap<String, HashMap<String, Translation>>> c = translations.values();

       	   	
	    	List <Question> questions = f.getQuestions();		
	    	for(Question q : questions) {
	    		
	    		if(q.getEnabled()) {
	    			String labelRef = q.getQTextId();
	    			String hintRef = q.getInfoTextId();
	    			
	    			enableTranslationRef(c, labelRef);
	    			enableTranslationRef(c, hintRef);
	    			
	    			// If this is a choice question, add the items
	    			if(q.getType().startsWith("select")) {
	    			   	Collection <Option> options = q.getChoices(); 
	    		    	List <Option> optionList = new ArrayList <Option> (options);
	    		    	
	    		    	for(Option o : optionList) {
	    		    		String oRef = o.getLabelId();
	    		    			enableTranslationRef(c, oRef);
	    		    	}
	    			}
	    			
	    			// If this is a repeating group then add the questions from the sub form
	    			if(q.getType().equals("begin repeat") || q.getType().equals("geolinestring") || q.getType().equals("geopolygon")) {
	    				
	    				Form subForm = template.getSubForm(f,q);			
	    				enableTranslationElements(subForm);
	    				
	    			}
	    			
	    		} else {
	    			log.info("----------Not enabled:" + q.getName());
	    		}
	    	}

       	}	
    	
    }
       	
    /*
     * Enable the translation element for the provided reference
     */
    public void enableTranslationRef(Collection<HashMap<String, HashMap<String, Translation>>> c, String ref) {
		Iterator<HashMap<String, HashMap<String, Translation>>> itr = c.iterator();

		if (ref != null) {
			while (itr.hasNext()) {	// Get each language
				HashMap <String, HashMap<String, Translation>> l = itr.next();	// Single language
				HashMap <String, Translation> types = l.get(ref);	// Get the types for this string identifier
				
				if(types != null) {
					Collection<Translation> tCollection = types.values();
					Iterator<Translation> itrT = tCollection.iterator();
					while(itrT.hasNext()) {								// Enable all types for this reference
						Translation t = (Translation) itrT.next();
						t.setEnabled(true);
					}
				} else {
					log.info("Error in enableTranslationRef(): No types for:" + ref);
				}
			}
		}
    }
    
    /*
     * Populate the Instance element starting with the top level form
     */
    public void populateInstance(Document outputDoc, Element parent) {
    	
    	if(firstForm != null) {
    		Element formElement = outputDoc.createElement(firstForm.getName());
    		formElement.setAttribute("id", template.getSurvey().getIdent()); 
    		formElement.setAttribute("version", String.valueOf(template.getSurvey().getVersion()));
    		formElement.setAttribute("project", String.valueOf(template.getProject().getName()));
    		populateForm(outputDoc, formElement, INSTANCE, firstForm); 	// Process the top level form
    		parent.appendChild(formElement);   		
    	}
    }
    
    /*
     * Populate the Cascade Instance element
     */
    public void populateCascadeInstance(Document outputDoc, Element parent, String instance) {
    	
    	log.info("TODO: Add value name and label name for instance:" + instance);

    	
    }

 
    
    public void populateBody(Document outputDoc, Element parent) {
    	Element bodyElement = outputDoc.createElement("h:body");
    	/*
    	 * Add class if it is set
    	 */
    	String surveyClass = template.getSurveyClass();
    	if(surveyClass != null) {
    		bodyElement.setAttribute("class", surveyClass);
    		gSurveyClass = surveyClass;
    	}
       	if(firstForm != null) {
    		populateForm(outputDoc, bodyElement, BODY, firstForm); 		// Process the top level form
    	}
    	
    	parent.appendChild(bodyElement);
    }
    
    /*
     * Add the form data
     * The actual data added will vary depending on where we are in the XForm
     * @param outputXML
     * @param parent
     * @param location
     * @param f
     * @param parentXPath
     */
    public void populateForm(Document outputDoc, Element parentElement, int location, Form f) {

    	Element currentParent = parentElement;
       	Stack<Element> elementStack = new Stack<Element>();	// Store the elements for non repeat groups
    	    	
		/*
		 * Add the questions from the template
		 */
    	List <Question> questions = f.getQuestions();		
    	for(Question q : questions) {
    		
    		// Skip questions that are not enabled
    		if(!q.getEnabled()) {
    			continue;
    		}
    		
        	Element questionElement = null;
        	String qType = q.getType();
        	  
    		if(location == INSTANCE) {    			
    			if(qType.equals("begin repeat") || qType.equals("geolinestring") || qType.equals("geopolygon")) {
    				
    				Form subForm = template.getSubForm(f,q);
    				Element formElement_template = outputDoc.createElement(subForm.getName());
    				formElement_template.setAttribute("jr:template", "");
    				populateForm(outputDoc, formElement_template, INSTANCE, subForm);	
    				currentParent.appendChild(formElement_template);
    				
    			} else if(qType.equals("begin group")) {
    				
    				// Write the question then make this element the new parent
    				questionElement = outputDoc.createElement(q.getName());
    				currentParent.appendChild(questionElement);
    				
    				elementStack.push(currentParent);
					currentParent = questionElement;
    				
    			} else if(qType.equals("end group")) {	
    				
    				currentParent = elementStack.pop();

    			} else {
    						
      				questionElement = outputDoc.createElement(UtilityMethods.getLastFromPath(q.getPath()));
    				if(q.getDefaultAnswer() != null) {
    					questionElement.setTextContent(q.getDefaultAnswer());
    				}
					
    				currentParent.appendChild(questionElement);			
    			} 			
    			
    		} else if(location == BIND) {
    			
    			//if(subForm != null) {
       			if(qType.equals("begin repeat") || qType.equals("geolinestring") || qType.equals("geopolygon")) {
    				Form subForm = template.getSubForm(f,q);
    				populateForm(outputDoc, currentParent, BIND, subForm);
    				questionElement = populateBindQuestion(outputDoc, f, q, f.getPath());
    				currentParent.appendChild(questionElement);
    				
    			} else if (q.getType().equals("begin group")) {
    				
    				questionElement = populateBindQuestion(outputDoc, f, q, f.getPath());
					currentParent.appendChild(questionElement);
					
    			} else if(q.getType().equals("end group")) { 
    				
    			} else {
    			
    				questionElement = populateBindQuestion(outputDoc, f, q, f.getPath());					
    				currentParent.appendChild(questionElement);
    				
    			}   						
    			
    		} else if(location == BODY) {
    			//if(subForm != null) {
    			if(qType.equals("begin repeat") || qType.equals("geolinestring") || qType.equals("geopolygon")) {
    				Form subForm = template.getSubForm(f,q);
    				
    				Element groupElement = outputDoc.createElement("group");
    				currentParent.appendChild(groupElement);
    				
    				Element labelElement = outputDoc.createElement("label");
    				String labelRef = subForm.getLabel();
    				if(labelRef != null && !labelRef.trim().isEmpty()) {
    					String label = "jr:itext('" + labelRef + "')";
    					labelElement.setAttribute("ref", label);
    				}
    				groupElement.appendChild(labelElement);
    				
    				Element repeatElement = outputDoc.createElement("repeat");
    				repeatElement.setAttribute("nodeset", subForm.getPath());
    				String repeats = subForm.getRepeats();
    				if(repeats != null) {		// Add the repeat count if it exists
    					repeatElement.setAttribute("jr:count", repeats);
    					repeatElement.setAttribute("jr:noAddRemove", "true()");
    				}
    				groupElement.appendChild(repeatElement);
    				   				
    				populateForm(outputDoc, repeatElement, BODY, subForm);

    			} else {	// Add question to output
    				if(q.isVisible()) {
    					
    					questionElement = populateBodyQuestion(outputDoc, f, q, f.getPath());	
    					currentParent.appendChild(questionElement);

    				}
    			}
    			
    			/*
    			 * Set the parent element according to whether we are entering or leaving a non repeat group
    			 */
    			if(qType.equals("end group")) {
    				
       				currentParent = elementStack.pop();
       				// currentParentPath = pathStack.pop();
       				
    			} else if (qType.equals("begin group")) {
    				//pathStack.push(currentParentPath);
            		//currentParentPath = currentParentPath + "/" + q.getName();
            		
       				elementStack.push(currentParent);
    				currentParent = questionElement;
    			}
    			
    		}

    		
    	}
    }
   
    /*
     * Populate a repeating group
     */
    public void createRepeatingGroup(Document outputXML, Element parent, Form subF, int location, 
    		String parentXPath, Question parentQuestion) {

		if(location == INSTANCE) {
			
			Element subFormParent = outputXML.createElement(parentQuestion.getName());
			populateForm(outputXML, subFormParent, location, subF);
			parent.appendChild(subFormParent);
			
		} else if(location == BIND) {
			
			populateForm(outputXML, parent, location, subF);
			
		} else {		// BODY
			
			Element subFormParent = outputXML.createElement("group");  
			subFormParent.setAttribute("ref", subF.getPath());
			
			// TODO Sets the repeat label to the parent question - Is this right?
			Element labelElement = outputXML.createElement("label");
			String jrRef = "jr:itext('" + parentQuestion.getQTextId() + "')";
			labelElement.setTextContent(jrRef);
			subFormParent.appendChild(labelElement);
			
			Element repeatElement = outputXML.createElement("repeat");  
			repeatElement.setAttribute("nodeset", subF.getPath());
			subFormParent.appendChild(repeatElement);
			
			populateForm(outputXML, repeatElement, location, subF);
			parent.appendChild(subFormParent);
			
		}
		
    	
    }
    
    /*
     * Populate the question element if this is part of the XForm bind
     */
    public Element populateBindQuestion(Document outputXML, Form f, Question q, String parentXPath) {

		Element questionElement = outputXML.createElement("bind");
		
		// Add reference
		// String reference = parentXPath + "/" + q.getName();
		String reference = q.getPath();
		questionElement.setAttribute("nodeset", reference);
		
		// Add type
		String type = q.getType();
		if(type != null) {
			if(type.equals("audio") || type.equals("video") || type.equals("image")) {
				type = "binary";
			}
			// Following commented out check for select and select1 seems to be obsolete 27/3/2015 as odk sets type of select questions in bind
			if(/*!type.equals("select") && !type.equals("select1") &&*/ !type.equals("begin group") && !type.equals("begin repeat")) {
				questionElement.setAttribute("type", type);
			}
		}
		
		// Add read only
		if(q.isReadOnly()) {
			questionElement.setAttribute("readonly", "true()");
		}
		
		// Add mandatory
		if(q.isMandatory()) {
			questionElement.setAttribute("required", "true()");
		}
		
		// Add relevant
		String relevant = q.getRelevant();
		if(relevant != null && relevant.trim().length() > 0 ) {
			questionElement.setAttribute("relevant", relevant);
		}
		
		// Add constraint
		String constraint = q.getConstraint();
		if(constraint != null && constraint.trim().length() > 0 ) {
			questionElement.setAttribute("constraint", constraint);
		}
		
		// Add constraint message
		String constraintMsg = q.getConstraintMsg();
		if(constraintMsg != null && constraintMsg.trim().length() > 0 ) {
			questionElement.setAttribute("jr:constraintMsg", constraintMsg);
		}
		
		// Add calculate
		String calculate = null;
		if(q.getName().equals("instanceName")) {
			calculate = template.getSurvey().getInstanceName();
			if(calculate == null) {
				calculate = q.getCalculate();	// Allow for legacy forms that were loaded before the instance name was set in the survey table
			}
		} else  {
			calculate = q.getCalculate();
		}
		if(calculate != null && calculate.trim().length() > 0 ) {
			questionElement.setAttribute("calculate", calculate);
		}
		
		// Add preload
		String source = q.getSource();
		if(source != null && source.trim().length() > 0 ) {
			if(!source.equals("user")) {
				questionElement.setAttribute("jr:preload", source);
				String param = q.getSourceParam();
				if(param != null && param.trim().length() > 0 ) {
					questionElement.setAttribute("jr:preloadParams", param);
				}
			}
		}
		
		return questionElement;
    }
    
    /*
     * Populate the question element if this is part of the XForm bind
     * @param outputXML
     * @param f
     * @param q
     */
    public Element populateBodyQuestion(Document outputXML, Form f, Question q, String parentXPath) {

		Element questionElement = null;
		String type = q.getType();
		if(type.equals("string") || type.equals("int") || type.equals("dateTime") || 
				type.equals("decimal") || type.equals("barcode") || type.equals("date") ||
				type.equals("geopoint") || type.equals("time")) {
			questionElement = outputXML.createElement("input");
		} else if (type.equals("select")) {
			questionElement = outputXML.createElement("select");
		} else if (type.equals("select1")) {
			questionElement = outputXML.createElement("select1");
		} else if (type.equals("image")) {
			questionElement = outputXML.createElement("upload");	
			questionElement.setAttribute("mediatype", "image/*");  // Add the media type attribute			
		} else if (type.equals("audio")) {
			questionElement = outputXML.createElement("upload");
			questionElement.setAttribute("mediatype", "audio/*");  // Add the media type attribute	
		} else if (type.equals("video")) {
			questionElement = outputXML.createElement("upload");
			questionElement.setAttribute("mediatype", "video/*");  // Add the media type attribute	
		} else if (type.equals("begin group") || type.equals("begin repeat")
				|| type.equals("geolinestring") || type.equals("geopolygon")) {	
			questionElement = outputXML.createElement("group");
		} else {
			log.info("Warning Unknown type- populateBodyQuestion: " + type);
			questionElement = outputXML.createElement("input");
		}
		

		// Add the reference attribute
		if(questionElement != null) {
			// String reference = parentXPath + "/" + q.getName();
			questionElement.setAttribute("ref", q.getPath());
		}
		
		// Add the appearance
		if(questionElement != null) {
			String appearance = q.getAppearance();
			if(appearance != null) {
				questionElement.setAttribute("appearance", appearance);
			}
		}
			
		// Add the label
		if(questionElement !=null) {
			Element labelElement = outputXML.createElement("label");
			//String label = q.getQuestion();	
			String label = q.getQTextId();	
			if(label != null && label.trim().length() != 0) {		// Use question text by default

				// Add the reference wrapper
				label = q.getQTextId();
				if(label != null && label.trim().length() != 0) {
					label = "jr:itext('" + label + "')";
					labelElement.setAttribute("ref", label);
					questionElement.appendChild(labelElement);
				}
			}			
			
		}
		
		// Add the hint
		if(questionElement !=null) {
			Element hintElement = outputXML.createElement("hint");
			String hint = q.getInfoTextId();
			if(hint == null || hint.trim().length() == 0) {
				// Use the default hint text
				hint = q.getInfo();
				hintElement.setTextContent(hint);
			} else {
				// Add the reference wrapper
				hint = "jr:itext('" + hint + "')";
				hintElement.setAttribute("ref", hint);
			}			
				
			questionElement.appendChild(hintElement);
		}
		
		boolean cascade = true;
		String cascadeInstance = q.getCascadeInstance();
		if(cascadeInstance == null) {
			cascade = false;
		}
		
		// Add the itemset
		if(cascade) {
			Element isElement = outputXML.createElement("itemset");
			isElement.setAttribute("nodeset", q.getNodeset());			
		
			Element vElement = outputXML.createElement("value");
			vElement.setAttribute("ref", q.getNodesetValue());
			Element lElement = outputXML.createElement("label");
			lElement.setAttribute("ref", q.getNodesetLabel());
			
			isElement.appendChild(vElement);
			isElement.appendChild(lElement);
			questionElement.appendChild(isElement);
			
		}
		
		// If this is a choice question and it doesn't store it's items in itemset, then add the items
		if(!cascade && questionElement != null && q.getType().startsWith("select")) {
			populateOptions(outputXML, questionElement, q);
		}
		return questionElement;
    }
    
    /*
     * Add the options
     * @param outputXML
     * @param parent
     * @param q
     */
    public void populateOptions(Document outputXML, Element parent, Question q) {

    	Collection <Option> options = q.getChoices(); 
    	List <Option> optionList = new ArrayList <Option> (options);
  
		/*
		 * Sort the options
		 * The question sequence number is determined by the order of
		 * Questions in the forms question array list
		 */
		java.util.Collections.sort(optionList, new Comparator<Option>() {
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
    			
    	for(Option o : optionList) {
    		Element optionElement = outputXML.createElement("item");
    		
    		Element labelElement = outputXML.createElement("label");
			String label = o.getLabelId();
			if(label == null) {
				// Use the default label text
				label = o.getLabel();
				labelElement.setTextContent(label);
			} else {
				// Add the reference wrapper
				label = "jr:itext('" + label + "')";
				labelElement.setAttribute("ref", label);
			}
    		
    		Element valueElement = outputXML.createElement("value");
    		String value = o.getValue();
    		if(value == null) {
    			value = o.getLabel();
    		}
    		valueElement.setTextContent(value);
    		
    		optionElement.appendChild(labelElement);
    		optionElement.appendChild(valueElement);
    		parent.appendChild(optionElement); 		
    	}
    }
    
    /*
     * Add the options
     * @param outputXML
     * @param parent
     * @param q
     */
    public void populateCascadeOptions(Document outputXML, Element parent, 
    		CascadeInstance ci) {

    	List <Option> optionList = template.getCascadeOptionList(ci.name); 
  
		/*
		 * Sort the options
		 * The question sequence number is determined by the order of
		 * Questions in the forms question array list
		 */
		java.util.Collections.sort(optionList, new Comparator<Option>() {
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
    			
    	for(Option o : optionList) {
 
    		// Add item
    		Element itemElement = outputXML.createElement("item");
    		parent.appendChild(itemElement);
    		
    		// Add label element
    		Element labelElement = outputXML.createElement(ci.labelKey);	
			String label = o.getLabelId();
			if(label == null) {
				// Use the default label text
				label = o.getLabel();			
			} 
			labelElement.setTextContent(label);
    		
			// Add value element
    		Element valueElement = outputXML.createElement(ci.valueKey); 
    		String value = o.getValue();
    		if(value == null) {
    			value = o.getLabel();
    		}
    		valueElement.setTextContent(value);
    		
    		itemElement.appendChild(labelElement);
    		itemElement.appendChild(valueElement);		
    		
    		// Add other elements that are used for selecting relevant values
    		o.setCascadeKeyValues();	// Set the key value pairs from the filter string
    		HashMap<String, String> cvs = o.getCascadeKeyValues();
        	List<String> keyList = new ArrayList<String>(cvs.keySet());
        	for(String k : keyList) {
        		String v = cvs.get(k);
        		Element keyElement = outputXML.createElement(k);
        		keyElement.setTextContent(v);
        		itemElement.appendChild(keyElement);
        	}
    		itemElement.appendChild(labelElement);
    		itemElement.appendChild(valueElement);		
    	}
    }
    

    /*
     * Get the instance data for an XForm as a string
     */
    public String getInstance(int sId, String templateName, SurveyTemplate template, String key, 
    		String keyval, 
    		int priKey,
    		boolean simplifyMedia) throws ParserConfigurationException, ClassNotFoundException, SQLException, TransformerException, ApplicationException {
    	String instanceXML = null;
    	
    	Connection connection = null; 
    	
		DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
		DocumentBuilder b = dbf.newDocumentBuilder();    		
		Document outputXML = b.newDocument(); 
		
       	Writer outWriter = new StringWriter();
       	Result outStream = new StreamResult(outWriter);
       	
       	gFilenames = new ArrayList<String> ();
    	gInstanceId = null;

       	// Get template details
		String firstFormRef = template.getFirstFormRef();
		log.info("First form ref: " + firstFormRef);
		Form firstForm = template.getForm(firstFormRef);
		
		// Get database driver and connection to the results database
		
		try {
		    Class.forName("org.postgresql.Driver");	 
		} catch (ClassNotFoundException e) {
			log.log(Level.SEVERE, "Can't find PostgreSQL JDBC Driver", e);
		}
		
		String requester = "GetXForm - getInstance";
		connection = ResultsDataSource.getConnection(requester);
	 
		/*
		 * Replace the primary key with the primary key of the record that matches the passed in key and key value
		 */
		try {
			if(key != null && keyval != null) { 
				if(key.equals("prikey")) {
					priKey = Integer.parseInt(keyval);
					if(!priKeyValid(connection, firstForm, priKey)) {
						priKey = 0;
					}
				} else {
					priKey = getPrimaryKey(connection, firstForm, key, keyval);
				}
			} else {
				if(!priKeyValid(connection, firstForm, priKey)) {
					priKey = 0;
				}
			}
			
			log.info("Generate XML");
			// Generate the XML
			boolean hasData = false;
			if(priKey > 0) {
				hasData = true;
				populateForm(outputXML, firstForm, priKey, -1, connection, template, 
						null, sId, templateName, false, simplifyMedia);    
			} else if(key != null && keyval != null)  {
				// Create a blank form containing only the key values
				hasData = true;
				log.info("Outputting blank form");
				populateBlankForm(outputXML, firstForm, connection,  template, null, 
						sId, key, keyval, templateName, false);
			} 
			
	   		// Write the survey to a string and return it to the calling program
			if(hasData) {
	        	Transformer transformer = TransformerFactory.newInstance().newTransformer();
	        	transformer.setOutputProperty(OutputKeys.INDENT, "yes");
	        	DOMSource source = new DOMSource(outputXML);
	        	transformer.transform(source, outStream);
			
	        	instanceXML = outWriter.toString();
			} else {
				instanceXML = "";
			}
		} finally {
			try {
            	if (connection != null) {
            		connection.close();
            		connection = null;
            	}
            	log.info("   Release results connection: " + requester);
            } catch (SQLException e) {
            	log.log(Level.SEVERE, "Failed to close connection", e);
            }	
		}
		
    	return instanceXML;
    }
    
    /*
     * Getters
     */
    public String getInstanceId() {
    	return gInstanceId;
    }
    
    public ArrayList<String> getFilenames() {
    	return gFilenames;
    }
    
    public String getSurveyClass() {
    	return gSurveyClass;
    }
    
    
	/*
	 * Make sure the primary key is valid and can return data
	 */
	boolean priKeyValid(Connection connection, Form firstForm,  int priKey) throws ApplicationException, SQLException {
		
		String table = firstForm.getTableName().replace("'", "''");	// Escape apostrophes
		boolean isValid = false;
	
		// There is a double check below on whether or not the record has been modified / replaced
		// This was added in version 15.05, at some point it may be safe to remove the check for the "Replaced by " string and _bad
		String sql = "select count(*) from " + table + " where prikey = ? " +
				"and _modified = 'false' and (_bad = false or (_bad = true and _bad_reason not like 'Replaced by%'));";
		log.info("Checking primary key: " + sql + " : " + priKey);
		PreparedStatement pstmt = connection.prepareStatement(sql);
		pstmt.setInt(1, priKey);
		
		ResultSet rs = null;
		try {
			rs = pstmt.executeQuery();
			if(rs.next()) {
				if(rs.getInt(1) > 0) {
					isValid = true;
				}
			}

		} catch (Exception e) {
			String msg = e.getMessage();
			if(msg.contains("does not exist")) {
				// Presumably no data has been uploaded yet - therefore no key but not an error
			} else {			
				e.printStackTrace();
			}
		}
	

		return isValid;
	}
    
	/*
	 * Get the primary key from the passed in key values
	 *  The key must be in the top level form
	 */
	int getPrimaryKey(Connection connection, Form firstForm, String key, String keyval) throws ApplicationException, SQLException {
		int prikey = 0;
		String table = firstForm.getTableName().replace("'", "''");	// Escape apostrophes
		key = key.replace("'", "''");	// Escape apostrophes
		String type = null;
		String keyColumnName = null;
		
		// Get the key type
		List<Question> questions = firstForm.getQuestions();
		for(int i = 0; i < questions.size(); i++) {
			Question q = questions.get(i);
			if(q.getName().equals(key)) {
				type = q.getType();
				keyColumnName = q.getColumnName();
				break;
			}
		}
		if(type == null) {
			throw new ApplicationException("Key: " + key + " not found");
		}
		String sql = "select prikey from " + table + " where " + keyColumnName + " = ? " +
				"and _modified = 'false' and (_bad = false or (_bad = true and _bad_reason not like 'Replaced by%'));";
		
		PreparedStatement pstmt = connection.prepareStatement(sql);
		if(type.equals("string") || type.equals("barcode")) {
			pstmt.setString(1, keyval);
		} else if (type.equals("int")) {
			pstmt.setInt(1, Integer.parseInt(keyval));
		} else if (type.equals("decimal")) {
			pstmt.setFloat(1, Float.parseFloat(keyval));
		} else {
			throw new ApplicationException("Invalid question type: " + type + ", allowed values are text, barcode, integer, decimal");
		}
		
		ResultSet rs = null;
		boolean hasKey;
		try {
			log.info("Getting primary key: " + pstmt.toString());
			rs = pstmt.executeQuery();
			hasKey = rs.next();
		} catch (Exception e) {
			String msg = e.getMessage();
			if(msg.contains("does not exist")) {
				// Presumably no data has been uploaded yet - therefore no key but not an error
			} else {			
				e.printStackTrace();
			}
			hasKey = false;	
			//throw new ApplicationException("Record not found for key: " + key + " and value " + keyval);
		}
		if(hasKey) {
			prikey = rs.getInt(1);
			if(rs.next()) {
				throw new ApplicationException("Multiple records found for key: " + key + " and value " + keyval);
			}

		} else {
			log.info("Key " + key + " and value " + keyval + " not found");
			//throw new ApplicationException("Record not found for key: " + key + " and value " + keyval);
		}

		return prikey;
	}
	
	/*
     * Create a blank form 
     *   a) populated only by the key data or
     *   b) as a java rosa template
     * @param outputDoc
     */
    public void populateBlankForm(Document outputDoc, Form form, Connection connection, SurveyTemplate template,
       		Element parentElement,
    		int sId,
    		String key,
    		String keyval,
    		String survey_ident,
    		boolean isTemplate) throws SQLException {
	
 		List<Results> record = new ArrayList<Results> ();
 		
    	List<Question> questions = form.getQuestions();
		for(Question q : questions) {
			
			String qName = q.getName();
			String qType = q.getType(); 
			
			String value = "";
			if(qName.equals(key)) {
				value = keyval;
			} 
			
			log.info("Qtype: " + qType + " qName: " + qName);
			if(qType.equals("begin repeat") || qType.equals("geolinestring") || qType.equals("geopolygon")) {	
			
				Form subForm = template.getSubForm(form, q);
    			
    			if(subForm != null) {	
            		record.add(new Results(qName, subForm, null, false, false, false, null));
    			}
    			
    		} else if(qType.equals("begin group")) { 
    			
    			record.add(new Results(qName, null, null, true, false,false, null));
    			
    		} else if(qType.equals("end group")) { 
    			
    			record.add(new Results(qName, null, null, false, true,false, null));
    			
    		} else {

        		record.add(new Results(qName, null, value, false, false,false, null));
			}
		}
    	
		
        Element currentParent = outputDoc.createElement(form.getName());   // Create a form element

    	Results item = null;
	    Stack<Element> elementStack = new Stack<Element>();	// Store the elements for non repeat groups
    	for(int j = 0; j < record.size(); j++){

    		item= record.get(j);   			

    		if(item.subForm != null) {
				populateBlankForm(outputDoc, item.subForm, connection, template, 
						currentParent, sId, key, keyval, survey_ident, isTemplate);				
    		} else if (item.begin_group) { 

    			Element childElement = null;
    			childElement = outputDoc.createElement(item.name);
        		currentParent.appendChild(childElement);
        			
        		elementStack.push(currentParent);
				currentParent = childElement;
					
    		} else if (item.end_group) { 
    				
    			currentParent = elementStack.pop();
    				
    		} else {  // Question
    				
				// Create the question element
    			Element childElement = null;
				childElement = outputDoc.createElement(item.name);
				childElement.setTextContent(item.value);
    			currentParent.appendChild(childElement);
			}
    		
    	}
   		
		// Append this new form to its parent (if the parent is null append to output doc)
		if(parentElement != null) {
			if(isTemplate) {
				currentParent.setAttribute("jr:template", ""); 
			}
			parentElement.appendChild(currentParent);
		} else {
			currentParent.setAttribute("id", survey_ident);
			outputDoc.appendChild(currentParent);
		}
    	
    }
    
	/*
     * Add the data for this form
     * @param outputDoc
     */
    public void populateForm(Document outputDoc, Form form, int id, int parentId, 
    		Connection connection,
    		SurveyTemplate template,
    		Element parentElement,
    		int sId,
    		String survey_ident,
    		boolean isFirstSubForm,
    		boolean simplifyMedia) throws SQLException {
	
		List<List<Results>> results = getResults(form, id, parentId, connection, template, simplifyMedia);  // Add the child elements
    	boolean generatedTemplate = false;
		// For each record returned from the database add a form element
    	for(int i = 0; i < results.size(); i++) {
    		
        	Element currentParent = outputDoc.createElement(form.getName());   // Create a form element
    		List<Results> record = results.get(i);
  
    		Results priKey = record.get(0);	// Get the primary key
    		
    		/*
    		 * Add data for the remaining questions
    		 */
    		Results item = null;
	       	Stack<Element> elementStack = new Stack<Element>();	// Store the elements for non repeat groups
    		for(int j = 1; j < record.size(); j++){

    			item= record.get(j);   			

    			if(item.subForm != null) {
    				boolean needTemplate = (!generatedTemplate && (parentElement == null));
    				populateForm(outputDoc, item.subForm, -1, 
    						Integer.parseInt(priKey.value), connection, template, 
    						currentParent, sId, survey_ident, 
    						needTemplate,
    						simplifyMedia);
    			} else if (item.begin_group) { 
    				Element childElement = null;
    				childElement = outputDoc.createElement(item.name);
        			currentParent.appendChild(childElement);
        			
        			elementStack.push(currentParent);
					currentParent = childElement;
					
    			} else if (item.end_group) { 
    				
    				currentParent = elementStack.pop();
    				
    			} else {  // Question
    				
    				// remove _task_key functionality
    				// Set some default values for task management questions
    				//if(item.name != null && item.name.equals("_task_key")) {
    				//	item.value = priKey.value;
    				// } else 
    				if(item.name != null && item.name.toLowerCase().equals("instanceid")) {
    					gInstanceId = item.value;
    				}  else if(item.media && item.filename != null && !item.filename.equals("null")) {
    					gFilenames.add(item.filename);
    				}
    				
    				// Create the question element
        			Element childElement = null;
    				childElement = outputDoc.createElement(item.name);
    				childElement.setTextContent(item.value);
        			currentParent.appendChild(childElement);
    			}

    		}
    		// Append this new form to its parent (if the parent is null append to output doc)
    		if(parentElement != null) {
    			/*
    			 * The following code attempts to put a template section into instance data 
    			 * however it does not appear to be needed
    			 * This template section is probably only required in the form model
    			 *
    			 
    			if(isFirstSubForm && !generatedTemplate) {
    				 Add a template for enketo
    				populateBlankForm(outputDoc, form, connection,  template, 
    						parentElement, sId, null, null, survey_ident, true);
    				generatedTemplate = true;
    			}
    			*/
    			parentElement.appendChild(currentParent);
    		} else {
    			currentParent.setAttribute("id", survey_ident);
    			outputDoc.appendChild(currentParent);
    		}
    	}
    	    	
    }
    
    
    /*
     * Get the results
     * @param form
     * @param id
     * @param parentId
     */
    List<List<Results>> getResults(Form form, int id, int parentId, Connection connection,
    		SurveyTemplate template,
    		boolean simplifyMedia) throws SQLException{
 
    	List<List<Results>> output = new ArrayList<List<Results>> ();
    	
    	/*
    	 * Retrieve the results record from the database (excluding select questions)
    	 *  Select questions are retrieved using a separate query as there are multiple 
    	 *  columns per question
    	 */
    	
    	String sql = "select prikey";
    	List<Question> questions = form.getQuestions();
    	for(Question q : questions) {
    		String col = null;
    				
    		if(template.getSubForm(form, q) == null) {
    			// This question is not a place holder for a subform
    			if(q.getSource() != null) {		// Ignore questions with no source, these can only be dummy questions that indicate the position of a subform
		    		String qType = q.getType();
		    		if(qType.equals("geopoint")) {
		    			col = "ST_AsText(" + q.getColumnName() + ")";
		    		} else if(qType.equals("select")){
		    			continue;	// Select data columns are retrieved separately as there are multiple columns per question
		    		} else {
		    			col = q.getColumnName();
		    		}
		
		    		sql += "," + col;
    			}
    		}

    	}
    	sql += " from " + form.getTableName();
    	if(id != -1) {
    		sql += " where prikey=" + id + ";";
    	} else {
    		sql += " where parkey=" + parentId + ";";
    	}
    	log.info(sql);

    	PreparedStatement pstmt = connection.prepareStatement(sql);	 			
    	ResultSet resultSet = pstmt.executeQuery();
		
    	// For each record returned from the database add the data values to the instance
    	while(resultSet.next()) {
    		
    		List<Results> record = new ArrayList<Results> ();
    		
    		String priKey = resultSet.getString(1);
    		record.add(new Results("prikey", null, priKey, false, false, false, null));
    		
    		/*
    		 * Add data for the remaining questions
    		 */
    		int index = 2;
    		
    		for(Question q : questions) {
    			
    			String qName = q.getName();
				String qType = q.getType(); 
				String qPath = q.getPath();
				String qSource = q.getSource();
				
    			if(qType.equals("begin repeat") || qType.equals("geolinestring") || qType.equals("geopolygon")) {	
	    			Form subForm = template.getSubForm(form, q);
	    			
	    			if(subForm != null) {	
	            		record.add(new Results(qName, subForm, null, false, false, false, null));
	    			}
	    			
	    		} else if(qType.equals("begin group")) { 
	    			
	    			record.add(new Results(qName, null, null, true, false, false, null));
	    			
	    		} else if(qType.equals("end group")) { 
	    			
	    			record.add(new Results(qName, null, null, false, true, false, null));
	    			
	    		} else if(qType.equals("select")) {		// Get the data from all the option columns
	    				
					String sqlSelect = "select ";
					List<Option> options = new ArrayList<Option>(q.getValidChoices());
					UtilityMethods.sortOptions(options);	// Order within an XForm is not actually required, this is just for consistency of reading

					boolean hasColumns = false;
					for(Option option : options) {
						if(hasColumns) {
							sqlSelect += ",";
						}
						sqlSelect += q.getColumnName() + "__" + option.getColumnName();
						hasColumns = true;
					}
					sqlSelect += " from " + form.getTableName() + " where prikey=" + priKey + ";";
			    	log.info(sqlSelect);
			    	
			    	pstmt = connection.prepareStatement(sqlSelect);	 			
			    	ResultSet resultSetOptions = pstmt.executeQuery();
			    	resultSetOptions.next();		// There will only be one record
		    		
			    	String optValue = "";
			    	hasColumns = false;
			    	for(Option option : options) {
			    		String opt = q.getColumnName() + "__" + option.getColumnName();
			    		boolean optSet = resultSetOptions.getBoolean(opt);
			    		log.fine("Option " + opt + ":" + resultSetOptions.getString(opt));
			    		if(optSet) {
				    		if(hasColumns) {
				    			optValue += " ";
				    		}
				    		optValue += option.getValue(); 
				    		hasColumns = true;
			    		}
					}
			    	
	        		record.add(new Results(UtilityMethods.getLastFromPath(qPath), null, optValue, false, false, false, null));
				
	    		} else if(qType.equals("image") || qType.equals("audio") || qType.equals("video") ) {		// Get the file name
	    			
	    			String value = resultSet.getString(index);
	    			String filename = null;
	    			if(value != null) {
	    				int idx = value.lastIndexOf('/');
	    				if(idx > -1) {
	    					filename = value.substring(idx + 1);
	    				}
	    				if(filename != null && !filename.equals("null")) {
	    					gFilenames.add(filename);
	    				}
	    			}
	    			if(simplifyMedia) {
	    				value = filename;
	    			}
	    			record.add(new Results(UtilityMethods.getLastFromPath(qPath), null, value, false, false, false, filename));
	    			index++;
	    			
	    		} else if(qSource != null) {
  
    				String value = resultSet.getString(index);
 				
    				if(value != null && qType.equals("geopoint")) {
    					int idx1 = value.indexOf('(');
    					int idx2 = value.indexOf(')');
    					if(idx1 > 0 && (idx2 > idx1)) {
	    					value = value.substring(idx1 + 1, idx2 );
	    					// These values are in the order longitude latitude.  This needs to be reversed for the XForm
	    					String [] coords = value.split(" ");
	    					if(coords.length > 1) {
	    						value = coords[1] + " " + coords[0] + " 0 0";
	    					}
    					} else {
    						log.severe("Invalid value for geopoint");
    						value = null;
    					}
    				}
    				
    				// Ignore data not provided by user
    				if(!qSource.equals("user")) {	
    					value="";
    				}

            		record.add(new Results(UtilityMethods.getLastFromPath(qPath), null, value, false, false, false, null));

	    			index++;
    			}
    			
    		}
    		output.add(record);
    	}   	
    	
		return output;
    }
    
}

