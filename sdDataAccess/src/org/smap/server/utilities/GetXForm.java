package org.smap.server.utilities;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
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
import java.util.ResourceBundle;
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
import org.smap.sdal.Utilities.CSVParser;
import org.smap.sdal.Utilities.GeneralUtilityMethods;
import org.smap.sdal.Utilities.ResultsDataSource;
import org.smap.sdal.Utilities.SDDataSource;
import org.smap.sdal.managers.SurveyTableManager;
import org.smap.sdal.managers.TaskManager;
import org.smap.sdal.managers.TranslationManager;
import org.smap.sdal.model.Instance;
import org.smap.sdal.model.KeyValueSimp;
import org.smap.sdal.model.Line;
import org.smap.sdal.model.ManifestValue;
import org.smap.sdal.model.MetaItem;
import org.smap.sdal.model.Point;
import org.smap.sdal.model.Polygon;
import org.smap.server.entities.Form;
import org.smap.server.entities.Option;
import org.smap.server.entities.Question;
import org.smap.server.entities.Survey;
import org.smap.server.entities.Translation;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/*
 * Return an XForm built from a survey defined in the database
 * 
 * Translations are stored as a hierarchy of values from language down to element type (as below)
 *   languages (HashMap)-> translation id's (HashMap)-> translation types (HashMap) -> translation element (String)
 */
public class GetXForm {
	private int INSTANCE = 1; // Identifies which part of the XML document is being updated with form details
	private int BIND = 2;
	private int BODY = 3;
	private Form firstForm;
	SurveyTemplate template = null;
	private String gInstanceId = null;
	private String gSurveyClass = null;
	private ArrayList<String> gFilenames;
	private ArrayList<String> gPaths;
	private boolean embedExternalSearch = false;
	private boolean gInTableList = false;
	private boolean modelInstanceOnly = false;
	private boolean isWebForms = false;
	private boolean useNodesets = false;
	private ResourceBundle localisation = null;
	String remoteUser = null;
	private String tz;
	
	private static  String FILE_MIME="text/plain,application/pdf,application/vnd.ms-excel,application/msword,text/richtext,application/vnd.openxmlformats-officedocument.wordprocessingml.document,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet,application/zip,application/x-zip,application/x-zip-compressed" ;

	private HashMap<String, Integer> gRecordCounts = new HashMap<> ();
	
	private static Logger log = Logger.getLogger(GetXForm.class.getName());

	public GetXForm(ResourceBundle l, String user, String tz) {
		localisation = l;
		if(tz == null) {
			tz = "UTC";
		}
		this.tz = tz;
		remoteUser = user;
	}
	
	/*
	 * Get the XForm as a string
	 */
	public String get(SurveyTemplate template, boolean isWebForms, boolean useNodesets, 
			boolean modelInstanceOnly, String user) throws Exception {

		// Set Globals
		this.modelInstanceOnly = modelInstanceOnly;
		this.isWebForms = isWebForms;
		this.useNodesets = useNodesets;
		this.template = template;

		String response = null;

		if (isWebForms) {
			embedExternalSearch = true; // Webforms do not support search therefore embed the choices in the html
		}

		Connection sd = null;
		Connection cResults = null;
		try {
			sd = SDDataSource.getConnection("getXForm");
			cResults = ResultsDataSource.getConnection("getXForm");

			// Create a new XML Document
			DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
			DocumentBuilder b = dbf.newDocumentBuilder();
			Document outputXML = b.newDocument();

			Writer outWriter = new StringWriter();
			Result outStream = new StreamResult(outWriter);

			// Get the first form
			String firstFormRef = template.getFirstFormRef();
			if (firstFormRef == null) {
				log.log(Level.SEVERE, "Error: First Form Reference is null");
			}
			firstForm = template.getForm(firstFormRef);

			Element parent;
			if (modelInstanceOnly) {
				parent = outputXML.createElement("model");
				outputXML.appendChild(parent);
				populateModel(sd, cResults, outputXML, b, parent, user);
			} else {
				parent = populateRoot(outputXML);
				populateHead(sd, cResults, outputXML, b, parent, user);
				populateBody(sd, outputXML, parent);
			}

			// Write the survey to a string and return it to the calling program
			Transformer transformer = TransformerFactory.newInstance().newTransformer();
			transformer.setOutputProperty(OutputKeys.INDENT, "yes");
			transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
			transformer.setOutputProperty(OutputKeys.METHOD, "xml");

			DOMSource source = new DOMSource(outputXML);
			transformer.transform(source, outStream);

			response = outWriter.toString();

		} finally {
			SDDataSource.closeConnection("getXForm", sd);
			ResultsDataSource.closeConnection("getXForm", cResults);
		}

		return response;

	}

	/*
	 * Create the root element
	 * 
	 * @param outputDoc
	 */
	public Element populateRoot(Document outputDoc) {

		Element rootElement = outputDoc.createElement("h:html");
		rootElement.setAttribute("xmlns", "http://www.w3.org/2002/xforms");
		rootElement.setAttribute("xmlns:ev", "http://www.w3.org/2001/xml-events");
		rootElement.setAttribute("xmlns:h", "http://www.w3.org/1999/xhtml");
		rootElement.setAttribute("xmlns:jr", "http://openrosa.org/javarosa");
		rootElement.setAttribute("xmlns:odk", "http://www.opendatakit.org/xforms");
		rootElement.setAttribute("xmlns:orx", "http://openrosa.org/xforms");
		rootElement.setAttribute("xmlns:xsd", "http://www.w3.org/2001/XMLSchema");
		outputDoc.appendChild(rootElement);

		return rootElement;
	}

	/*
	 * Populate the head element
	 * 
	 * @param outputXML
	 */
	public void populateHead(Connection sd, Connection cResults, Document outputDoc, 
			DocumentBuilder documentBuilder, Element parent, String user)
			throws Exception {

		Survey s = template.getSurvey();

		// Create Elements
		Element headElement = outputDoc.createElement("h:head");
		parent.appendChild(headElement);

		Element titleElement = outputDoc.createElement("h:title");
		titleElement.setTextContent(s.getDisplayName());
		headElement.appendChild(titleElement);

		Element modelElement = outputDoc.createElement("model");
		headElement.appendChild(modelElement);

		populateModel(sd, cResults, outputDoc, documentBuilder, modelElement, user);

	}

	/*
	 * Populate the model
	 */
	private void populateModel(Connection sd, Connection cResults, Document outputDoc, DocumentBuilder documentBuilder, 
			Element parent, String user)
			throws Exception {

		if (!modelInstanceOnly) {
			Element itextElement = outputDoc.createElement("itext");
			parent.appendChild(itextElement);
			populateItext(sd, outputDoc, documentBuilder, itextElement);
		}

		Element instanceElement = outputDoc.createElement("instance");
		parent.appendChild(instanceElement);
		populateInstance(sd, outputDoc, instanceElement);

		// Add a last saved instance  <instance id="last-saved" src="jr://instance/last-saved"/>
		if(!isWebForms) {
			Element lastSavedElement = outputDoc.createElement("instance");
			lastSavedElement.setAttribute("id", "last-saved");
			lastSavedElement.setAttribute("src", "jr://instance/last-saved");
			parent.appendChild(lastSavedElement);
		}
		
		if (template.hasCascade()) {

			List<CascadeInstance> cis = template.getCascadeInstances();
			for (int i = 0; i < cis.size(); i++) {
				Element cascadeInstanceElement = outputDoc.createElement("instance");
				cascadeInstanceElement.setAttribute("id", cis.get(i).name);
				parent.appendChild(cascadeInstanceElement);
				Element rootElement = outputDoc.createElement("root");
				cascadeInstanceElement.appendChild(rootElement);
				populateCascadeOptions(outputDoc, rootElement, cis.get(i));
			}
		}

		// Add pulldata instances as required by enketo
		if (isWebForms) {
			TranslationManager tm = new TranslationManager();
			List<ManifestValue> manifests = tm.getPulldataManifests(sd, template.getSurvey().getId());
			for (int i = 0; i < manifests.size(); i++) {
				ManifestValue mv = manifests.get(i);
				if (mv.filePath != null || (mv.type != null && mv.type.equals("linked"))) {
					Element pulldataElement = outputDoc.createElement("instance");
					pulldataElement.setAttribute("id", mv.baseName);
					pulldataElement.setAttribute("src", "jr://csv/" + mv.baseName + ".csv");
					parent.appendChild(pulldataElement);
					Element rootElement = outputDoc.createElement("root");
					pulldataElement.appendChild(rootElement);
					
					if(mv.filePath != null) {
						populateCSVElements(outputDoc, rootElement, mv.filePath);
					} else {
						int oId = GeneralUtilityMethods.getOrganisationId(sd, remoteUser);
						SurveyTableManager stm = new SurveyTableManager(sd, cResults, localisation, oId, mv.sId, mv.fileName, user);
						populateCSVElementsFromSurvey(outputDoc, rootElement, stm);
					}
				} 
			}
		}

		// Add forms to bind elements
		if (firstForm != null && !modelInstanceOnly) {
			populateForm(sd, outputDoc, parent, BIND, firstForm);
		}
	}

