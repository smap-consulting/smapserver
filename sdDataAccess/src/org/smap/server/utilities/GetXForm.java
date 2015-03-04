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

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Result;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.smap.model.CascadeInstance;
import org.smap.model.SurveyTemplate;
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

	public GetXForm() {

	}

    /*
     * Get the XForm as a string
     */
    public String get(SurveyTemplate template) {
     	   
       	String response = null;
       	this.template = template;
  	    	
    	try {
    		System.out.println("Getting survey as XML-------------------------------");
    		// Create a new XML Document
    		DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
    		DocumentBuilder b = dbf.newDocumentBuilder();    		
    		Document outputXML = b.newDocument(); 
    		
           	Writer outWriter = new StringWriter();
           	Result outStream = new StreamResult(outWriter);
           	
           	// Get the first form
           	String firstFormRef = template.getFirstFormRef();
           	if(firstFormRef == null) {
           		System.out.println("Error: First Form Reference is null");
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
							System.out.println("t3: "+ valueElement.getTextContent());
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
	    			System.out.println("----------Not enabled:" + q.getName());
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
					System.out.println("Error in enableTranslationRef(): No types for:" + ref);
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
    	
    	System.out.println("TODO: Add value name and label name for instance:" + instance);

    	
    }

 
    
    public void populateBody(Document outputDoc, Element parent) {
    	Element bodyElement = outputDoc.createElement("h:body");
    	/*
    	 * Add class if it is set
    	 */
    	String surveyClass = template.getSurveyClass();
    	if(surveyClass != null) {
    		bodyElement.setAttribute("class", surveyClass);
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
    public void createRepeatingGroup(Document outputXML, Element parent, Form subF, int location, String parentXPath, Question parentQuestion) {

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
			if(!type.equals("select") && !type.equals("select1") && !type.equals("begin group") && !type.equals("repeat group")) {
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
		String calculate = q.getCalculate();
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
			System.out.println("Warning Unknown type- populateBodyQuestion: " + type);
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
    
    
}

