package org.smap.server.utilities;

import java.io.InputStream;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.smap.model.SurveyTemplate;
import org.smap.sdal.Utilities.GeneralUtilityMethods;
import org.smap.server.entities.Form;
import org.smap.server.entities.Option;
import org.smap.server.entities.Question;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class PutXForm {

	SurveyTemplate template = null;
	boolean instanceFound = false;
	
	private class Content {
		String direct = null;
		String ref = null;
		public Content() {
			direct = null;
			ref = null;
		}
	}
	
    /*
     * Load the XForm into an object based model
     */
    public SurveyTemplate put(InputStream is, String user, String basePath) throws Exception {
    	template = null;
     	    	

		DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
		DocumentBuilder b = dbf.newDocumentBuilder();
		
		Document surveyDocument = b.parse(is);
		Element rootElement = surveyDocument.getDocumentElement();  
		
		template = new SurveyTemplate();
		template.createSurvey();
		template.setUser(user);
		template.setBasePath(basePath);
		processElement(rootElement);   	   		
    		
    	return template;
 
    }
    
    /*
     * Method to process the XML until an interesting element is found
     * 
     * @param n the element to be parsed
     */
    private void processElement(Node n) {
    	NodeList eList = n.getChildNodes();
		if (eList != null) {
			for (int i = 0; i < eList.getLength(); i++) {
				String eName = eList.item(i).getNodeName();
				if(eName.equals("instance")) {
					if(!instanceFound) {
						template.resetNextQuestionSeq();  // Initially calculate the sequence in the order questions are placed in the model, this will pick up calculate questions that are not in the body
						processInstance(eList.item(i));  // Process first instance and get default values
						instanceFound = true;
					} else {
						processCascadingSelect(eList.item(i));
					}
				} else if(eName.equals("itext")) {
					processIText(eList.item(i));
				} else if(eName.equals("bind")) {
					processBind(eList.item(i));  // Add data from bind element
				} else if(eName.equals("h:body")) {
					processBody(eList.item(i), null, null);
				} else {
					processElement(eList.item(i));
				}
			}
		}
    }

    /*
     * Process a cascading select instance
     */
    private void processCascadingSelect(Node n) {
    	
    	String instanceId = null;
    	
    	// Get the instance identifier
    	NamedNodeMap nmL = n.getAttributes();
    	if(nmL != null) {
    		Node id = nmL.getNamedItem("id");
    		if(id != null) {
    			 instanceId = id.getNodeValue();
    		}
    	}
    	
    	if(instanceId != null) {
    		Node topInstance = UtilityMethods.getFirstElement(n);			// According to XForms spec "Instance data always has a single root element"
    		processCascadingItems(topInstance, instanceId);
    	}
    }
    
    /*
     * Method to process all the items that make up a cascading instance
     */
    private void processCascadingItems(Node n, String cascadeInstanceId) {
       	

    	// Process the cascading instance's children
    	NodeList eList = n.getChildNodes();
		if (eList != null) {
			for (int i = 0, count = 0; i < eList.getLength(); i++) {
				
				String itemname = eList.item(i).getNodeName();
				if(!itemname.equals("#text")) {
					String ref = cascadeInstanceId + "::" + i;
					template.createCascadeOption(ref, cascadeInstanceId);
					Option o = template.getCascadeOption(ref);
					o.setSeq(count++);
					
					NodeList childList = eList.item(i).getChildNodes();
					if(childList != null) {
						for (int j = 0; j < childList.getLength(); j++) {
							String name = childList.item(j).getNodeName();
							String value = childList.item(j).getTextContent();
							if(name != null && !name.equals("#text") && value != null) {
								o.addCascadeKeyValue(name, value);
							}
							
						}
					}
				}
				
			}
		}
    }
    
    /*
     * Method to process the XMLForms "instance" element
     * @param n the DOM node of the instance element
     */
    private void processInstance(Node n) {
    	Node topForm = UtilityMethods.getFirstElement(n);			// According to XForms spec "Instance data always has a single root element"
    	template.setFirstFormName(topForm.getNodeName());
    	  	
    	processInstanceForm(topForm, "");
    }
    
    /*
     * Load the multiple language text strings
     * 
     * @param n element to parse
     */
    private void processIText(Node n) {
    	NodeList eList = n.getChildNodes();
		if (eList != null) {
			for (int i = 0; i < eList.getLength(); i++) {
				String eName = eList.item(i).getNodeName();
				if(eName.equals("translation")) {
					processLanguage(eList.item(i));
				} 
			}
		}
    }
    
    private void processLanguage(Node n) {
    	// Get the language String
    	String language = "default";
    	
    	NamedNodeMap nmL = n.getAttributes();
    	if(nmL != null) {
    		Node nl = nmL.getNamedItem("lang");
    		Node ndl = nmL.getNamedItem("default");
    		if(nl != null) {
    			language = nl.getNodeValue();
    		}
    		if(ndl != null) {
    			String def_lang_set = ndl.getNodeValue();
    			if(def_lang_set.startsWith("true")) {
    				template.setDefaultLanguage(language);
    			}
    		}
    	}
		
    	// Get all the translations for this language
    	NodeList eList = n.getChildNodes();
		if (language != null && eList != null) {
			for (int i = 0; i < eList.getLength(); i++) {
				String eName = eList.item(i).getNodeName();
				if(eName.equals("text")) {
					NamedNodeMap nm = eList.item(i).getAttributes();
					if(nm != null) {
						Node ni = nm.getNamedItem("id");
						if(ni != null) {
							String ref = ni.getNodeValue();
							NodeList childList = eList.item(i).getChildNodes();
							if(childList != null) {
								for (int j = 0; j < childList.getLength(); j++) {
									String cName = childList.item(j).getNodeName();
									if(cName.equals("value")) {
										String value = null;
										String form = "none";	// Default to "none" for old style templates
																// Also "hints" do not use types
										
										NamedNodeMap valueAtts = childList.item(j).getAttributes();
										Node formNode = valueAtts.getNamedItem("form");
										if(formNode != null) {
											form = formNode.getNodeValue();
										}
										
										Node valueNode = childList.item(j);
										value = getValue(valueNode);
										
										template.addIText(language, ref, form, value);
									}
								}
							}
						}
					}
				} 
			}
		}
    }
    
    // Get the value from 
    private String getValue(Node n) {
		// Wrap the value in some XML as it can include XML fragments
		//String value = "<t>";
		String value = "";
		
		// Extract the value as a string, convert embedded nodes into a string	

		NodeList vList = n.getChildNodes();
		if(vList != null) {
			for (int k = 0; k < vList.getLength(); k++) {
				
				String subName = vList.item(k).getNodeName();
				String subValue = vList.item(k).getTextContent();
				
				if(subName.equals("#text")) {
					// XML fragments are stored in the database so store values escaped
					value += GeneralUtilityMethods.esc(subValue);
				} else {
					value += "<" + subName;
					
					NamedNodeMap subAtts = vList.item(k).getAttributes();
					if(subAtts != null) {
						for (int l = 0; l < subAtts.getLength(); l++) {
							String subAttName = subAtts.item(l).getNodeName();
							String subAttValue = subAtts.item(l).getNodeValue();															
							value += " " + subAttName + "=\"" + subAttValue + "\"";
						}
					}	
					
					value += "/>";
				}
									
				
			}
		}
		//value += "</t>";
		
		return value;
    }

    
    /*
     * Process the body of the XForm
     * This is called recursively for elements within the body
     *  
     * @param n the element to be parsed
     * @param formRef the reference of the current form
     */
    private void processBody(Node n, String formRef, Question lastGroupQuestion) {
    	
    	/*
    	 * Get the class, used for:
    	 *  webform paging
    	 */
     	NamedNodeMap nm = n.getAttributes();
    	if(nm != null) {
			Node ni = nm.getNamedItem("class");
			if(ni != null) {
				template.setSurveyClass(ni.getNodeValue());
			} 
    	} 
    	
    	NodeList eList = n.getChildNodes();
		if (eList != null) {
			for (int i = 0; i < eList.getLength(); i++) {
				if(eList.item(i).getNodeType() == Node.ELEMENT_NODE) {
					String eName = eList.item(i).getNodeName();
					if(eName.equals("group")) {
						
						/*
						 * Normal groups (begin group) are stored as questions
						 * If the group turns out to be a repeat group then a new form will be created
						 */
						String newFormRef = processBodyQuestion(eList.item(i), formRef);
						
						if(formRef == null) {
							formRef = newFormRef;
						}
								
					} else if(eName.equals("repeat")) {
						
						/*
						 * So this is a repeating group
						 * The parent question for this group has already been created and stored as lastGroupQuestion
						 */
						String newFormRef = repeatForm(eList.item(i), formRef, lastGroupQuestion);
						String newFormName = UtilityMethods.getLastFromPath(newFormRef);
					    
		    	    	// Set the source and question type if this is a "complex question" such as geopolygon
		    	    	if(newFormName.startsWith("geopolygon")) {
		    	    		lastGroupQuestion.setSource("user");
		    	    		lastGroupQuestion.setType("geopolygon");
		    	    		lastGroupQuestion.setName("the_geom");	// Geometry types are always called "the_geom"
		    	    	} else if(newFormName.startsWith("geolinestring")) {
		    	    		lastGroupQuestion.setSource("user");
		    	    		lastGroupQuestion.setType("geolinestring");
		    	    		lastGroupQuestion.setName("the_geom");	// Geometry types are always called "the_geom"
		    	    	}
		    	    	
		    	    	// set last group question as the parent of the repeating form
		    	    	Form f = template.getForm(newFormRef);						
						processBody(eList.item(i), newFormRef, lastGroupQuestion);
						
					} else if(eName.equals("input") || eName.equals("select") || eName.equals("select1")
							|| eName.equals("trigger") || eName.equals("upload")) {
						
						String newFormRef = processBodyQuestion(eList.item(i), formRef);
						if(formRef == null) {
							formRef = newFormRef;
						}
					
					} else {
						processBody(eList.item(i), formRef, lastGroupQuestion);
					}
				}
			}
		}
    }
    

    /*
     * Method to create:
     *  A dummy question to represent the start of a group
     */
    private void createGroupQuestion(String groupBeginRef, String formRef,  int questionSeq) {
		String questionRef = groupBeginRef;
		String questionName = UtilityMethods.getLastFromPath(questionRef);
		template.createQuestion(questionRef, questionName);
    	Question q = template.getQuestion(questionRef);
    	
    	// Set the sequence number of the question
    	q.setType("begin group");	// Default to begin group for groups not in the body
    	q.setSeq(questionSeq);
 
    }
    
    /*
     * Method to create:
     *  A dummy question to represent the end of a non repeating group
     */
    private void createGroupEndQuestion(String groupBeginRef, String formRef,  int questionSeq) {
		String questionRef = groupBeginRef + "_groupEnd";
		String questionName = UtilityMethods.getLastFromPath(questionRef);
		template.createQuestion(questionRef, questionName);
    	Question q = template.getQuestion(questionRef);
    	
    	q.setBeginRef(groupBeginRef);
    	q.setFormRef(formRef);		// Set the reference of the form containing this question 	   	
    	q.setSeq(questionSeq);		// Set the sequence number of the question
    	
    	// Set the type
    	q.setType("end group");
    }
    
    /*
     * Method to get a label
     * This could be a reference to an itext element (multi language)
     *  or be included directly in the element
     *  
     *  @param n the element
     *  @return the content string
     */
    private Content getContent(Node n, String ref, String name) {
    	Content c = new Content();
    	
    	// First try to get a referenced value
    	NamedNodeMap nm = n.getAttributes();
    	if(nm != null) {
			Node ni = nm.getNamedItem("ref");
			if(ni != null) {
				c.ref = ni.getNodeValue();
				int idx1 = c.ref.indexOf('\'') + 1;
				int idx2 = c.ref.lastIndexOf('\'', c.ref.length() - 1);
				c.ref = c.ref.substring(idx1, idx2);
			} 
    	} 
    	 	
    	// Get the element's embedded text content
    	
    	if(name.equals("value")) {
    		c.direct = n.getTextContent(); 
    	} else {
    		c.direct = getValue(n);	// Get value as xml fragment
    	}
    	
    	// If this is a direct value we should create a translation so that there is a translation for every string
    	if(c.ref == null && name != null && !name.equals("value") && ref != null && c.direct != null && c.direct.trim().length() > 0) {
    		c.ref = ref + "_direct:" + name;	// Make sure dummy translations do not have the same id as a real translation by adding "_direct"
    		template.addDummyTranslation(c.ref, c.direct);
    	}

    	return c;
    }
    
    /*
     * Create a new form when a repeating group element is found
     * 
     * @param n the element to parse
     * @return reference to new form
     */
    private String repeatForm(Node n, String parentFormRef, Question parentQuestion) {
    	String formName = null;
    	String ref = null;
    	
    	NamedNodeMap nm = n.getAttributes();
    	if(nm != null) {
			Node ni = nm.getNamedItem("nodeset");
			Node nr = nm.getNamedItem("jr:count");
			if(ni != null) {
				ref = ni.getNodeValue();
				formName = UtilityMethods.getLastFromPath(ref);
				
				if(parentFormRef == null) {
					parentFormRef = createTopLevelForm(ref);	// Use the top level form, create it if you have to					
				}
				
		    	template.createForm(ref, formName);
		    	Form newRepeatForm = template.getForm(ref);
		    	newRepeatForm.setParentFormRef(parentFormRef);	// Set the parent form
		    	newRepeatForm.setParentQuestionRef(parentQuestion.getPath());	// Set the parent question
		    	if(nr != null) {
		    		String repeats = nr.getNodeValue();
		    		template.getForm(ref).setRepeatsRef(repeats);
		    		
		    		// Set the dummy calculate that this repeat references as being a repeat calculate
		    		Question qCalc = template.getQuestion(repeats.trim());
		    		if(qCalc != null) {
		    			qCalc.setRepeatCount(true);
		    		}
		    	}
			}
    	}
    	
    	return ref;
    }

    
    /*
     * Method to process an input, select or select1 element within the XMLForms "body" element
     * 
     * @param n the element to be parsed
     * @param formRef reference to the form that contains this question
     * @param the sequence of this question
     */
    private String processBodyQuestion(Node n, String formRef) { 
    	
    	String questionRef = null;
    
    	NamedNodeMap nm = n.getAttributes(); 
		Node refNode = nm.getNamedItem("ref");
		if(refNode == null && (n.getNodeName().equals("group"))) {
			// This may be a repeating group that does not have a reference attribute
			// get the question reference from the repeat elements nodeset
			questionRef = getRepeatNodeset(n);
			if(questionRef == null) {
				// may be a normal group without a reference
				// get the question reference from the groups label
				questionRef = getGroupRefFromLabel(n);
			}
		} else {
			questionRef = nm.getNamedItem("ref").getNodeValue();
		}
		
		// Still no reference! report an error
		if(questionRef == null) {
			if(refNode == null && (n.getNodeName().equals("group"))) {
				/*
				 * This next bit of code handles the case where a hand crafted XML form
				 *  did not include any reference for the group however we still want to get
				 *  the groups questions.
				 *  TODO: replace this code to create a dummy group that can store appearance
				 *   information but does not appear in path
				 */
				processBody(n, formRef, null);	// Continue with all the question in this group without creating the group
			} else {
				System.out.println("Error: Question reference is null: " + n.getNodeName() + " : " + formRef);
			}	
			return formRef;		// Don't create the body question
		}

    	if(formRef == null) {
    		formRef = createTopLevelForm(questionRef);	// Use the top level form, create it if you have to
    	}
    	
   	 	// make this the full path if is a relative XPath
    	if(!questionRef.startsWith("/")) {
    		questionRef = formRef + "/" + questionRef;
    	}
    	
    	// Create the question if it is not already created   	
    	if(template.getQuestion(questionRef) == null) {
    		String questionName = UtilityMethods.getLastFromPath(questionRef);
    		template.createQuestion(questionRef, questionName);
    	}
    	
    	Question q = template.getQuestion(questionRef);   	
    	q.setVisible(true);		// As this question is in the body of the form then set it to visible
    	  		
    	q.setFormRef(formRef);    	// Set the reference of the form containing this question 	
    	q.setPath(questionRef);    	// Set the XForm Path   	   	
    	//q.setSeq(template.getNextQuestionSeq());    	// Set the sequence number of the question
    	
    	q.setDefaultAnswer(template.getDefault(questionRef));    	// Set the default answer if it exists
    	

    	Node nodeMediaType = nm.getNamedItem("mediatype");    	// set the media type
    	if(nodeMediaType != null) {
			String mediaType = nodeMediaType.getNodeValue();
			if(mediaType != null) {
				if(mediaType.toLowerCase().startsWith("audio")) {
					q.setType("audio");
				} else if(mediaType.toLowerCase().startsWith("image")) {
					q.setType("image");
				} if(mediaType.toLowerCase().startsWith("video")) {
					q.setType("video");
				}
			}
    	}
    	
    	String eName = n.getNodeName();    	// Set the select, select1 type
    	if(eName.equals("select")) {
    		q.setType("select");
    	} else if(eName.equals("select1")) {
    		q.setType("select1");		
    	}
    	
    	if(q.getSource() == null) {    	// Set the source (where the source is null then no results will be stored for this question)
    		if(!eName.equals("group")) {	// groups do not record any data therefore no source
    			q.setSource("user");		
    		}
    	}
    	
    	// Set the appearance
		Node appNode = nm.getNamedItem("appearance");	
		String appearance = null;
		if(appNode != null) {
			appearance = appNode.getNodeValue();
			q.setAppearance(appearance);
			// Survey level manifests can be set in the appearance attribute
			template.addManifestFromAppearance(appearance);	
		}
		
    	// Set the autoplay
		Node autoplayNode = nm.getNamedItem("autoplay");	
		String autoplay = null;
		if(autoplayNode != null) {
			autoplay = autoplayNode.getNodeValue();
			q.setAutoPlay(autoplay);
		}
    	
    	if(eName.equals("group")) {
    		setGroupLabel(questionRef, n);	// Get the label for this group

    		if(isRepeat(n))	{ 	// repeating group
    			q.setType("begin repeat");
    			processBody(n, formRef, q);
    			 // Delete the question that marks the end of this repeating group, we don't need it
    			template.removeQuestion(q.getPath() + "_groupEnd");
    		} else {									// Non repeating group
    			q.setType("begin group");
    			processBody(n, formRef, q);	// Continue with all the question in this group
    		}
    	} else {
    	
			// Process the question element's children
	    	NodeList eList = n.getChildNodes();
	
			template.setNextOptionSeq(1);	// Reset the option sequence number
			if (eList != null) {
				for(int i = 0; i < eList.getLength(); i++) {
					processBodyQuestionChild(questionRef, eList.item(i), true, false, formRef, null);
				}
			}
    	}
		
		return formRef;
    }    
    
    /*
     * Method to create the top level form from the path of a child
     * 
     * @param path of child element
     * @returns reference to top level form
     */
    String createTopLevelForm(String path) {
    	String formRef = null;
    	
    	String topFormRef = template.getFirstFormRef();
		if(topFormRef == null) {
			String parentFormName = UtilityMethods.getFirstFromPath(path);
			if(parentFormName == null) {
				// Use the top level entity name from the instance
				parentFormName = template.getFirstFormName();
			}
			formRef = "/" + parentFormName;
			template.createForm(formRef, parentFormName);
			template.setFirstFormRef(formRef);
			template.setFirstFormName(parentFormName);
		} else {
			formRef = topFormRef;
		}
		
		return formRef;
    }
    
    /*
     * Method to process the children element of a question
     * These include aspects of the question itself as well as options for a select question
     * 
     * @param questionRef the reference to the question
     * @param n the XML element to parse
     * @question set to true if the aspects of a question are being parsed else false
     * @param formRef reference to the form that contains this question
     */
    private void processBodyQuestionChild(String questionRef, Node n, boolean question, 
    		boolean option, String formRef, String cascadeInstanceId) { 	 
    	
    	// Get the question to be updated
    	Question q = template.getQuestion(questionRef);    	
    	    	
    	if(q != null && question && n.getNodeName().equals("label")) {
    		Content c = getContent(n, questionRef, "label");
    		if(c.ref != null) {
    			q.setQTextId(c.ref);
    		} else {
        		q.setQuestion(c.direct);
    		}

    	} else if(q!= null && question && n.getNodeName().equals("hint")) {
    		Content c = getContent(n, questionRef, "hint");
    		if(c.ref != null) {
        		q.setInfoTextId(c.ref);
    		} else {
    			q.setInfo(c.direct);
    		}

    		
    	} else if(q!= null && question && n.getNodeName().equals("itemset")) {
			question = false; // from now all labels will be for the itemset
			option = false;
			
			NamedNodeMap nm = n.getAttributes(); 
			Node refNode = nm.getNamedItem("nodeset");
			if(refNode != null) {
				String nodeset = refNode.getNodeValue();
				int idx1 = nodeset.indexOf('\'') + 1;
				int idx2 = nodeset.indexOf('\'', idx1 + 1);
				cascadeInstanceId = nodeset.substring(idx1, idx2);
				template.createCascadeOptions(cascadeInstanceId, questionRef);
				q.setCascadeInstance(cascadeInstanceId);
				q.setNodeset(nodeset);
			}
    		
    	} else if(question && n.getNodeName().equals("item")) {
			question = false; // from now all labels will be for options
			option = true;
			
			// Create a new option
    		int seq = template.getNextOptionSeq();
			String optionRef = questionRef + "/" + seq;
			template.createOption(optionRef, questionRef);    		
    		template.getOption(optionRef).setSeq(seq);
    		template.setNextOptionSeq(seq + 1);
			
    	} else if(!question && option && n.getNodeName().equals("label")) {
    		
    		// Get the last option created
    		int seq = template.getNextOptionSeq() - 1;
    		String optionRef = questionRef + "/" + seq;
    		
    		Option o = template.getOption(optionRef);
    		if(o != null) {
    			Content c = getContent(n, optionRef, "label");
    			if(c.ref != null) {
        			o.setLabelId(c.ref);
    			} else {
        			o.setLabel(c.direct);
    			}

    		}
    		
    	} else if(!question && option && n.getNodeName().equals("value")) {
    		
    		// Get the last option created
    		int seq = template.getNextOptionSeq() - 1;
    		String optionRef = questionRef + "/" + seq;
    		
    		Option o = template.getOption(optionRef);
    		if(o != null) {
    			Content c = getContent(n, optionRef, "value");
    			o.setValue(c.direct);
    		}
    		
    	} else if(!question && !option && n.getNodeName().equals("value")) {
			
			NamedNodeMap nm = n.getAttributes(); 
			Node refNode = nm.getNamedItem("ref");
			if(refNode != null) {
				q.setNodesetValue(refNode.getNodeValue());
				template.setCascadeValue(questionRef, refNode.getNodeValue());
			}
    		
    	} else if(!question && !option && n.getNodeName().equals("label")) {
			
    		NamedNodeMap nm = n.getAttributes(); 
			Node refNode = nm.getNamedItem("ref");
			if(refNode != null) {
				q.setNodesetLabel(refNode.getNodeValue());
				template.setCascadeLabel(questionRef, refNode.getNodeValue());
			}	
    	}
    	
    	// Process the children's children
    	NodeList eList = n.getChildNodes();
		if (eList != null) {
			for(int i = 0; i < eList.getLength(); i++) {
				processBodyQuestionChild(questionRef, eList.item(i), question, option, 
						formRef, cascadeInstanceId);
			}
		}
    	
    }
    
    /*
     * Method to process the children element of a non repeating group that relate specifically to the group
     * That is the label!
     */
    private void setGroupLabel(String questionRef, Node n) { 	 
    	
    	// Get the question to be updated
    	Question q = template.getQuestion(questionRef);   
    	
		NodeList eList = n.getChildNodes();
    	    
    	if (eList != null) {
			for(int i = 0; i < eList.getLength(); i++) {
		    	if(q != null && eList.item(i).getNodeName().equals("label")) {
		    		Content c = getContent(eList.item(i), questionRef, "label");
		    		if(c.ref != null) {
			    		q.setQTextId(c.ref);
		    		} else {
			    		q.setQuestion(c.direct);
		    		}
		    		break;	// stop at first label
		    	} 
			}
    	}  	
    }
    
    /*
     * Return the reference if this is a repeating group
     * Return null if "repeat" is not found 
     */
    private String getRepeatNodeset(Node n) { 	 
    	
		NodeList eList = n.getChildNodes();
	    
    	if (eList != null) {
			for(int i = 0; i < eList.getLength(); i++) {
		    	if(eList.item(i).getNodeName().equals("repeat")) {
		    		NamedNodeMap nm = eList.item(i).getAttributes(); 
		    		return nm.getNamedItem("nodeset").getNodeValue();
		    	} 
			}
    	}  
    	return null;
    	
    }
    
    /*
     * Return the reference assuming this is a non repeating group
     */
    private String getGroupRefFromLabel(Node n) { 	 
    	
		NodeList eList = n.getChildNodes();
	    
    	if (eList != null) {
    		// The first label will be the groups label
			for(int i = 0; i < eList.getLength(); i++) {
		    	if(eList.item(i).getNodeName().equals("label")) {
		    		NamedNodeMap nm = eList.item(i).getAttributes(); 
		    		Node nRef = nm.getNamedItem("ref");
		    		if(nRef == null) {
		    			return null;
		    		} else {
			    		String labelKey = nRef.getNodeValue();
			    		if(labelKey.startsWith("jr:itext")); {
			    			// Text id reference, set the label_id
			    			int idx1 = labelKey.indexOf('\'');
			    			int idx2 = labelKey.indexOf(':', idx1 + 1);
			    			labelKey = labelKey.substring(idx1 + 1, idx2);
			    		}
			    		return labelKey;
		    		}
		    	} 
			}
    	}  
    	return null;
    	
    }
    
    /*
     * Return true if this is a repeating group
     * Return false if "repeat" is not found or a new group is found
     */
    private boolean isRepeat(Node n) { 	 
    	
    	// Get the group to be checked
    	//Question q = template.getQuestion(questionRef);   
    	
		NodeList eList = n.getChildNodes();
    	    
    	if (eList != null) {
			for(int i = 0; i < eList.getLength(); i++) {
		    	if(/*q != null && */eList.item(i).getNodeName().equals("repeat")) {
		    		return true;
		    	} else if(/*q != null && */eList.item(i).getNodeName().equals("group")) {
		    		return false;
		    	} 
			}
    	}
    	
    	return false;
    	
    }
    
    /*
     * Method to process a form element within the XMLForms "instance" element
     * The sequence of questions is determined from the question order in the instance element
     * This ensures that non visible questions that appear in the body are included in the correct
     * location in the output file.  However currently this means that if the XML is manually edited to 
     * change the sequence of questions in the body then this will not be picked up by the template
     * loader
     * 
     * @param n form element to parse
     * @param parentFormRef reference to the parent form
     */
    private void processInstanceForm(Node n, String parentFormRef) {
      	String formName = n.getNodeName();
    	String formRef = parentFormRef + "/" + formName;

    	// Process the form's children
    	NodeList eList = n.getChildNodes();
		if (eList != null) {
			for (int i = 0; i < eList.getLength(); i++) {
				String questionName = eList.item(i).getNodeName();
				String questionRef = formRef + "/" + questionName;
				if(UtilityMethods.hasChildElement(eList.item(i))) {
					createGroupQuestion(questionRef, formRef, template.getNextQuestionSeq());
					processInstanceForm(eList.item(i), formRef);
					createGroupEndQuestion(questionRef, formRef, template.getNextQuestionSeq());
				} else {
					// Create the question
					
					if(!questionName.equals("#text") /*&& !questionName.equals("instanceID") WFP */) {
			    		template.createQuestion(questionRef, questionName);
				    	Question q = template.getQuestion(questionRef);
				    	q.setPath(questionRef);
				    	q.setSeq(template.getNextQuestionSeq());    	// Set the sequence number of the question 
					}
			    	// Set the default value
					String defaultAnswer = eList.item(i).getTextContent();
					if(defaultAnswer != null && defaultAnswer.trim().length() > 0) {
						template.addDefault(questionRef, defaultAnswer);
					}
				}
			}
		}
    }
      
    /*
     * Method to process the XMLForms "bind" element
     * These instances add properties to already created questions
     * 
     * @param n the DOM node of the bind element
     */
    private void processBind(Node n) {
    	
    	NamedNodeMap nm = n.getAttributes(); 	
    	String questionRef = nm.getNamedItem("nodeset").getNodeValue();		// Get the question reference for this bind element
    	String questionName = UtilityMethods.getLastFromPath(questionRef);
    	
    	//if(!questionName.equals("instanceID")) { WFP
	    	// Create the question if it is not already created   	
	    	if(template.getQuestion(questionRef) == null) {		
	    		template.createQuestion(questionRef, questionName);
	    	}
	    	
	    	Question q = template.getQuestion(questionRef);
	    	
	    	// Update the question object with the properties of the bind element
	    	for(int i = 0; i < nm.getLength(); i++) {
	    		Node attribute = nm.item(i);
	    		String name = attribute.getNodeName();
	    		if (name.equals("nodeset")) {
	    			continue;		// Done this one
	    			
	    		} else if (name.equals("readonly")) {
	    			if(attribute.getNodeValue().equals("true()")) {
	    				q.setReadOnly(true);
	    			}
	    			
	    		} else if (name.equals("required")) {
	    			if(attribute.getNodeValue().equals("true()")) {
	    				q.setMandatory(true);
	    			}
	    			
	    		} else if (name.equals("type")) {
	    			String type = attribute.getNodeValue();
	    			if(type.equals("integer")) {	// standardise on int
	    				type = "int";
	    			}
	    			if(!type.equals("binary")) {	// binary types set to audio, image or video by the question
	    				q.setType(type);  
	    			}
	    			if(type.equals("geopoint") || type.equals("geoshape") || type.equals("geotrace")) {	// Geo point data types will be named "the_geom"
	    				q.setName("the_geom");
	    			}
	    			
	    		} else if (name.equals("relevant")) {
	   				q.setRelevant(attribute.getNodeValue());   			
	    			
	    		} else if (name.equals("constraint")) {
	   				q.setConstraint(attribute.getNodeValue());   			
	    			
	    		} else if (name.equals("jr:preload")) {
	   				q.setSource(attribute.getNodeValue());   			
	    			
	    		} else if (name.equals("jr:preloadParams")) {
	   				q.setSourceParam(attribute.getNodeValue());   			
	    			
	    		} else if (name.equals("jr:constraintMsg")) {
	   				q.setConstraintMsg(attribute.getNodeValue());   			
	    			
	    		} else if (name.equals("jr:requiredMsg")) {
	   				q.setRequiredMsg(attribute.getNodeValue());   			
	    			
	    		} else if (name.equals("calculate")) {
	    			
	   				q.setCalculate(attribute.getNodeValue()); 
	   				
	   				// Survey level manifests can be set in the appearance attribute
	   				template.addManifestFromCalculate(attribute.getNodeValue());
	   				template.addSurveyInstanceNameFromCalculate(attribute.getNodeValue(), questionRef);
	   				
	   				if(q.getType() == null || !q.getType().startsWith("begin")) {
	   					q.setSource("user");	// Set source as it may not have been set in the body
	   				}
	    		} else {
	    			System.out.println("Warning, bind attribute ignored (" + name + ":" + attribute.getNodeValue());
	    		}
	    	}
    	//}
    	

      } 

    	
}

