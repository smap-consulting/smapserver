package org.smap.server.utilities;

import java.io.StringWriter;
import java.io.Writer;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Stack;
import java.util.logging.Level;
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
import org.smap.sdal.model.Option;
import org.smap.sdal.model.OptionList;
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
	HashMap<String, String> paths = new HashMap<>(); // Keep paths out of the survey model and instead store them here

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
		bodyElement.setAttribute("class",
				"or clearfix" + (survey.surveyClass != null ? (" " + survey.surveyClass) : ""));
		bodyElement.setAttribute("dir", "ltr");
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
		bodyElement.setAttribute("dir", "auto");
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
		for (Form form : survey.forms) {
			if (form.parentform == 0) { // Start with top level form
				addPaths(form, "/");
				processQuestions(outputDoc, parent, form);
				processPreloads(outputDoc, parent, form);
				processCalculations(outputDoc, parent, form);
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
	 * Associate question names with their paths
	 */
	private void addPaths(Form form, String pathStem) {

		Stack<String> pathStack = new Stack<>(); // Store the paths as we go in and out of groups

		pathStem = pathStem + form.name + "/";
		System.out.println("Path: " + pathStem);

		for (Question q : form.questions) {

			paths.put(getRefName(q.name, form), pathStem + q.name); // Save the path

			if (!q.inMeta && !q.name.equals("meta_groupEnd") && !q.isPreload() && !q.type.equals("calculate")) {
				if (q.type.equals("end group")) {

					pathStem = pathStack.pop();

				} else if (q.type.equals("begin group")) {

					pathStack.push(pathStem);
					pathStem = pathStem + q.name + "/";

				} else if (q.type.equals("begin repeat")) {


					for (Form subForm : survey.forms) {
						if (subForm.parentQuestion == q.id) { // continue with next form
							addPaths(subForm, pathStem);
							break;
						}
					}

				} 
			}
		}

	}
	
	/*
	 * Process the main block of questions Skip over: - preloads - meta group
	 */
	private void processQuestions(Document outputDoc, Element parent, Form form) {

		Element bodyElement = null;
		Element currentParent = parent;
		Stack<Element> elementStack = new Stack<>(); // Store the elements for non repeat groups
		Stack<String> pathStack = new Stack<>(); // Store the paths as we go in and out of groups

		for (Question q : form.questions) {

			if (!q.inMeta && !q.name.equals("meta_groupEnd") && !q.isPreload() && !q.type.equals("calculate")) {
				if (q.type.equals("end group")) {

					currentParent = elementStack.pop();

				} else if (q.type.equals("begin group")) {

					elementStack.push(currentParent);
					currentParent = addGroupWrapper(outputDoc, currentParent, q, false, form);

				} else if (q.type.equals("begin repeat")) {

					elementStack.push(currentParent);
					currentParent = addGroupWrapper(outputDoc, currentParent, q, true, form);

					addRepeat(outputDoc, currentParent, q, form);

					// repeat into
					Element repeatInfo = outputDoc.createElement("div");
					repeatInfo.setAttribute("class", "or-repeat-info");
					repeatInfo.setAttribute("data-name", paths.get(getRefName(q.name, form)));
					currentParent.appendChild(repeatInfo);

					// Exit the group
					currentParent = elementStack.pop();

				} else if (q.isSelect()) {

					// fieldset
					bodyElement = outputDoc.createElement("fieldset");
					bodyElement.setAttribute("class", "question");

					Element extraFieldsetElement = outputDoc.createElement("fieldset");
					bodyElement.appendChild(extraFieldsetElement);

					addSelectContents(outputDoc, extraFieldsetElement, q, form);
					currentParent.appendChild(bodyElement);

				} else {

					// Non select question
					bodyElement = outputDoc.createElement("label");
					bodyElement.setAttribute("class", "question non-select" +
							(q.relevant != null && q.relevant.trim().length() > 0 ? " or-branch pre-init" : ""));
					addLabelContents(outputDoc, bodyElement, q, form);
					currentParent.appendChild(bodyElement);

				}
			}
		}

	}

	/*
	 * Process the main block of questions Preloads are only in the top level form
	 */
	private void processPreloads(Document outputDoc, Element parent, Form form) {

		Element preloadElement = null;
		Element bodyElement = outputDoc.createElement("fieldset");
		bodyElement.setAttribute("style", "display:none;");
		bodyElement.setAttribute("id", "or-preload-items");

		for (Question q : form.questions) {

			if (q.isPreload() && !q.inMeta) {
				preloadElement = outputDoc.createElement("label");
				preloadElement.setAttribute("class", "calculation non-select");
				bodyElement.appendChild(preloadElement);

				preloadElement = outputDoc.createElement("input");
				preloadElement.setAttribute("type", "hidden");
				preloadElement.setAttribute("name", paths.get(getRefName(q.name, form)));
				preloadElement.setAttribute("data-preload", q.source);
				preloadElement.setAttribute("data-preload-params", q.source_param);
				preloadElement.setAttribute("data-type-xml", getXmlType(q));
				bodyElement.appendChild(preloadElement);
			}
		}
		parent.appendChild(bodyElement);

	}

	/*
	 * Process the main block of questions Preloads are only in the top level form
	 */
	private void processCalculations(Document outputDoc, Element parent, Form form) {

		Element preloadElement = null;
		Element bodyElement = outputDoc.createElement("fieldset");
		bodyElement.setAttribute("style", "display:none;");
		bodyElement.setAttribute("id", "or-calculated-items");

		for (Question q : form.questions) {

			if (q.type.equals("calculate")) {
				if (q.source_param != null && q.source_param.equals("deviceid")) {
					// Add a calculate for the device
					q.calculation = "'webform'";
				}

				if (q.calculation != null && q.calculation.trim().length() > 0) {
					preloadElement = outputDoc.createElement("label");
					preloadElement.setAttribute("class", "calculation non-select");
					bodyElement.appendChild(preloadElement);

					preloadElement = outputDoc.createElement("input");
					preloadElement.setAttribute("type", "hidden");
					preloadElement.setAttribute("name", paths.get(getRefName(q.name, form)));
					try {
						preloadElement.setAttribute("data-calculate", 
								UtilityMethods.convertAllxlsNames(q.calculation, false, paths, form.id));
					} catch (Exception e) {
						log.log(Level.SEVERE, e.getMessage(), e);
					}
					preloadElement.setAttribute("data-type-xml", getXmlType(q));
					bodyElement.appendChild(preloadElement);
				}
			}

		}
		parent.appendChild(bodyElement);

	}

	/*
	 * Add the contents of a select
	 */
	private void addSelectContents(Document outputDoc, Element parent, Question q, Form form) {

		// legend
		Element bodyElement = outputDoc.createElement("legend");
		addLabels(outputDoc, bodyElement, q, form);
		parent.appendChild(bodyElement);

		Element optionWrapperElement = outputDoc.createElement("div");
		optionWrapperElement.setAttribute("class", "option-wrapper");

		// options
		addOptions(outputDoc, optionWrapperElement, q, form);
		parent.appendChild(optionWrapperElement);

	}

	/*
	 * Add the contents of a label
	 */
	private void addLabelContents(Document outputDoc, Element parent, Question q, Form form) {

		// span
		addLabels(outputDoc, parent, q, form);

		// input
		Element bodyElement = outputDoc.createElement("input");
		bodyElement.setAttribute("type", getInputType(q));
		bodyElement.setAttribute("name", paths.get(getRefName(q.name, form)));
		bodyElement.setAttribute("data-type-xml", getXmlType(q));

		// constraint
		if (q.constraint != null && q.constraint.trim().length() > 0) {
			try {
				bodyElement.setAttribute("data-constraint", 
						UtilityMethods.convertAllxlsNames(q.constraint, false, paths, form.id));
			} catch (Exception e) {
				log.log(Level.SEVERE, e.getMessage(), e);
			}
		}

		// relevant
		if (q.relevant != null && q.relevant.trim().length() > 0) {
			try {
				bodyElement.setAttribute("data-relevant", 
						UtilityMethods.convertAllxlsNames(q.relevant, false, paths, form.id));
			} catch (Exception e) {
				log.log(Level.SEVERE, e.getMessage(), e);
			}
		}
		
		parent.appendChild(bodyElement);
	}

	private void addOptions(Document outputDoc, Element parent, Question q, Form form) {

		// label
		Element bodyElement = outputDoc.createElement("label");
		bodyElement.setAttribute("class", "itemset-template");
		bodyElement.setAttribute("data-items-path", q.nodeset);

		Element inputElement = outputDoc.createElement("input");
		inputElement.setAttribute("type", getInputType(q));
		inputElement.setAttribute("data-name", paths.get(getRefName(q.name, form)));
		inputElement.setAttribute("data-type-xml", getXmlType(q));
		bodyElement.appendChild(inputElement);

		parent.appendChild(bodyElement);

		Element optionElement = outputDoc.createElement("span");
		optionElement.setAttribute("class", "itemset-labels");
		optionElement.setAttribute("data-value-ref", "name");
		optionElement.setAttribute("data-label-type", "itext");
		optionElement.setAttribute("data-label-ref", "itextId");
		addOptionLabels(outputDoc, optionElement, q, form);

		parent.appendChild(optionElement);

	}

	private void addOptionLabels(Document outputDoc, Element parent, Question q, Form form) {

		ArrayList<Option> options = survey.optionLists.get(q.list_name).options;
		for (Option o : options) {
			int idx = 0;
			Element bodyElement = null;
			for (Language lang : survey.languages) {
				bodyElement = outputDoc.createElement("span");
				bodyElement.setAttribute("lang", lang.name);
				bodyElement.setAttribute("class",
						"option-label" + (lang.name.equals(survey.def_lang) ? " active" : ""));
				bodyElement.setAttribute("data-itext-id", o.text_id);
				
				String label = o.labels.get(idx).text;
				try {
					label = UtilityMethods.convertAllxlsNames(o.labels.get(idx).text, true, paths, form.id);
				} catch (Exception e) {
					log.log(Level.SEVERE, e.getMessage(), e);
				}
				bodyElement.setTextContent(label);
				parent.appendChild(bodyElement);
				idx++;
			}

			parent.appendChild(bodyElement);
		}

	}

	/*
	 * Add a wrapper for a group then return the new parent
	 */
	private Element addGroupWrapper(Document outputDoc, Element parent, Question q, boolean repeat, Form form) {
		Element groupElement = outputDoc.createElement("section");
		if (hasLabel(q)) {
			groupElement.setAttribute("class", "or-group");
		} else {
			groupElement.setAttribute("class", "or-group-data");
		}
		if (!repeat) {
			groupElement.setAttribute("name", paths.get(getRefName(q.name, form)));
		}
		addGroupTitle(outputDoc, groupElement, q, form);
		parent.appendChild(groupElement);
		return groupElement;
	}

	/*
	 * Add a wrapper for a repeat then return the new parent
	 */
	private void addRepeat(Document outputDoc, Element parent, Question q, Form form) {

		Element bodyElement = outputDoc.createElement("section");
		bodyElement.setAttribute("class", "or-repeat");
		bodyElement.setAttribute("name", paths.get(getRefName(q.name, form)));

		// Process sub form
		for (Form subForm : survey.forms) {
			if (subForm.parentQuestion == q.id) { // continue with next form
				processQuestions(outputDoc, bodyElement, subForm);
				break;
			}
		}

		parent.appendChild(bodyElement);

	}

	private void addGroupTitle(Document outputDoc, Element parent, Question q, Form form) {
		Element bodyElement = outputDoc.createElement("h4");
		addLabels(outputDoc, bodyElement, q, form);
		parent.appendChild(bodyElement);
	}

	private void addLabels(Document outputDoc, Element parent, Question q, Form form) {
		int idx = 0;
		Element bodyElement = null;
		for (Language lang : survey.languages) {
			bodyElement = outputDoc.createElement("span");
			bodyElement.setAttribute("lang", lang.name);
			bodyElement.setAttribute("class", "question-label" + (lang.name.equals(survey.def_lang) ? " active" : ""));
			bodyElement.setAttribute("data-itext-id", q.text_id);
			
			String label = q.labels.get(idx).text;
			try {
				label = UtilityMethods.convertAllxlsNames(q.labels.get(idx).text, true, paths, form.id);
			} catch (Exception e) {
				log.log(Level.SEVERE, e.getMessage(), e);
			}
			bodyElement.setTextContent(label);
			parent.appendChild(bodyElement);
			idx++;
		}

		// Constraint message
		if (q.constraint_msg != null && q.constraint_msg.length() > 0) {
			bodyElement = outputDoc.createElement("span");
			bodyElement.setAttribute("lang", "");
			bodyElement.setAttribute("class", "or-constraint-msg active");
			bodyElement.setTextContent(q.constraint_msg);
			parent.appendChild(bodyElement);
		}
	}

	/*
	 * Return the input type required by enketo
	 */
	private String getInputType(Question q) {

		String type = null;
		if (q.type.equals("int")) {
			type = "number";
		} else if (q.type.equals("string")) {
			type = "text";
		} else if (q.type.equals("select1")) {
			type = "radio";
		} else if (q.type.equals("select")) {
			type = "checkbox";
		} else {
			log.info("#### unknown type: " + q.type + " for question " + q.name);
			type = "text";
		}
		return type;
	}

	/*
	 * Return the input type required by enketo
	 */
	private String getXmlType(Question q) {

		String type = null;
		if (q.type.equals("calculate")) {
			type = "string";
		} else {
			type = q.type;
		}
		return type;
	}

	/*
	 * Returns true if the question has any label - text, image, audio or video
	 */
	private boolean hasLabel(Question q) {
		boolean hasLabel = false;

		for (int i = 0; i < survey.languages.size(); i++) {
			if (q.labels.get(i) != null) {
				Label l = q.labels.get(i);
				if ((l.text != null && l.text.trim().length() > 0) || l.image != null || l.video != null
						|| l.audio != null) {
					hasLabel = true;
					break;
				}
			}

		}
		return hasLabel;

	}
	
	private String getRefName(String qName, Form form) {
		if(qName.equals("_the_geom")) {
			return form.id + "_the_geom";
		} else {
			return qName;
		}
	}

}
