package org.smap.server.utilities;

import java.io.StringWriter;
import java.io.Writer;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.Stack;
import java.util.logging.Logger;

import javax.servlet.http.HttpServletRequest;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Result;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.smap.sdal.Utilities.GeneralUtilityMethods;
import org.smap.sdal.Utilities.SDDataSource;
import org.smap.sdal.managers.SurveyManager;
import org.smap.sdal.model.Form;
import org.smap.sdal.model.Label;
import org.smap.sdal.model.Language;
import org.smap.sdal.model.Question;
import org.smap.sdal.model.Survey;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/*
 * Return an HTML5 section built from a survey defined in the database
 * 
 * Translations are stored as a hierarchy of values from language down to element type (as below)
 *   languages (HashMap)-> translation id's (HashMap)-> translation types (HashMap) -> translation element (String)
 */
public class GetHtml {

	Survey survey = null;
	int languageIndex = 0;

	private static Logger log = Logger.getLogger(GetHtml.class.getName());

	/*
	 * Get the Html as a string
	 */
	public String get(HttpServletRequest request, int sId, boolean superUser) {

		String response = null;

		// Get the base path
		String basePath = GeneralUtilityMethods.getBasePath(request);
		Connection sd = SDDataSource.getConnection("Get Html");
		SurveyManager sm = new SurveyManager();

		try {

			survey = sm.getById(sd, null, request.getRemoteUser(), sId, true, basePath, null, false, false, true, true,
					false, "internal", superUser, 0, null);

			log.info("Getting survey as Html-------------------------------");
			// Create a new XML Document
			DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
			DocumentBuilder b = dbf.newDocumentBuilder();
			Document outputHtml = b.newDocument();

			Writer outWriter = new StringWriter();
			Result outStream = new StreamResult(outWriter);

			Element parent;
			parent = populateRoot(outputHtml);
			// populateHead(sd, outputHtml, b, parent);
			createForm(outputHtml, parent, true, true);

			// Write the survey to a string and return it to the calling program
			Transformer transformer = TransformerFactory.newInstance().newTransformer();
			transformer.setOutputProperty(OutputKeys.INDENT, "yes");
			transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
			transformer.setOutputProperty(OutputKeys.METHOD, "xml");

			DOMSource source = new DOMSource(outputHtml);
			transformer.transform(source, outStream);

			response = outWriter.toString();

		} catch (Exception e) {
			response = e.getMessage();
			e.printStackTrace();
		} finally {
			SDDataSource.closeConnection("getXForm", sd);
		}

		return response;

	}

	/*
	 * Create the root element
	 * 
	 * @param outputDoc
	 */
	public Element populateRoot(Document outputDoc) {

		Element rootElement = outputDoc.createElement("root");
		/*
		 * rootElement.setAttribute("xmlns:ev", "http://www.w3.org/2001/xml-events");
		 * rootElement.setAttribute("xmlns:h", "http://www.w3.org/1999/xhtml");
		 * rootElement.setAttribute("xmlns:jr", "http://openrosa.org/javarosa");
		 * rootElement.setAttribute("xmlns:xsd", "http://www.w3.org/2001/XMLSchema");
		 * rootElement.setAttribute("xmlns:xf", "http://www.w3.org/2002/xforms");
		 * rootElement.setAttribute("xmlns:xalan", "http://xml.apache.org/xalan\"");
		 */
		outputDoc.appendChild(rootElement);

		return rootElement;
	}

	public void createForm(Document outputDoc, Element parent, boolean isWebForms, boolean useNodesets)
			throws Exception {

		Element bodyElement = outputDoc.createElement("form");
		bodyElement.setAttribute("novalidate", "novalidate");
		bodyElement.setAttribute("autocomplete", "off");
		bodyElement.setAttribute("class", "or clearfix");
		bodyElement.setAttribute("id", survey.getIdent());

		populateForm(outputDoc, bodyElement);
		parent.appendChild(bodyElement);
	}

	private void populateForm(Document outputDoc, Element parent) {

		// logo
		Element bodyElement = outputDoc.createElement("section");
		bodyElement.setAttribute("class", "form-logo");
		bodyElement.setTextContent(" "); // Set a dummy value a enketo does not understand empty sections
		parent.appendChild(bodyElement);

		// title
		bodyElement = outputDoc.createElement("h3");
		bodyElement.setAttribute("id", "form-title");
		bodyElement.setTextContent(survey.getDisplayName());
		parent.appendChild(bodyElement);

		// Languages
		bodyElement = outputDoc.createElement("select");
		bodyElement.setAttribute("id", "form-languages");
		if (survey.languages == null || survey.languages.size() == 0) {
			bodyElement.setAttribute("style", "display:none;");
		}
		bodyElement.setAttribute("data-default-lang", survey.def_lang);
		populateLanguageChoices(outputDoc, bodyElement);
		parent.appendChild(bodyElement);

		// Questions
		System.out.println("==== Questions");
		for (Form form : survey.forms) {
			if (form.parentform == 0) { // Start with top level form
				processQuestions(outputDoc, parent, form);
				break;
			}
		}

	}