	/*
	 * Populate the itext element with language translations
	 */
	public void populateItext(Connection sd, Document outputDoc, DocumentBuilder builder, Element parent)
			throws SQLException {

		Survey s = template.getSurvey();

		HashMap<String, HashMap<String, HashMap<String, Translation>>> translations = template.getTranslations();
		// Write the translation objects
		Collection<HashMap<String, HashMap<String, Translation>>> c = translations.values();
		Iterator<HashMap<String, HashMap<String, Translation>>> itr = c.iterator();
		while (itr.hasNext()) {
			HashMap<String, HashMap<String, Translation>> aLanguageTranslation = itr.next(); // A single language
			Collection<HashMap<String, Translation>> l = aLanguageTranslation.values();
			Iterator<HashMap<String, Translation>> itrL = l.iterator();
			Element languageElement = null;
			while (itrL.hasNext()) { // ID of a question or label
				HashMap<String, Translation> types = (HashMap<String, Translation>) itrL.next();

				Collection<Translation> t = types.values();
				Iterator<Translation> itrT = t.iterator();

				Element textElement = null;
				while (itrT.hasNext()) {

					Translation trans = (Translation) itrT.next();

					if (languageElement == null) {
						languageElement = outputDoc.createElement("translation");
						languageElement.setAttribute("lang", trans.getLanguage());
						if (s.getDefLang() != null && s.getDefLang().equals(trans.getLanguage())) {
							languageElement.setAttribute("default", "true()"); // set default language
						}
						parent.appendChild(languageElement);
					}

					if (textElement == null) {
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
					if (type.equals("image") || type.equals("video") || type.equals("audio")) {
						String base = type;
						if (type.equals("image")) {
							base = "images";
						}
						String fileLocn = trans.getValue(); // Location of file on disk, only file name is used by
						// fieldTask
						String filename = "";
						if (fileLocn != null) {
							int idx = fileLocn.lastIndexOf('/');
							if (idx > 0) {
								filename = fileLocn.substring(idx + 1);
							} else {
								filename = fileLocn;
							}
						}

						valueElement.setTextContent("jr://" + base + "/" + filename);

					} else {
						// The text could be an xml fragment
						try {

							String v = trans.getValueXML(template.getQuestionPaths(), 0);
							// valueElement.setTextContent(v);
							xfragDoc = builder.parse(new InputSource(new StringReader(v)));
							Element rootFrag = xfragDoc.getDocumentElement();
							addXmlFrag(outputDoc, valueElement, rootFrag);

						} catch (Exception e) {
							e.printStackTrace();
						}
					}

					if (!type.equals("none")) {
						valueElement.setAttribute("form", type);
					}

					textElement.appendChild(valueElement);

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
	 * Populate the Instance element starting with the top level form
	 */
	public void populateInstance(Connection sd, Document outputDoc, Element parent) throws Exception {

		if (firstForm != null) {
			Element formElement = outputDoc.createElement(firstForm.getName());
			if (modelInstanceOnly) {
				// Namespaces as per enketo
				formElement.setAttribute("xmlns:jr", "http://openrosa.org/javarosa");
				formElement.setAttribute("xmlns:orx", "http://openrosa.org/xforms");
			}
			formElement.setAttribute("id", template.getSurvey().getIdent());
			formElement.setAttribute("version", String.valueOf(template.getSurvey().getVersion()));
			if (!isWebForms) {
				formElement.setAttribute("project", String.valueOf(template.getProject().getName()));
			}
			populateForm(sd, outputDoc, formElement, INSTANCE, firstForm); // Process the top form
			parent.appendChild(formElement);
		}
	}

	/*
	 * Populate the Cascade Instance element
	 */
	public void populateCascadeInstance(Document outputDoc, Element parent, String instance) {

		log.info("TODO: Add value name and label name for instance:" + instance);

	}

	public void populateBody(Connection sd, Document outputDoc, Element parent) throws Exception {
		Element bodyElement = outputDoc.createElement("h:body");

		/*
		 * Add class if it is set
		 */
		String surveyClass = template.getSurveyClass();
		if (surveyClass != null) {
			bodyElement.setAttribute("class", surveyClass);
			gSurveyClass = surveyClass;
		}
		if (firstForm != null) {
			populateForm(sd, outputDoc, bodyElement, BODY, firstForm); // Process the top level form
		}

		parent.appendChild(bodyElement);
	}

	/*
	 * Add the form data The actual data added will vary depending on where we are
	 * in the XForm
	 * 
	 * @param outputXML
	 * 
	 * @param parent
	 * 
	 * @param location
	 * 
	 * @param f
	 * 
	 * @param parentXPath
	 */
	public void populateForm(Connection sd, Document outputDoc, Element parentElement, int location, Form f)
			throws Exception {

		Element currentParent = parentElement;
		Stack<Element> elementStack = new Stack<Element>(); // Store the elements for non repeat groups

		/*
		 * If this is the top level form add the meta and preload data for the survey
		 */
		if(!f.hasParent()) {

			ArrayList<MetaItem> preloads = template.getSurvey().getMeta();	// // preloads
			if(location == INSTANCE) {
				// Add the meta group
				Element metaGroup = outputDoc.createElement("meta");
				currentParent.appendChild(metaGroup);
				Element instanceID = outputDoc.createElement("instanceID");
				metaGroup.appendChild(instanceID);
				Element instanceName = outputDoc.createElement("instanceName");
				metaGroup.appendChild(instanceName);

				// Add a timing element if we have entered the meta group and timing is enabled
				if (template.getSurvey().getTimingData()) {
					Element audit = outputDoc.createElement("audit");
					metaGroup.appendChild(audit);
				}

				if(preloads != null) {
					for(MetaItem mi : preloads) {
						if(mi.isPreload) {
							Element preload = outputDoc.createElement(mi.name);
							currentParent.appendChild(preload);
						}
					}
				}

			} else if(location == BIND) {
				Element instanceId = outputDoc.createElement("bind");
				instanceId.setAttribute("nodeset", "/main/meta/instanceID");
				instanceId.setAttribute("type", "string");
				instanceId.setAttribute("calculate", "concat('uuid:', uuid())");
				currentParent.appendChild(instanceId);
				
				String instanceNameCalculate = UtilityMethods.convertAllxlsNames(template.getSurvey().getInstanceName(), false,
						template.getQuestionPaths(), f.getId(), false, "instanceName");
				if(instanceNameCalculate != null && instanceNameCalculate.trim().length() > 0) {
					Element instanceName = outputDoc.createElement("bind");
					instanceName.setAttribute("nodeset", "/main/meta/instanceName");
					instanceName.setAttribute("type", "string");					
					instanceName.setAttribute("calculate", instanceNameCalculate);
					currentParent.appendChild(instanceName);
				}

				if(preloads != null) {
					for(MetaItem mi : preloads) {
						if(mi.isPreload) {
							Element preload = outputDoc.createElement("bind");
							preload.setAttribute("nodeset", "/main/" + mi.name);
							preload.setAttribute("type", mi.type);
							preload.setAttribute("jr:preload", mi.dataType);
							preload.setAttribute("jr:preloadParams", mi.sourceParam);
							currentParent.appendChild(preload);
						}
					}
				}
				
				// Add parameters to control auditing of location if this is requested
				if (template.getSurvey().getTimingData()) {
					Element audit = outputDoc.createElement("bind");
					audit.setAttribute("nodeset", "/main/meta/audit");
					audit.setAttribute("type", "binary");
					if (template.getSurvey().getAuditLocationData()) {
						audit.setAttribute("odk:location-priority", "balanced");
						audit.setAttribute("odk:location-min-interval", "10");
						audit.setAttribute("odk:location-max-age", "60");
					}
					if (template.getSurvey().getTrackChanges()) {
						audit.setAttribute("odk:track-changes", "true");
					}
					currentParent.appendChild(audit);
				}
			} 
		}
		/*
		 * Add the questions from the template
		 */
		List<Question> questions = f.getQuestions(sd, f.getPath(null));
		for (Question q : questions) {

			// Backward compatability - Ignore Meta  questions 
			if(GeneralUtilityMethods.isMetaQuestion(q.getName())) {
				continue;
			}
			Element questionElement = null;
			String qType = q.getType();

			// Add a marker if this is a table list group
			if (qType.equals("begin group")) {
				if (q.isTableList) {
					gInTableList = true;
				} else {
					String appearance = q.getAppearance(false, null);
					if (appearance != null && appearance.contains("table-list")) {
						q.isTableList = true;
						gInTableList = true;
						q.setAppearance(appearance.replace("table-list", "field-list"));
					}
				}
			} else if (qType.equals("end group")) {
				gInTableList = false;
			}

			if (location == INSTANCE) {
				if (qType.equals("begin repeat") || qType.equals("geolinestring") || qType.equals("geopolygon")) {

					Form subForm = template.getSubForm(f, q);

					if (subForm.getRepeats(true, template.getQuestionPaths()) != null) {
						// Add the calculation for repeat count
						questionElement = outputDoc.createElement(q.getName() + "_count");
						currentParent.appendChild(questionElement);
					}

					Element formElement_template = outputDoc.createElement(subForm.getName());

					if (modelInstanceOnly) {
						formElement_template.setAttribute("template", ""); // The model requires a local name only
					} else {
						formElement_template.setAttribute("jr:template", "");
					}
					populateForm(sd, outputDoc, formElement_template, INSTANCE, subForm);
					currentParent.appendChild(formElement_template);

				} else if (qType.equals("begin group")) {

					// Write the question then make this element the new parent
					questionElement = outputDoc.createElement(q.getName());
					currentParent.appendChild(questionElement);

					elementStack.push(currentParent);
					currentParent = questionElement;

					// Add a dummy instance element for the table list labels if this is a table
					// list question
					if (q.isTableList) {
						Element labelsElement = getTableListLabelsElement(sd, outputDoc, f, q, f.getPath(null));
						if(labelsElement == null) {
							throw new ApplicationException(localisation.getString("ts_ns"));
						}
						currentParent.appendChild(labelsElement);
					}

				} else if (qType.equals("end group")) {

					currentParent = elementStack.pop();

				} else {

					questionElement = outputDoc.createElement(q.getName());
					if (q.getDefaultAnswer() != null) {
						questionElement.setTextContent(q.getDefaultAnswer());
					}

					currentParent.appendChild(questionElement);
				}

			} else if (location == BIND) {

				// if(subForm != null) {
				if (qType.equals("begin repeat") || qType.equals("geolinestring") || qType.equals("geopolygon")) {

					Form subForm = template.getSubForm(f, q);

					// Apply bind for repeat question
					questionElement = populateBindQuestion(outputDoc, f, q, f.getPath(null), false);
					currentParent.appendChild(questionElement);

					// Process sub form

					populateForm(sd, outputDoc, currentParent, BIND, subForm);
					if (subForm.getRepeats(true, template.getQuestionPaths()) != null) {
						// Add the calculation for repeat count
						questionElement = populateBindQuestion(outputDoc, f, q, f.getPath(null), true);
						currentParent.appendChild(questionElement);
					}

				} else if (q.getType().equals("begin group")) {

					questionElement = populateBindQuestion(outputDoc, f, q, f.getPath(null), false);
					currentParent.appendChild(questionElement);

				} else if (q.getType().equals("end group")) {

				} else {

					questionElement = populateBindQuestion(outputDoc, f, q, f.getPath(null), false);
					currentParent.appendChild(questionElement);
					

				}

			} else if (location == BODY) {
				String appearance = q.getAppearance(true, template.getQuestionPaths());
				if(appearance != null && appearance.equals("hidden")) {
					continue;
				}
				// if(subForm != null) {
				if (qType.equals("begin repeat") || qType.equals("geolinestring") || qType.equals("geopolygon")) {
					Form subForm = template.getSubForm(f, q);

					Element groupElement = outputDoc.createElement("group");
					currentParent.appendChild(groupElement);

					Element labelElement = outputDoc.createElement("label");

					String labelRef = q.getQTextId();
					if (labelRef != null && !labelRef.trim().isEmpty()) {
						String label = "jr:itext('" + labelRef + "')";
						labelElement.setAttribute("ref", label);
					}
					groupElement.appendChild(labelElement);

					Element repeatElement = outputDoc.createElement("repeat");
					repeatElement.setAttribute("nodeset", subForm.getPath(null));

					// Add appearance
					appearance = q.getAppearance(true, template.getQuestionPaths());
					if (appearance != null) {
						repeatElement.setAttribute("appearance", appearance);
					}

					String repeats = subForm.getRepeats(true, template.getQuestionPaths());
					if (repeats != null && repeats.trim().length() > 0) { // Add the path to the repeat count question
						String repeatCountPath = template.getQuestionPaths().get(q.getName()) + "_count";
						repeatElement.setAttribute("jr:count", repeatCountPath);
						repeatElement.setAttribute("jr:noAddRemove", "true()");
					} else if(q.isReference()) {
						repeatElement.setAttribute("jr:noAddRemove", "true()");
					}
					groupElement.appendChild(repeatElement);

					populateForm(sd, outputDoc, repeatElement, BODY, subForm);

				} else if(qType.equals("end group")) { 
					// Ignore end group
				} else { // Add question to output
					if (q.isVisible() || qType.equals("begin group")) {

						questionElement = populateBodyQuestion(sd, outputDoc, f, q, f.getPath(null), useNodesets);
						currentParent.appendChild(questionElement);

					}
				}

				/*
				 * Set the parent element according to whether we are entering or leaving a non
				 * repeat group Add table list labels if this is a table-list group
				 */
				if (qType.equals("end group")) {

					currentParent = elementStack.pop();

				} else if (qType.equals("begin group")) {

					elementStack.push(currentParent);
					currentParent = questionElement;

					// Add table list labels
					if (q.isTableList) {
						Element labelsElement = populateTableListLabels(sd, outputDoc, f, q, f.getPath(null));
						currentParent.appendChild(labelsElement);
					}
				}
			}
		}
	}

	/*
	 * Populate the question element if this is part of the XForm bind
	 */
	public Element populateBindQuestion(Document outputXML, Form f, Question q, String parentXPath, boolean count)
			throws Exception {

		Element questionElement = outputXML.createElement("bind");

		// Add type
		String type = q.getType();
		String dataType = q.getDataType();

		if(type.equals("range")) {
			dataType = getDataTypeFromRange(q.getParameters());
		}

		if (type.equals("audio") || type.equals("video") || type.equals("image") || type.equals("file")) {
			type = "binary";
		} else if (type.equals("begin repeat") && count) {
			type = "string"; // For a calculate
		} else if (type.equals("calculate")) {
			type = "string";
		} else if (type.equals("note")) {
			type = "string";
		}
		if (!type.equals("begin group") && !type.equals("begin repeat") && !type.equals("geopolygon")
				&& !type.equals("geolinestring")) {
			// Use the data type if it exists
			if(dataType != null && dataType.trim().length() > 0) {
				dataType = "xsd:" + dataType;
				questionElement.setAttribute("type", dataType);
			} else {
				questionElement.setAttribute("type", type);
			}
		}

		// Add reference
		String reference = getQuestionReference(template.getQuestionPaths(), f.getId(), q.getName());
		if (q.getType().equals("begin repeat") && count) {
			reference += "_count"; // Reference is to the calculate question for this form
		}
		questionElement.setAttribute("nodeset", reference);

		if (!count) {
			// Add read only
			if (q.isReadOnly() || q.getType().equals("note")) {	
				questionElement.setAttribute("readonly", "true()");
			}

			// Add mandatory
			if (q.isMandatory()) {
				questionElement.setAttribute("required", "true()");

				// Add required message
				String requiredMsg = q.getRequiredMsg();
				if (requiredMsg != null && requiredMsg.trim().length() > 0) {
					questionElement.setAttribute("jr:requiredMsg", requiredMsg);
				}
			}

			// Add relevant
			String relevant = q.getRelevant(true, template.getQuestionPaths(), template.getXFormFormName());
			if (relevant != null && relevant.trim().length() > 0) {
				questionElement.setAttribute("relevant", relevant);
			}

			// Add constraint
			String constraint = q.getConstraint(true, template.getQuestionPaths(), template.getXFormFormName());
			if (constraint != null && constraint.trim().length() > 0) {
				questionElement.setAttribute("constraint", constraint);
			}

			// Add constraint message
			String constraintMsg = q.getConstraintMsg();
			if (constraintMsg != null && constraintMsg.trim().length() > 0) {
				questionElement.setAttribute("jr:constraintMsg", constraintMsg);
			}

			// Add bind parameters
			String pixelParam = GeneralUtilityMethods.getSurveyParameter("max-pixels", q.getParameters());
			if(pixelParam != null) {
				questionElement.setAttribute("orx:max-pixels", pixelParam);
			}
		}

		// Add calculate
		String calculate = null;
		if (q.getName().equals("instanceName")) {
			calculate = UtilityMethods.convertAllxlsNames(template.getSurvey().getInstanceName(), false,
					template.getQuestionPaths(), f.getId(), false, q.getName());
			if (calculate == null) {
				// Allow for legacy forms that were loaded before the instance name was
				// set in the survey table
				calculate = q.getCalculate(true, template.getQuestionPaths(), template.getXFormFormName()); 
			}
		} else if (q.getType().equals("begin repeat") && count) {
			Form subForm = template.getSubForm(f, q);
			String repeats = subForm.getRepeats(true, template.getQuestionPaths());
			if (repeats != null && repeats.trim().length() > 0) { // Add the path to the repeat count question
				calculate = repeats;
			}
		} else if(!q.getType().equals("begin group")) {
			calculate = q.getCalculate(true, template.getQuestionPaths(), template.getXFormFormName());
		}
		if (calculate != null && calculate.trim().length() > 0) {
			Survey s = template.getSurvey();
			calculate = GeneralUtilityMethods.removeSelfReferences(calculate, s.getIdent());
			questionElement.setAttribute("calculate", calculate);
		}

		// Add preload
		String source = q.getSource();
		if (source != null && source.trim().length() > 0) {
			if (!source.equals("user")) {
				questionElement.setAttribute("jr:preload", source);
				String param = q.getSourceParam();
				if (param != null && param.trim().length() > 0) {
					questionElement.setAttribute("jr:preloadParams", param);
				}
			}
		}

		return questionElement;
	}

	/*
	 * Populate the question element if this is part of the XForm bind
	 * 
	 * @param outputXML
	 * 
	 * @param f
	 * 
	 * @param q
	 */
	public Element populateBodyQuestion(Connection sd, Document outputXML, Form f, Question q, String parentXPath,
			boolean useNodesets) throws Exception {

		Element questionElement = null;
		Survey s = template.getSurvey();

		String type = q.getType();
		if (type.equals("string") || type.equals("int") || type.equals("dateTime") || type.equals("decimal")
				|| type.equals("barcode") || type.equals("date") || type.equals("geopoint") || type.equals("time") || type.equals("note")) {
			questionElement = outputXML.createElement("input");
		} else if (type.equals("select")) {
			questionElement = outputXML.createElement("select");
		} else if (type.equals("select1")) {
			questionElement = outputXML.createElement("select1");
		} else if (type.equals("image")) {
			questionElement = outputXML.createElement("upload");
			questionElement.setAttribute("mediatype", "image/*"); // Add the media type attribute
		} else if (type.equals("audio")) {
			questionElement = outputXML.createElement("upload");
			questionElement.setAttribute("mediatype", "audio/*"); // Add the media type attribute
		} else if (type.equals("video")) {
			questionElement = outputXML.createElement("upload");
			questionElement.setAttribute("mediatype", "video/*"); // Add the media type attribute
		} else if (type.equals("file")) {
			questionElement = outputXML.createElement("upload");
			questionElement.setAttribute("mediatype", FILE_MIME); // Add the media type attribute
		} else if (type.equals("begin group") || type.equals("begin repeat") || type.equals("geolinestring")
				|| type.equals("geopolygon")) {
			questionElement = outputXML.createElement("group");
		} else if (type.equals("acknowledge")) {
			questionElement = outputXML.createElement("trigger");
		} else if (type.equals("range")) {
			questionElement = outputXML.createElement("range");
		} else if (type.equals("rank")) {
			questionElement = outputXML.createElement("odk:rank");
		} else {
			log.info("Warning Unknown type- populateBodyQuestion: " + type);
			questionElement = outputXML.createElement("input");
		}

		// Add the reference attribute
		if (questionElement != null) {
			String path = getQuestionReference(template.getQuestionPaths(), f.getId(), q.getName());
			questionElement.setAttribute("ref", path);
		}

		// Add the appearance
		if (questionElement != null) {
			String appearance = q.getAppearance(true, template.getQuestionPaths());

			if (gInTableList && type.startsWith("select")) {
				if (appearance == null) {
					appearance = "";
				}
				if (!appearance.contains("field-list")) {
					appearance = appearance.trim();
					if (appearance.length() > 0) {
						appearance += " ";
					}
					appearance += "list-nolabel";
				}
			}
			if (appearance != null && appearance.trim().length() > 0) {
				appearance = GeneralUtilityMethods.removeSelfReferences(appearance, s.getIdent());
				questionElement.setAttribute("appearance", appearance);
			}
		}

		// Add Body Parameters
		ArrayList<KeyValueSimp> parameters = q.getParameters();
		if (parameters != null) {
			for(KeyValueSimp kv : parameters) {
				questionElement.setAttribute(kv.k, kv.v);
			}
		}

		// Add the autoplay
		if (questionElement != null) {
			String autoplay = q.getAutoPlay();
			if (autoplay != null) {
				questionElement.setAttribute("autoplay", autoplay);
			}
		}

		// Add the questionThreshold
		if (questionElement != null) {
			String accuracy = q.getAccuracy();
			if (accuracy != null) {
				questionElement.setAttribute("accuracyThreshold", accuracy);
			}
		}
		
		// Add the body intent
		if (questionElement != null) {
			String intent = q.getIntent();
			if (intent != null) {
				questionElement.setAttribute("intent", intent);
			}
		}

		// Add the label
		if (questionElement != null) {
			Element labelElement = outputXML.createElement("label");
			// String label = q.getQuestion();
			String label = q.getQTextId();
			if (label != null && label.trim().length() != 0) { // Use question text by default

				// Add the reference wrapper
				label = q.getQTextId();
				if (label != null && label.trim().length() != 0) {
					label = "jr:itext('" + label + "')";
					labelElement.setAttribute("ref", label);
					questionElement.appendChild(labelElement);
				}
			}

		}

		// Add the hint
		if (questionElement != null) {
			Element hintElement = outputXML.createElement("hint");
			String hint = q.getInfoTextId();
			if (hint == null || hint.trim().length() == 0) {
				// Use the default hint text
				hint = q.getInfo();
				hintElement.setTextContent(hint);
			} else {
				// Add the reference wrapper
				hint = "jr:itext('" + hint + "')";
				hintElement.setAttribute("ref", hint);
			}

			if (hint != null && hint.trim().length() > 0) {
				questionElement.appendChild(hintElement);
			}
		}

		boolean cascade = false;
		if (useNodesets) {
			// String nodeset = q.getNodeset(true, false, template.getQuestionPaths(),
			// embedExternalSearch);
			String nodeset = UtilityMethods.getNodeset(true, false, template.getQuestionPaths(), embedExternalSearch,
					q.getNodeset(), q.getAppearance(false, null), q.getFormId());
			// Add the itemset
			if (nodeset != null
					&& (!GeneralUtilityMethods.isAppearanceExternalFile(q.getAppearance(true, template.getQuestionPaths()))
							|| embedExternalSearch)) {
				cascade = true;
				Element isElement = outputXML.createElement("itemset");
				String adjustedNodeset = GeneralUtilityMethods.addNodesetFunctions(nodeset, 
						GeneralUtilityMethods.getSurveyParameter("randomize", q.getParameters())); 
				isElement.setAttribute("nodeset", adjustedNodeset);

				Element vElement = outputXML.createElement("value");
				vElement.setAttribute("ref", q.getNodesetValue());
				Element lElement = outputXML.createElement("label");
				lElement.setAttribute("ref", q.getNodesetLabel());

				isElement.appendChild(vElement);
				isElement.appendChild(lElement);
				questionElement.appendChild(isElement);

			}
		}

		// If this is a choice question and it doesn't store it's items in itemset, then
		// add the items
		if (!cascade && questionElement != null && q.getType().startsWith("select")) {
			populateOptions(sd, outputXML, questionElement, q);
		}
		return questionElement;
	}

	/*
	 * Create an instance label element for table list groups
	 */
	public Element getTableListLabelsElement(Connection sd, Document outputXML, Form f, Question q, String parentXPath)
			throws Exception {

		Element labelsElement = null;
		List<Question> questions = f.getQuestions(sd, f.getPath(null));
		boolean inGroup = false;
		for (Question qx : questions) {
			if (qx.getType().equals("begin group") && qx.getName().equals(q.getName())) {
				inGroup = true;
				continue; // Skip the begin group question
			}
			if (inGroup && qx.getType().equals("end group")) {
				inGroup = false;
				break; // Must be done
			}

			if (inGroup) {
				if (qx.getType().startsWith("select")) {
					labelsElement = outputXML.createElement(qx.getName() + "_table_list_labels");
					break; // Only need labels from one of the select questions
				}

			}
		}

		return labelsElement;
	}

	/*
	 * Create a labels element for table list groups
	 */
	public Element populateTableListLabels(Connection sd, Document outputXML, Form f, Question q, String parentXPath)
			throws Exception {

		Element labelsElement = null;
		List<Question> questions = f.getQuestions(sd, f.getPath(null));
		boolean inGroup = false;
		for (Question qx : questions) {
			if (qx.getType().equals("begin group") && qx.getName().equals(q.getName())) {
				inGroup = true;
				continue; // Skip the begin group question
			}
			if (inGroup && qx.getType().equals("end group")) {
				inGroup = false;
				break; // Must be done
			}

			if (inGroup) {
				if (qx.getType().startsWith("select")) {
					labelsElement = outputXML.createElement(qx.getType());

					// Add the reference attribute
					String path = getQuestionReference(template.getQuestionPaths(), f.getId(), qx.getName());
					labelsElement.setAttribute("ref", path + "_table_list_labels");

					// Add the appearance
					labelsElement.setAttribute("appearance", "label");

					populateOptions(sd, outputXML, labelsElement, qx);
					break; // Only need labels from one of the select questions
				}

			}
		}

		return labelsElement;
	}

	/*
	 * Add the options
	 * 
	 * @param outputXML
	 * 
	 * @param parent
	 * 
	 * @param q
	 */
	public void populateOptions(Connection sd, Document outputXML, Element parent, Question q) throws SQLException {

		Collection<Option> options = q.getChoices(sd);
		List<Option> optionList = new ArrayList<Option>(options);

		/*
		 * Sort the options The question sequence number is determined by the order of
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

		for (Option o : optionList) {
			Element optionElement = outputXML.createElement("item");

			Element labelElement = outputXML.createElement("label");
			String label = o.getLabelId();
			if (label == null) {
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
			if (value == null) {
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
	 * 
	 * @param outputXML
	 * 
	 * @param parent
	 * 
	 * @param q
	 */
	public void populateCascadeOptions(Document outputXML, Element parent, CascadeInstance ci) {

		List<Option> optionList = template.getCascadeOptionList(ci.name);

		/*
		 * Sort the options The question sequence number is determined by the order of
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

		for (Option o : optionList) {

			// Add item
			Element itemElement = outputXML.createElement("item");
			parent.appendChild(itemElement);

			// Add label element
			Element labelElement = outputXML.createElement(ci.labelKey);
			String label = o.getLabelId();
			if (label == null) {
				// Use the default label text
				label = o.getLabel();
			}
			labelElement.setTextContent(label);

			// Add value element
			Element valueElement = outputXML.createElement(ci.valueKey);
			String value = o.getValue();
			if (value == null) {
				value = o.getLabel();
			}
			valueElement.setTextContent(value);

			itemElement.appendChild(labelElement);
			itemElement.appendChild(valueElement);

			// Add other elements that are used for selecting relevant values

			if (embedExternalSearch) {

			}
			o.setCascadeKeyValues(); // Set the key value pairs from the filter string
			HashMap<String, String> cvs = o.getCascadeKeyValues();
			List<String> keyList = new ArrayList<String>(cvs.keySet());
			for (String k : keyList) {
				String v = cvs.get(k);
				Element keyElement = outputXML.createElement(k);
				keyElement.setTextContent(v);
				itemElement.appendChild(keyElement);
			}
			itemElement.appendChild(labelElement);
			itemElement.appendChild(valueElement);
		}
	}

	public void populateCSVElements(Document outputXML, Element parent, String filepath) throws Exception {

		BufferedReader br = null;
		File file = new File(filepath);

		try {
			FileReader reader = new FileReader(file);
			br = new BufferedReader(reader);
			CSVParser parser = new CSVParser(localisation);

			// Get Header
			String line = GeneralUtilityMethods.removeBOM(br.readLine());
			String cols[] = parser.parseLine(line);

			while (line != null) {
				line = br.readLine();
				if (line != null && line.trim().length() > 0) {
					String[] values = parser.parseLine(line);
					
					if(values.length == cols.length) {
						Element item = outputXML.createElement("item");
						parent.appendChild(item);
						Element elem = null;
						for (int i = 0; i < cols.length; i++) {
							try {
								elem = outputXML.createElement(cols[i]);
								String v = values[i];
								v = v.replaceAll("'", "");
								elem.setTextContent(v);
								item.appendChild(elem);
							} catch (Exception e) {
								log.log(Level.SEVERE, e.getMessage(), e);
								String msg = localisation.getString("msg_inv_col");
								msg = msg.replaceAll("%s1", cols[i]);
								msg = msg.replaceAll("%s2", file.getName());
								throw new Exception (msg);
							}
						}
					} else {
						log.info("Error: Values length is " + values.length + " and column length is " + cols.length);
					}
				}
			}
		} finally {
			try {br.close();} catch (Exception e) {}
		}
	}
	
	public void populateCSVElementsFromSurvey(Document outputXML, Element parent, SurveyTableManager stm) throws Exception {

		
		PreparedStatement pstmt = null;
		ArrayList<KeyValueSimp> line = null;
		try {
			stm.initData(pstmt, "all", null, null, null, null, null, tz);
			line = stm.getLine();
			while(line != null) {
				// process line
				Element item = outputXML.createElement("item");
				parent.appendChild(item);
				Element elem = null;
				for(KeyValueSimp kv : line) {
					elem = outputXML.createElement(kv.k);
					String v = kv.v.replaceAll("'", "");
					elem.setTextContent(v);
					item.appendChild(elem);
				}
				line = stm.getLine();
				
			}

		} finally {
			if (pstmt != null) try {	pstmt.close();} catch (Exception e) {};
		}
	}

	/*
	 * Get the instance data for an XForm
	 */
	public String getInstanceXml(int sId, String templateName, SurveyTemplate template, String key, String keyval,
			int priKey, boolean simplifyMedia, 
			boolean isWebForms, int taskKey,
			String urlprefix) 
			throws ParserConfigurationException, ClassNotFoundException, SQLException, TransformerException, ApplicationException {

		this.isWebForms = isWebForms;

		String instanceXML = null;

		Connection cResults = null;
		Connection sd = null;

		DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
		DocumentBuilder b = dbf.newDocumentBuilder();
		Document outputXML = b.newDocument();

		Writer outWriter = new StringWriter();
		Result outStream = new StreamResult(outWriter);

		gFilenames = new ArrayList<String>();
		gPaths = new ArrayList<String>();
		gInstanceId = null;

		log.info("Getting instance data: " + templateName + " : " + key + " : " + keyval);
		
		// Get template details
		String firstFormRef = template.getFirstFormRef();
		Form firstForm = template.getForm(firstFormRef);

		// Get database driver and connection to the results database

		String requester = "GetXForm - getInstance";
		cResults = ResultsDataSource.getConnection(requester);
		sd = SDDataSource.getConnection(requester);

		/*
		 * Replace the primary key with the primary key of the record that matches the
		 * passed in key and key value
		 */
		try {
			if (key != null && keyval != null) {
				if (key.equals("prikey")) {
					priKey = Integer.parseInt(keyval);
					if (!priKeyValid(cResults, firstForm, priKey)) {
						priKey = 0;
					}
				} else {
					priKey = getPrimaryKey(sd, cResults, firstForm, key, keyval, sId);
				}
			} else {
				if (!priKeyValid(cResults, firstForm, priKey)) {
					priKey = 0;
				}
			}

			// Generate the XML
			boolean hasData = false;
			if (priKey > 0) {
				hasData = true;
				populateFormData(outputXML, firstForm, priKey, -1, cResults, sd, template, null, sId, templateName, false,
						simplifyMedia, null, -1);
			} else if (key != null && keyval != null) {
				// Create a blank form containing only the key values
				hasData = true;
				populateBlankForm(outputXML, firstForm, sd, template, null, sId, key, keyval, templateName, false);
			} else if(taskKey > 0) {
				// Create a form containing only the initial task data
				hasData = true;
				TaskManager tm = new TaskManager(localisation, tz);
				Instance instance = tm.getInstance(sd, taskKey);
				populateTaskDataForm(outputXML, firstForm, sd, template, null, sId, templateName, instance, urlprefix, true);
			}

			// Write the survey to a string and return it to the calling program
			if (hasData) {
				Transformer transformer = TransformerFactory.newInstance().newTransformer();
				transformer.setOutputProperty(OutputKeys.INDENT, "yes");
				DOMSource source = new DOMSource(outputXML);
				transformer.transform(source, outStream);

				instanceXML = outWriter.toString();
			} else {
				instanceXML = "";
			}
		} finally {
			ResultsDataSource.closeConnection(requester, cResults);
			SDDataSource.closeConnection(requester, sd);
		}

		return instanceXML;
	}

	/*
	 * Getters
	 */
	public String getInstanceId() {
		return gInstanceId;
	}
	
	public HashMap<String, Integer> getRecordCounts() {
		return gRecordCounts;
	}

	public ArrayList<String> getFilenames() {
		return gFilenames;
	}
	
	public ArrayList<String> getMediaPaths() {
		return gPaths;
	}

	public String getSurveyClass() {
		return gSurveyClass;
	}

	/*
	 * Make sure the primary key is valid and can return data
	 */
	boolean priKeyValid(Connection connection, Form firstForm, int priKey) throws ApplicationException, SQLException {

		String table = firstForm.getTableName().replace("'", "''"); // Escape apostrophes
		boolean isValid = false;

		// There is a double check below on whether or not the record has been modified
		// / replaced
		// This was added in version 15.05, at some point it may be safe to remove the
		// check for the "Replaced by " string and _bad
		// Update - allow the returning of modified data.  It is quite reasonable that a task may reference
		// an old source data record. This happens due to multiple assignements.
		// The processing of a submission from this old data is being updated to apply a "merge"
		//String sql = "select count(*) from " + table + " where prikey = ? "
		//		+ "and _modified = 'false' and (_bad = false or (_bad = true and _bad_reason not like 'Replaced by%'));";
		String sql = "select count(*) from " + table + " where prikey = ? ";
				
		PreparedStatement pstmt = connection.prepareStatement(sql);

		ResultSet rs = null;
		try {

			pstmt.setInt(1, priKey);
			log.info("Checking primary key: " + pstmt.toString());

			rs = pstmt.executeQuery();
			if (rs.next()) {
				if (rs.getInt(1) > 0) {
					isValid = true;
				}
			}

		} catch (Exception e) {
			String msg = e.getMessage();
			if (msg.contains("does not exist")) {
				log.info("Exception checking primary key: " + msg);
				// Presumably no data has been uploaded yet - therefore no key but not an error
			} else {
				e.printStackTrace();
			}
		} finally {
			if (pstmt != null) try {	pstmt.close();} catch (Exception e) {};
		}

		return isValid;
	}

	/*
	 * Get the primary key from the passed in key values The key must be in the top
	 * level form
	 */
	int getPrimaryKey(Connection sd, Connection cResults, Form firstForm, String key, String keyval, int sId)
			throws ApplicationException, SQLException {
		int prikey = 0;
		String table = firstForm.getTableName().replace("'", "''"); // Escape apostrophes
		key = key.replace("'", "''"); // Escape apostrophes
		String type = null;
		String keyColumnName = null;

		// Get the key type
		if(key.equals("instanceid")) {
			keyColumnName = key;
			type = "string";
			// Get the latest instance id in the thread
			keyval = GeneralUtilityMethods.getLatestInstanceId(cResults, table, keyval);
		} else {
			List<Question> questions = firstForm.getQuestions(sd, firstForm.getPath(null));
			for (int i = 0; i < questions.size(); i++) {
				Question q = questions.get(i);
				if (q.getName().toLowerCase().trim().equals(key)) {
					type = q.getType();
					keyColumnName = q.getColumnName(false);
					break;
				}
			}
		}
		if (type == null) {
			// Check the prelaods
			ArrayList<MetaItem> meta = GeneralUtilityMethods.getPreloads(sd, sId);
			for(MetaItem mi : meta) {
				if(mi.isPreload && mi.name.equals(key)) {
					type = mi.type;
					keyColumnName = mi.columnName;
					break;
				}
			}
		}

		if (type == null) {
			throw new ApplicationException("Key: " + key + " not found");
		}
		String sql = "select prikey from " + table + " where " + keyColumnName + " = ? "
				+ "and _modified = 'false' and (_bad = false or (_bad = true and _bad_reason not like 'Replaced by%'));";

		PreparedStatement pstmt = cResults.prepareStatement(sql);
		if (type.equals("string") || type.equals("barcode")) {
			pstmt.setString(1, keyval);
		} else if (type.equals("int")) {
			pstmt.setInt(1, Integer.parseInt(keyval));
		} else if (type.equals("decimal")) {
			pstmt.setFloat(1, Float.parseFloat(keyval));
		} else {
			throw new ApplicationException(
					"Invalid question type: " + type + ", allowed values are text, barcode, integer, decimal");
		}

		ResultSet rs = null;
		boolean hasKey;
		try {
			log.info("Getting primary key: " + pstmt.toString());
			rs = pstmt.executeQuery();
			hasKey = rs.next();
		} catch (Exception e) {
			String msg = e.getMessage();
			if (msg.contains("does not exist")) {
				// Presumably no data has been uploaded yet - therefore no key but not an error
			} else {
				e.printStackTrace();
			}
			hasKey = false;
			// throw new ApplicationException("Record not found for key: " + key + " and
			// value " + keyval);
		}
		if (hasKey) {
			prikey = rs.getInt(1);
			if (rs.next()) {
				throw new ApplicationException("Multiple records found for key: " + key + " and value " + keyval);
			}

		} else {
			log.info("Key " + key + " and value " + keyval + " not found");
			// throw new ApplicationException("Record not found for key: " + key + " and
			// value " + keyval);
		}

		return prikey;
	}

	/*
	 * Create a blank form a) populated only by the key data or b) as a java rosa
	 * template
	 * 
	 * @param outputDoc
	 */
	public void populateBlankForm(Document outputDoc, Form form, Connection sd, SurveyTemplate template,
			Element parentElement, int sId, String key, String keyval, String survey_ident, boolean isTemplate)
					throws SQLException {

		List<Results> record = new ArrayList<Results>();

		List<Question> questions = form.getQuestions(sd, form.getPath(null));
		for (Question q : questions) {

			String qName = q.getName();
			String qType = q.getType();

			String value = "";
			if (qName.equals(key)) {
				value = keyval;
			}

			if (qType.equals("begin repeat") || qType.equals("geolinestring") || qType.equals("geopolygon")) {

				Form subForm = template.getSubForm(form, q);

				if (subForm != null) {
					record.add(new Results(qName, subForm, null, false, false, false, null, q.getParameters(), false));
				}

			} else if (qType.equals("begin group")) {

				record.add(new Results(qName, null, null, true, false, false, null, q.getParameters(), false));

			} else if (qType.equals("end group")) {

				record.add(new Results(qName, null, null, false, true, false, null, q.getParameters(), false));

			} else {

				record.add(new Results(qName, null, value, false, false, false, null, null, false));
			}
		}

		Element currentParent = outputDoc.createElement(form.getName()); // Create a form element

		Results item = null;
		Stack<Element> elementStack = new Stack<Element>(); // Store the elements for non repeat groups
		for (int j = 0; j < record.size(); j++) {

			item = record.get(j);

			if (item.subForm != null) {
				populateBlankForm(outputDoc, item.subForm, sd, template, currentParent, sId, key, keyval, survey_ident,
						isTemplate);
			} else if (item.begin_group) {

				Element childElement = null;
				childElement = outputDoc.createElement(item.name);
				currentParent.appendChild(childElement);

				elementStack.push(currentParent);
				currentParent = childElement;

			} else if (item.end_group) {

				currentParent = elementStack.pop();

			} else { // Question

				// Create the question element
				Element childElement = null;
				childElement = outputDoc.createElement(item.name);
				childElement.setTextContent(item.value);
				currentParent.appendChild(childElement);
			}

		}

		// Append this new form to its parent (if the parent is null append to output
		// doc)
		if (parentElement != null) {
			if (isTemplate) {
				currentParent.setAttribute("jr:template", "");
			}
			parentElement.appendChild(currentParent);
		} else {
			currentParent.setAttribute("id", survey_ident);
			outputDoc.appendChild(currentParent);
		}

	}
	
	/*
	 * Create a form poplulated with the initial data supplied in a task
	 * 
	 * @param outputDoc
	 */
	public void populateTaskDataForm(Document outputDoc, Form form, Connection sd, SurveyTemplate template,
				Element parentElement, int sId, String survey_ident, 
				Instance instance,
				String urlprefix,
				boolean isTopLevel)
			throws SQLException {

		List<Results> record = new ArrayList<Results>();

		List<Question> questions = form.getQuestions(sd, form.getPath(null));
		if(isTopLevel) {		// Add meta group
			Question q = new Question();
			q.setType("begin group");
			q.setName("meta");
			questions.add(q);
			
			q = new Question();
			q.setType("text");
			q.setName("instanceID");
			questions.add(q);
			
			q = new Question();
			q.setType("text");
			q.setName("instanceName");
			questions.add(q);
			
			q = new Question();
			q.setType("end group");
			q.setName("meta");
			questions.add(q);
			
		}
		for (Question q : questions) {

			String qName = q.getName();
			String qType = q.getType();

			// Set the value from the instance data
			String value = "";
			if(instance != null) {
				if(qType.equals("geopoint")  || qType.equals("geoshape") || qType.equals("geotrace")) {

					if(qType.equals("geopoint") && instance.point_geometry != null) {		
						value = instance.point_geometry.getAsOdk();
					} else if(qType.equals("geoshape") && instance.polygon_geometry != null) {
						value = GeneralUtilityMethods.getOdkPolygon(instance.polygon_geometry);
					} else if(qType.equals("geotrace") && instance.line_geometry != null) {
						value = GeneralUtilityMethods.getOdkLine(instance.line_geometry);
					}
					
				} else {
					String qValue = instance.values.get(qName); 
					if(qValue != null) {
						if(qType.equals("image")  || qType.equals("audio") || qType.equals("video")) {
							// Hack for special situation on localhost
							if(urlprefix.equals("http://localhost/")) {
								urlprefix = "https://localhost/";
							}
							if(qValue.startsWith(urlprefix)) {
								int idx = qValue.lastIndexOf("/");
								if(qValue.length() > idx) {
									value = qValue.substring(idx + 1);
									gFilenames.add(value);
								} else {
									value = qValue;
								}
								String path = qValue.substring(urlprefix.length());	// Local image remove prefix
								gPaths.add(path);
							} else {
								value = qValue;
							}
						} else {
							value = qValue;
						}
					}
				}
			}

			if (qType.equals("begin repeat") || qType.equals("geolinestring") || qType.equals("geopolygon")) {

				Form subForm = template.getSubForm(form, q);

				if (subForm != null) {
					record.add(new Results(qName, subForm, null, false, false, false, null, q.getParameters(), false));
				}

			} else if (qType.equals("begin group")) {

				record.add(new Results(qName, null, null, true, false, false, null, q.getParameters(), false));

			} else if (qType.equals("end group")) {

				record.add(new Results(qName, null, null, false, true, false, null, q.getParameters(), false));

			} else {

				record.add(new Results(qName, null, value, false, false, false, null, null, false));
			}
		}

		Element currentParent = outputDoc.createElement(form.getName()); // Create a form element

		Results item = null;
		Stack<Element> elementStack = new Stack<Element>(); // Store the elements for non repeat groups
		for (int j = 0; j < record.size(); j++) {

			item = record.get(j);

			if (item.subForm != null) {
				Instance iSub = null;
				if(instance != null) {
					ArrayList<Instance> subInstanceList = instance.repeats.get(item.name);
					if(subInstanceList.size() > j) {
						iSub = subInstanceList.get(j);
					}
				}
				populateTaskDataForm(outputDoc, item.subForm, sd, template, currentParent, sId, 
						survey_ident, iSub, urlprefix, false);		

				Element childElement = null;
				childElement = outputDoc.createElement(item.name);
				currentParent.appendChild(childElement);

				elementStack.push(currentParent);
				currentParent = childElement;

			} else { // Question

				// Create the question element
				Element childElement = null;
				childElement = outputDoc.createElement(item.name);
				childElement.setTextContent(item.value);
				currentParent.appendChild(childElement);
			}

		}

		// Append this new form to its parent (if the parent is null append to output doc)
		if (parentElement != null) {
			parentElement.appendChild(currentParent);
		} else {
			currentParent.setAttribute("id", survey_ident);
			outputDoc.appendChild(currentParent);
		}

	}

	/*
	 * Add the data for this form
	 * 
	 * @param outputDoc
	 */
	public void populateFormData(Document outputDoc, Form form, int id, int parentId, Connection cResults, Connection sd,
			SurveyTemplate template, Element parentElement, int sId, String survey_ident, boolean isFirstSubForm,
			boolean simplifyMedia, String order, int count) throws SQLException {

		List<List<Results>> results = null;
		if (GeneralUtilityMethods.tableExists(cResults, form.getTableName())) {
			results = getResults(form, id, parentId, cResults, sd, template, simplifyMedia, sId, order, count); // Add the child elements
		} else {
			results = new ArrayList<List<Results>>(); // Create an empty list
		}

		/*
		 * Store a link between the form name and the number of records
		 * This is used to calculate the repeat count of reference forms
		 */
		gRecordCounts.put(form.getName(), results.size());
		
		boolean generatedTemplate = false;
		// For each record returned from the database add a form element
		for (int i = 0; i < results.size(); i++) {

			Element currentParent = outputDoc.createElement(form.getName()); // Create a form element
			List<Results> record = results.get(i);

			Results priKey = record.get(0); // Get the primary key

			/*
			 * Add data for the remaining questions
			 */
			Results item = null;
			Stack<Element> elementStack = new Stack<Element>(); // Store the elements for non repeat groups
			for (int j = 1; j < record.size(); j++) {

				item = record.get(j);

				if (item.subForm != null) {
					count = -1;
					order = GeneralUtilityMethods.getSurveyParameter("instance_order", item.parameters);
					String c = GeneralUtilityMethods.getSurveyParameter("instance_count", item.parameters);

					if(c != null) {
						try {
							count = Integer.parseInt(c);
						} catch (Exception e) {
							// ignore
						}
					}
					

					boolean needTemplate = (!generatedTemplate && (parentElement == null));
					populateFormData(outputDoc, item.subForm, -1, Integer.parseInt(priKey.value), cResults, sd, template,
							currentParent, sId, survey_ident, needTemplate, simplifyMedia, order, count);
					
				} else if (item.begin_group) {
					Element childElement = null;
					childElement = outputDoc.createElement(item.name);
					currentParent.appendChild(childElement);

					elementStack.push(currentParent);
					currentParent = childElement;

				} else if (item.end_group) {

					currentParent = elementStack.pop();

				} else { // Question

					if (item.name != null && item.name.toLowerCase().equals("instanceid")) {
						gInstanceId = item.value;
					} else if (item.media && item.filename != null && !item.filename.equals("null")) {
						gFilenames.add(item.filename);
						gPaths.add(item.value);
					}

					// Create the question element
					Element childElement = null;
					childElement = outputDoc.createElement(item.name);
					// Escape any single quotes if this is called by webforms as the output is
					// stored as a string with single quotes around it
					String escValue = item.value;
					if (isWebForms && item.value != null) {
						escValue = item.value.replace("'", "\\\'");
					}

					if(!item.isStartPreload) {		// Don't add start time as this needs to be reset when editing the form instance
						childElement.setTextContent(escValue);
					}
					currentParent.appendChild(childElement);
				}

			}
			// Append this new form to its parent (if the parent is null append to output
			// doc)
			if (parentElement != null) {
				parentElement.appendChild(currentParent);
			} else {
				currentParent.setAttribute("id", survey_ident);
				outputDoc.appendChild(currentParent);
			}
		}

	}

	/*
	 * Get the results
	 * 
	 * @param form
	 * 
	 * @param id
	 * 
	 * @param parentId
	 */
	List<List<Results>> getResults(Form form, int id, int parentId, Connection cResults, Connection sd,
			SurveyTemplate template, boolean simplifyMedia, int sId, String order, int count) throws SQLException {

		List<List<Results>> output = new ArrayList<List<Results>>();

		Form processForm = null;		
		List<Question> questions = form.getQuestions(sd, form.getPath(null));
		Gson gson=  new GsonBuilder().disableHtmlEscaping().setDateFormat("yyyy-MM-dd").create();
		
		/*
		 * If this is a reference form then get the form that has the data
		 */
		boolean isReference = form.getReference();
		
		log.info("Get results for form: " + form.getId() + " name : " + form.getName() + " reference: " + isReference);
		
		if(isReference) {
			/*
			 * Get the form that has the data referred to from this reference form
			 */
			List<Form> forms = template.getAllForms();
			for(Form f : forms) {
				if(f.getTableName().equals(form.getTableName()) && !f.getReference()) {
					processForm = f;
					break;
				}
			}
		} else {
			processForm = form;
		}
		

		/*
		 * Retrieve the results record from the database (excluding uncompressed select questions)
		 */
		StringBuffer sql = new StringBuffer("select prikey");

		if(parentId <= 0) {
			// Add Meta
			sql.append(",instanceID, instanceName");
			ArrayList<MetaItem> preloads = GeneralUtilityMethods.getPreloads(sd, sId);
			for(MetaItem mi : preloads) {
				if(mi.isPreload) {
					sql.append(",").append(mi.columnName);
				}
			}
		}

		/*
		 * Get the data
		 */
		boolean hasPoint = false;
		boolean hasPointAltitude = false;
		for (Question q : questions) {
			String col = null;

			// Backward compatability - Ignore Meta  questions 
			if(GeneralUtilityMethods.isMetaQuestion(q.getName())) {
				continue;
			}
			
			if (q.isPublished() || isReference) {		// Referenced questions are never published
				if (template.getSubForm(processForm, q) == null) {
					// This question is not a place holder for a subform
					if (q.getSource() != null) { // Ignore questions with no source, these can only be dummy questions that indicate the position of a subform

						String qType = q.getType();
						if (qType.equals("geopoint") || qType.equals("geoshape") || qType.equals("geotrace")) {
							col = "ST_AsGeoJson(" + q.getColumnName(isReference) + ")";
							if(qType.equals("geopoint")) {
								hasPoint = true;
							}
						} else if (qType.equals("select") && !q.isCompressed()) {
							continue; 
						} else {
							col = q.getColumnName(isReference);
						}

						if(!isReference) {
							sql.append(",").append(col);			// Normal data question
						} else if(isReference) {
							if(q.getName().startsWith("_")) {
								// Reference question, check that the column exists as we cannot rely "on publish"
								if(GeneralUtilityMethods.hasColumn(cResults, processForm.getTableName(), col)) {
									sql.append(",").append(col);
								} else {
									sql.append(",").append("''");
								}
							} else {
								sql.append(",").append("''");	// Read only reference questions
							}
						}
					}
				}
			}

		}
		/*
		 * Get geometry altitude and accuracy of they are available
		 */
		if(hasPoint) {
			if(GeneralUtilityMethods.hasColumn(cResults, processForm.getTableName(), "the_geom_alt")) {
				sql.append(",the_geom_alt, the_geom_acc");
				hasPointAltitude = true;
			}
		}
		
		sql.append(" from ").append(processForm.getTableName());
		if (id != -1) {
			sql.append(" where prikey=").append(id);
		} else {
			sql.append(" where parkey=").append(parentId);
		}
		//sql.append(" and _bad = false");		Allow bad data when getting the data for an existing record

		if(order != null && order.equals("reverse")) {
			sql.append(" order by prikey desc");
		} else {
			sql.append(" order by prikey asc");
		}

		if(count >= 0) {
			sql.append(" limit ").append(count);
		}

		PreparedStatement pstmt = cResults.prepareStatement(sql.toString());
		log.info("Get data for instance XML: " + pstmt.toString());
		ResultSet resultSet = pstmt.executeQuery();

		// For each record returned from the database add the data values to the
		// instance
		while (resultSet.next()) {

			List<Results> record = new ArrayList<Results>();

			String priKey = resultSet.getString(1);
			record.add(new Results("prikey", null, priKey, false, false, false, null, null, false));

			/*
			 * Add data for the remaining questions
			 */
			int index = 2;

			if(parentId <= 0) {
				// Add Meta
				record.add(new Results("meta", null, null, true, false, false, null, null, false));
				record.add(new Results("instanceID", null, resultSet.getString(index++), false, false, false, null, null, false));	
				record.add(new Results("instanceName", null, resultSet.getString(index++), false, false, false, null, null, false));	
				record.add(new Results("meta_groupEnd", null, null, false, true, false, null, null, false));

				ArrayList<MetaItem> preloads = GeneralUtilityMethods.getPreloads(sd, sId);
				for(MetaItem mi : preloads) {
					if(mi.isPreload) {
						String value = resultSet.getString(index++);
						boolean isStartPreload = false;
						if(mi.sourceParam != null && mi.sourceParam.equals("start")) {
							isStartPreload = true;
						}
						record.add(new Results(mi.name, null, value, false, false, false, null, null, isStartPreload));	
					}
				}

			}

			for (Question q : questions) {

				// Backward compatability - Ignore Meta  questions 
				if(GeneralUtilityMethods.isMetaQuestion(q.getName())) {
					continue;
				}
				
				String qName = q.getName();
				String qType = q.getType();
				String qSource = q.getSource();

				if (qType.equals("begin repeat") || qType.equals("geolinestring") || qType.equals("geopolygon")) {
					Form subForm = template.getSubForm(processForm, q);

					if (subForm != null) {
						record.add(new Results(qName, subForm, null, false, false, false, null, q.getParameters(), false));
					}

				} else if (qType.equals("begin group")) {

					record.add(new Results(qName, null, null, true, false, false, null, q.getParameters(), false));

				} else if (qType.equals("end group")) {

					record.add(new Results(qName, null, null, false, true, false, null, q.getParameters(), false));

				} else if (qType.equals("select") && !q.isCompressed()) { // Get the data from all the option columns

					String optValue = "";
					if (q.isPublished() || isReference) { // Get the data from the table if this question has been published
						String sqlSelect = "select ";
						List<Option> options = new ArrayList<Option>(q.getValidChoices(sd));
						UtilityMethods.sortOptions(options); // Order within an XForm is not actually required, this is
						// just for consistency of reading

						boolean hasColumns = false;
						for (Option option : options) {
							if (hasColumns) {
								sqlSelect += ",";
							}
							sqlSelect += q.getColumnName(isReference) + "__" + option.getColumnName();
							hasColumns = true;
						}
						sqlSelect += " from " + processForm.getTableName() + " where prikey=" + priKey + ";";

						pstmt = cResults.prepareStatement(sqlSelect);
						log.info(pstmt.toString());
						ResultSet resultSetOptions = pstmt.executeQuery();
						resultSetOptions.next(); // There will only be one record

						hasColumns = false;
						for (Option option : options) {
							String opt = q.getColumnName(isReference) + "__" + option.getColumnName();
							boolean optSet = resultSetOptions.getBoolean(opt);
							log.fine("Option " + opt + ":" + resultSetOptions.getString(opt));
							if (optSet) {
								if (hasColumns) {
									optValue += " ";
								}
								optValue += option.getValue();
								hasColumns = true;
							}
						}
					}

					record.add(new Results(qName, null, optValue, false, false, false, null, q.getParameters(), false));

				} else if (GeneralUtilityMethods.isAttachmentType(qType)) { // Get the file name

					String value = null;
					if (q.isPublished() || isReference) { // Get the data from the table if this question has been published
						value = resultSet.getString(index);
					}
					String filename = null;
					if (value != null) {
						int idx = value.lastIndexOf('/');
						if (idx > -1) {
							filename = value.substring(idx + 1);
						}
						if (filename != null && !filename.equals("null")) {
							gFilenames.add(filename);
							gPaths.add(value);
						}
					}
					if (simplifyMedia) {
						value = filename;
					}

					record.add(new Results(qName, null, value, false, false, false, filename, q.getParameters(), false));

					if (q.isPublished() || isReference) {
						index++;
					}

				} else if (qSource != null) {

					String value = null;
					if (q.isPublished() || isReference) { // Get the data from the table if this question has been published
						value = resultSet.getString(index);
						if(/*isWebForms && */ value != null && q.getType().equals("dateTime")) {
							// Add the T separator 
							value = value.replace(' ',  'T');
							value = GeneralUtilityMethods.workAroundJava8bug00(value);
						}
					}

					if (value != null && qType.equals("geopoint")) {
						Point p = gson.fromJson(value, Point.class);
						if(hasPointAltitude) {
							log.info("Altitude: " + resultSet.getDouble("the_geom_alt"));
							log.info("Accuracy: " + resultSet.getDouble("the_geom_acc"));
							p.altitude = resultSet.getDouble("the_geom_alt");
							p.accuracy = resultSet.getDouble("the_geom_acc");
						}
						value = p.getAsOdk();
						log.info("Point value: " + value);
						
					} else if (value != null && qType.equals("geoshape")) {
						Polygon p = gson.fromJson(value, Polygon.class);
						value = GeneralUtilityMethods.getOdkPolygon(p);
					} else if (value != null && qType.equals("geotrace")) {
						Line l = gson.fromJson(value, Line.class);
						value = GeneralUtilityMethods.getOdkLine(l);
					}
					

					// Ignore data not provided by user
					if (!qSource.equals("user")) {
						value = "";
					}

					record.add(new Results(qName, null, value, false, false, false, null, q.getParameters(), false));

					if (q.isPublished() || isReference) {
						index++;
					}
				}
			}
			output.add(record);
		}

		return output;
	}

	/*
	 * Get the question reference This adds the form id to geometry questions since
	 * these questions do not have unique names and the path is found through f_id +
	 * qname
	 */
	private String getQuestionReference(HashMap<String, String> paths, int fId, String qName) {
		String key = qName;

		if (key.equals("the_geom")) {
			key = fId + key;
		}

		return paths.get(key);
	}

	/*
	 * Work out the range data type from its parameters
	 */
	private String getDataTypeFromRange(ArrayList<KeyValueSimp> parameters) {

		String dataType = "integer";

		if(parameters != null) {
			for(KeyValueSimp kv : parameters) {			
				if(kv.k.equals("start") || kv.k.equals("end") || kv.k.equals("step")) {
					if(kv.v.indexOf(".") >= 0) {
						dataType = "decimal";
						break;
					} 
				}
			}
		} 
		return dataType;
	}

}