	private void populateLanguageChoices(Document outputDoc, Element parent) {
		Element bodyElement = null;
		int idx = 0;
		for (Language lang : survey.languages) {
			bodyElement = outputDoc.createElement("option");
			bodyElement.setAttribute("value", lang.name);
			bodyElement.setTextContent(lang.name);
			parent.appendChild(bodyElement);

			// Save the index of the default language
			if (lang.name.equals(survey.def_lang)) {
				languageIndex = idx;
			}
			idx++;
		}
	}

	/*
	 * Process the main block of questions Skip over: - preloads - meta group
	 */
	private void processQuestions(Document outputDoc, Element parent, Form form) {

		Element bodyElement = null;
		Element currentParent = parent;
		Stack<Element> elementStack = new Stack<Element>();	// Store the elements for non repeat groups
		
		for (Question q : form.questions) {

			if(!q.inMeta && !q.name.equals("meta_groupEnd")) {		// ignore meta for the moment
				if (q.type.equals("end group")) {
					currentParent = elementStack.pop();
					
				} else if (q.type.equals("begin group")) {

					elementStack.push(currentParent);
					currentParent = addGroupWrapper(outputDoc, currentParent, q, false);
					
				} else if (q.type.equals("begin repeat")) {
					
					elementStack.push(currentParent);
					currentParent = addGroupWrapper(outputDoc, currentParent, q, true);
								
					addRepeat(outputDoc, currentParent, q);

					// repeat into
					Element repeatInfo = outputDoc.createElement("div");
					repeatInfo.setAttribute("class", "or-repeat-info");
					repeatInfo.setAttribute("data-name", q.name);
					currentParent.appendChild(repeatInfo);
					
					// Exit the group
					currentParent = elementStack.pop();
					
				} else if (q.isPreload()) {
					// Ignore pre-loads for the moment
					
				} else if (q.inMeta) {
					// Ignore meta questions for the moment
					
				} else {
	
					// Label
					bodyElement = outputDoc.createElement("label");
					bodyElement.setAttribute("class", "question" + (q.isSelect() ? "" : " non-select"));
					addLabelContents(outputDoc, bodyElement, q);
					currentParent.appendChild(bodyElement);
	
				}
			}
		}

	}

	/*
	 * Add the contents of a label
	 */
	private void addLabelContents(Document outputDoc, Element parent, Question q) {

		// span
		addLabels(outputDoc, parent, q);

		// input
		Element bodyElement = outputDoc.createElement("input");
		bodyElement.setAttribute("type", getInputType(q)); // TODO other types
		bodyElement.setAttribute("name", q.name); // TODO set path?
		bodyElement.setAttribute("data-type-xml", getDataType(q)); // TODO other types
		parent.appendChild(bodyElement);
	}

	/*
	 * Add a wrapper for a group then return the new parent
	 */
	private Element addGroupWrapper(Document outputDoc, Element parent, Question q, boolean repeat) {
		Element groupElement = outputDoc.createElement("section");
		groupElement.setAttribute("class", "or-group");
		if (!repeat) {
			groupElement.setAttribute("name", q.name);
		}
		addGroupTitle(outputDoc, groupElement, q);
		parent.appendChild(groupElement);		
		return groupElement;
	}
	
	/*
	 * Add a wrapper for a repeat then return the new parent
	 */
	private void addRepeat(Document outputDoc, Element parent, Question q) {
		
		Element bodyElement = outputDoc.createElement("section");
		bodyElement.setAttribute("class", "or-repeat");
		bodyElement.setAttribute("name", q.name);

		// Process sub form
		for (Form subForm : survey.forms) {
			if (subForm.parentQuestion == q.id) { // continue with next form
				processQuestions(outputDoc, bodyElement, subForm);
				break;
			}
		}
		
		parent.appendChild(bodyElement);

	}
	
	private void addGroupTitle(Document outputDoc, Element parent, Question q) {
		Element bodyElement = outputDoc.createElement("h4");
		addLabels(outputDoc, bodyElement, q);
		parent.appendChild(bodyElement);
	}
	
	private void addLabels(Document outputDoc, Element parent, Question q) {
		int idx = 0;
		Element bodyElement = null;
		for (Language lang : survey.languages) {
			bodyElement = outputDoc.createElement("span");
			bodyElement.setAttribute("lang", lang.name);
			bodyElement.setAttribute("class", "question-label" + (lang.name.equals(survey.def_lang) ? " active" : ""));
			bodyElement.setAttribute("data-itext-id", q.text_id);
			bodyElement.setTextContent(q.labels.get(idx).text);
			parent.appendChild(bodyElement);
			idx++;
		}
	}
	
	/*
	 * Return the input type required by enketo
	 */
	private String getInputType(Question q) {
		
		String type = null;
		if(q.type.equals("int")) {
			type = "number";
		} else if(q.type.equals("string")) {
			type = "text";
		} else {
			log.info("#### unknown type: " + q.type + " for question " + q.name);
			type = "text";
		}
		return type;
	}
	
	/*
	 * Return the data type required by enketo
	 */
	private String getDataType(Question q) {
		
		String type = null;
		if(q.type.equals("int")) {
			type = "int";
		} else if(q.type.equals("string")) {
			type = "string";
		} else {
			type = "string";
		}
		return type;
	}

}
