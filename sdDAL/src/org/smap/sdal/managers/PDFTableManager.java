package org.smap.sdal.managers;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Type;
import java.net.MalformedURLException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.smap.sdal.Utilities.GeneralUtilityMethods;
import org.smap.sdal.Utilities.PdfPageSizer;
import org.smap.sdal.Utilities.PdfUtilities;
import org.smap.sdal.model.DisplayItem;
import org.smap.sdal.model.KeyValue;
import org.smap.sdal.model.Label;
import org.smap.sdal.model.ManagedFormConfig;
import org.smap.sdal.model.Option;
import org.smap.sdal.model.Result;
import org.smap.sdal.model.TableColumn;
import org.smap.sdal.model.User;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.itextpdf.text.BadElementException;
import com.itextpdf.text.BaseColor;
import com.itextpdf.text.Chunk;
import com.itextpdf.text.Document;
import com.itextpdf.text.DocumentException;
import com.itextpdf.text.Font;
import com.itextpdf.text.Font.FontFamily;
import com.itextpdf.text.FontFactory;
import com.itextpdf.text.Image;
import com.itextpdf.text.List;
import com.itextpdf.text.ListItem;
import com.itextpdf.text.PageSize;
import com.itextpdf.text.Paragraph;
import com.itextpdf.text.pdf.BarcodeQRCode;
import com.itextpdf.text.pdf.BaseFont;
import com.itextpdf.text.pdf.PdfPCell;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfWriter;
import com.itextpdf.tool.xml.ElementList;
import com.itextpdf.tool.xml.XMLWorker;
import com.itextpdf.tool.xml.XMLWorkerHelper;
import com.itextpdf.tool.xml.css.CssFile;
import com.itextpdf.tool.xml.css.StyleAttrCSSResolver;
import com.itextpdf.tool.xml.html.Tags;
import com.itextpdf.tool.xml.parser.XMLParser;
import com.itextpdf.tool.xml.pipeline.css.CSSResolver;
import com.itextpdf.tool.xml.pipeline.css.CssResolverPipeline;
import com.itextpdf.tool.xml.pipeline.end.ElementHandlerPipeline;
import com.itextpdf.tool.xml.pipeline.html.HtmlPipeline;
import com.itextpdf.tool.xml.pipeline.html.HtmlPipelineContext;

/*****************************************************************************

This file is part of SMAP.

SMAP is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

SMAP is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with SMAP.  If not, see <http://www.gnu.org/licenses/>.

 ******************************************************************************/

/*
 * Manage the table that stores details on the forwarding of data onto other systems
 */
public class PDFTableManager {
	
	private static Logger log =
			 Logger.getLogger(PDFTableManager.class.getName());
	
	public static Font Symbols = null;
	public static Font defaultFont = null;
	private static final String DEFAULT_CSS = "/smap/bin/resources/css/default_pdf.css";
	private static int NUMBER_QUESTION_COLS = 10;
	
	Font font = new Font(FontFamily.HELVETICA, 10);
    Font fontbold = new Font(FontFamily.HELVETICA, 10, Font.BOLD);

	private class Parser {
		XMLParser xmlParser = null;
		ElementList elements = null;
	}
	
	private class PdfColumn {
		String human_name;
		int dataIndex;
		boolean barcode = false;
		String type;
		
		public PdfColumn(ResourceBundle localisation, int dataIndex, String n, boolean barcode, String type) {
			this.dataIndex = dataIndex;
			this.human_name = n;		
			this.barcode = barcode;	
			this.type = type;
		}
		
		// Return the width of this column
		public int getWidth() {
			int width = 256 * 20;		// 20 characters is default
			return width;
		}

	}
	int marginLeft = 50;
	int marginRight = 50;
	int marginTop_1 = 130;
	int marginBottom_1 = 100;
	int marginTop_2 = 50;
	int marginBottom_2 = 100;
	

	
	private class GlobalVariables {																// Level descended in form hierarchy
		//HashMap<String, Integer> count = new HashMap<String, Integer> ();		// Record number at a location given by depth_length as a string
		int [] cols = {NUMBER_QUESTION_COLS};	// Current Array of columns
		boolean hasAppendix = false;
		
		// Map of questions that need to have the results of another question appended to their results in a pdf report
		HashMap <String, ArrayList<String>> addToList = new HashMap <String, ArrayList<String>>();
	}

	/*
	 * Call this function to create a PDF
	 * Return a suggested name for the PDF file derived from the results
	 */
	public void createPdf(
			Connection sd,
			OutputStream outputStream, 
			ArrayList<ArrayList<KeyValue>> dArray, 
			ManagedFormConfig mfc,
			ResourceBundle localisation, 
			String tz,
			boolean landscape,
			String remoteUser,
			String basePath,
			String title,
			String project
			) {

		User user = null;
		UserManager um = new UserManager();
		
		try {
			
			user = um.getByIdent(sd, remoteUser);
			
			// Get fonts and embed them
			String os = System.getProperty("os.name");
			log.info("Operating System:" + os);
			
			if(os.startsWith("Mac")) {
				FontFactory.register("/Library/Fonts/fontawesome-webfont.ttf", "Symbols");
				FontFactory.register("/Library/Fonts/Arial Unicode.ttf", "default");
			} else if(os.indexOf("nix") >= 0 || os.indexOf("nux") >= 0 || os.indexOf("aix") > 0) {
				// Linux / Unix
				FontFactory.register("/usr/share/fonts/truetype/fontawesome-webfont.ttf", "Symbols");
				FontFactory.register("/usr/share/fonts/truetype/ttf-dejavu/DejaVuSans.ttf", "default");
			}
			
			Symbols = FontFactory.getFont("Symbols", BaseFont.IDENTITY_H, 
				    BaseFont.EMBEDDED, 12); 
			defaultFont = FontFactory.getFont("default", BaseFont.IDENTITY_H, 
				    BaseFont.EMBEDDED, 10); 

			
			ArrayList<PdfColumn> cols = getPdfColumnList(mfc, dArray, localisation);
			ArrayList<String> tableHeader = new ArrayList<String> ();
			for(PdfColumn col : cols) {
				tableHeader.add(col.human_name);
			}
			/*
			 * Create a PDF without the stationary
			 */				
			PdfWriter writer = null;
				
			/*
			 * If we need to add a letter head then create document in two passes, the second pass adds the letter head
			 * Else just create the document directly in a single pass
			 */
			Parser parser = getXMLParser();
			
			// Step 1 - Create the underlying document as a byte array
			Document document = null;
			if(landscape) {
				document = new Document(PageSize.A4.rotate());
			} else {
				document = new Document(PageSize.A4);
			}
			document.setMargins(marginLeft, marginRight, marginTop_1, marginBottom_1);
			writer = PdfWriter.getInstance(document, outputStream);
				
			writer.setInitialLeading(12);	
			writer.setPageEvent(new PdfPageSizer(title, project, 
					user, basePath, 
					tableHeader,
					marginLeft, marginRight, marginTop_2, marginBottom_2)); 
			
			document.open();
			document.add(new Chunk(""));	// Ensure there is something in the page so at least a blank document will be created
			processResults(parser, document, dArray, cols, basePath);
			document.close();
				
			
		} catch (SQLException e) {
			log.log(Level.SEVERE, "SQL Error", e);
			
		}  catch (Exception e) {
			log.log(Level.SEVERE, "Exception", e);
			
		}
	
	}
	
	
	private class UserSettings {
		String title;
		String license;
	}
	
	
	/*
	 * Get an XML Parser
	 */
	private Parser getXMLParser() {
		
		Parser parser = new Parser();
		
        // CSS
		 CSSResolver cssResolver = new StyleAttrCSSResolver();
		 try {
			 CssFile cssFile = XMLWorkerHelper.getCSS( new FileInputStream(DEFAULT_CSS));
		     cssResolver.addCss(cssFile);
		 } catch(Exception e) {
			 log.log(Level.SEVERE, "Failed to get CSS file", e);
			 cssResolver = XMLWorkerHelper.getInstance().getDefaultCssResolver(true);
		 }
 
        // HTML
        HtmlPipelineContext htmlContext = new HtmlPipelineContext(null);
        htmlContext.setTagFactory(Tags.getHtmlTagProcessorFactory());
        htmlContext.autoBookmark(false);
 
        // Pipelines
        parser.elements = new ElementList();
        ElementHandlerPipeline end = new ElementHandlerPipeline(parser.elements, null);
        HtmlPipeline html = new HtmlPipeline(htmlContext, end);
        CssResolverPipeline css = new CssResolverPipeline(cssResolver, html);
 
        // XML Worker
        XMLWorker worker = new XMLWorker(css, true);        
        parser.xmlParser = new XMLParser(worker);
        
        return parser;
		
	}
	
	/*
	 * Process the results and write to a table
	 */
	private void processResults(
			Parser parser,
			Document document,  
			ArrayList<ArrayList<KeyValue>> dArray, 
			ArrayList<PdfColumn> cols,
			String basePath) throws DocumentException, IOException {
		
	
		for(int index = 0; index < dArray.size(); index++) {
			
			ArrayList<KeyValue> record = dArray.get(index);
			PdfPTable newTable = processRow(parser, record, cols, basePath);
			newTable.setWidthPercentage(100);
			document.add(newTable);
			
		}
		
		return;
	}
	
	
	/*
	 * Add the table row to the document
	 */
	PdfPTable processRow(Parser parser, 
			ArrayList<KeyValue> record, 
			ArrayList<PdfColumn> cols, 
			String basePath) throws BadElementException, MalformedURLException, IOException {

		PdfPTable table = new PdfPTable(cols.size());	
		
		for(PdfColumn col : cols) {
			
			if(col.dataIndex >= 0) {
				PdfPCell cell = addDisplayItem(parser, record.get(col.dataIndex), basePath, col.barcode, col.type);
				cell.setBorderColor(BaseColor.LIGHT_GRAY);
				
				table.addCell(cell);
			}

		}
		return table;
	}
	


	/*
	 * Get the columns for the Pdf file
	 */
	private ArrayList<PdfColumn> getPdfColumnList(ManagedFormConfig mfc, 
			ArrayList<ArrayList<KeyValue>> dArray, 
			ResourceBundle localisation) {
		
		ArrayList<PdfColumn> cols = new ArrayList<PdfColumn> ();
		ArrayList<KeyValue> record = null;
		
		if(dArray.size() > 0) {
			 record = dArray.get(0);
		}
		
		for(int i = 0; i < mfc.columns.size(); i++) {
			TableColumn tc = mfc.columns.get(i);
			if(!tc.hide && tc.include) {
				int dataIndex = -1;
				if(record != null) {
					dataIndex = getDataIndex(record, tc.humanName);
				}
				cols.add(new PdfColumn(localisation, dataIndex, tc.humanName, tc.barcode, tc.type));
			}
		}
	
		
		return cols;
	}

	/*
	 * Get the index into the data set for a column
	 */
	private int getDataIndex(ArrayList<KeyValue> record, String name) {
		int idx = -1;
		
		for(int i = 0; i < record.size(); i++) {
			if(record.get(i).k.equals(name)) {
				idx = i;
				break;
			}
		}
		return idx;
	}
	
	/*
	 * Get the number of blank repeats to generate
	 */
	int getBlankRepeats(String appearance) {
		int repeats = 1;
		
		if(appearance != null) {
			String [] appValues = appearance.split(" ");
			if(appearance != null) {
				for(int i = 0; i < appValues.length; i++) {
					if(appValues[i].startsWith("pdfrepeat")) {
						String [] parts = appValues[i].split("_");
						if(parts.length >= 2) {
							repeats = Integer.valueOf(parts[1]);
						}
						break;
					}
				}
			}
		}
					
		return repeats;
	}
	/*
	 * Set the attributes for this question from keys set in the appearance column
	 */
	void setQuestionFormats(String appearance, DisplayItem di) {
	
		if(appearance != null) {
			String [] appValues = appearance.split(" ");
			if(appearance != null) {
				for(int i = 0; i < appValues.length; i++) {
					if(appValues[i].startsWith("pdflabelbg")) {
						setColor(appValues[i], di, true);
					} else if(appValues[i].startsWith("pdfvaluebg")) {
						setColor(appValues[i], di, false);
					} else if(appValues[i].startsWith("pdflabelw")) {
						setWidths(appValues[i], di);
					} else if(appValues[i].startsWith("pdfheight")) {
						setHeight(appValues[i], di);
					} else if(appValues[i].startsWith("pdfspace")) {
						setSpace(appValues[i], di);
					} else if(appValues[i].equals("pdflabelcaps")) {
						di.labelcaps = true;
					} else if(appValues[i].equals("pdflabelbold")) {
						di.labelbold = true;
					}
				}
			}
		}
	}
	
	/*
	 * Get the color values for a single appearance value
	 * Format is:  xxxx_0Xrr_0Xgg_0xbb
	 */
	void setColor(String aValue, DisplayItem di, boolean isLabel) {
		
		di.labelbg = null;
		BaseColor color = null;
		
		String [] parts = aValue.split("_");
		if(parts.length >= 4) {
			if(parts[1].startsWith("0x")) {
				color = new BaseColor(Integer.decode(parts[1]), 
						Integer.decode(parts[2]),
						Integer.decode(parts[3]));
			} else {
				color = new BaseColor(Integer.decode("0x" + parts[1]), 
					Integer.decode("0x" + parts[2]),
					Integer.decode("0x" + parts[3]));
			}
		}
		
		if(isLabel) {
			di.labelbg = color;
		} else {
			di.valuebg = color;
		}

	}
	
	/*
	 * Set the widths of the label and the value
	 * Appearance is:  pdflabelw_## where ## is a number from 0 to 10
	 */
	void setWidths(String aValue, DisplayItem di) {
		
		String [] parts = aValue.split("_");
		if(parts.length >= 2) {
			di.widthLabel = Integer.valueOf(parts[1]);   		
		}
		
		// Do bounds checking
		if(di.widthLabel < 0 || di.widthLabel > 10) {
			di.widthLabel = 5;		
		}
		
	}
	
	/*
	 * Set the height of the value
	 * Appearance is:  pdfheight_## where ## is the height
	 */
	void setHeight(String aValue, DisplayItem di) {
		
		String [] parts = aValue.split("_");
		if(parts.length >= 2) {
			di.valueHeight = Double.valueOf(parts[1]);   		
		}
		
	}
	
	/*
	 * Set space before this item
	 * Appearance is:  pdfheight_## where ## is the height
	 */
	void setSpace(String aValue, DisplayItem di) {
		
		String [] parts = aValue.split("_");
		if(parts.length >= 2) {
			di.space = Integer.valueOf(parts[1]);   		
		}
		
	}
	
	/*
	 * Convert the results  and survey definition arrays to display items
	 */
	ArrayList<DisplayItem> convertChoiceListToDisplayItems(
			org.smap.sdal.model.Survey survey, 
			org.smap.sdal.model.Question question,
			ArrayList<Result> choiceResults,
			int languageIdx) {
		
		ArrayList<DisplayItem> diList = null;
		if(choiceResults != null) {
			diList = new ArrayList<DisplayItem>();
			for(Result r : choiceResults) {

				Option option = survey.optionLists.get(r.listName).options.get(r.cIdx);
				Label label = option.labels.get(languageIdx);
				DisplayItem di = new DisplayItem();
				di.text = label.text == null ? "" : label.text;
				di.name = r.name;
				di.type = "choice";
				di.isSet = r.isSet;
				diList.add(di);
			}
		}
		return diList;
	}
	
	/*
	 * Add the question label, hint, and any media
	 */
	private PdfPCell addDisplayItem(Parser parser, 
			KeyValue kv, 
			String basePath,
			boolean barcode,
			String type) throws BadElementException, MalformedURLException, IOException {
		
		PdfPCell valueCell = new PdfPCell();
		valueCell.setBorderColor(BaseColor.LIGHT_GRAY);
		
		 
		// Set the content of the value cell
		try {
			if(type != null && type.equals("image")) {
				Image img = Image.getInstance(kv.v);
				valueCell.addElement(img);
			} else if(barcode) {
				BarcodeQRCode qrcode = new BarcodeQRCode(kv.v.trim(), 1, 1, null);
		         Image qrcodeImage = qrcode.getImage();
		         qrcodeImage.setAbsolutePosition(10,500);
		         qrcodeImage.scalePercent(200);
		         valueCell.addElement((qrcodeImage));
			} else {
				valueCell.addElement(getPara(kv.v));
			}
		} catch (Exception e) {
			log.info("Error updating value cell, continuing: " + basePath + " : " + kv.v);
			log.log(Level.SEVERE, "Exception", e);
		}

		return valueCell;
	}
	
	/*
	 * Set the contents of the value cell
	 */
	private void updateValueCell(PdfPCell valueCell, 
			String value,
			String basePath
			) throws BadElementException, MalformedURLException, IOException {
	
			
		valueCell.addElement(getPara(value));

		

	}
	
	private Paragraph getPara(String value) {
		
		Paragraph para = new Paragraph("", font);

		if(value != null && value.trim().length() > 0) {
			para.add(new Chunk(GeneralUtilityMethods.unesc(value), font));
		}
		
		return para;
	}
	
	private void processSelect(PdfPCell cell, DisplayItem di,
			boolean generateBlank,
			GlobalVariables gv) {

		// If generating blank template
		List list = new List();
		list.setAutoindent(false);
		list.setSymbolIndent(24);
		
		String stringValue = null;
		
		boolean isSelectMultiple = di.type.equals("select") ? true : false;
		
		// Questions that append their values to this question
		ArrayList<String> deps = gv.addToList.get(di.fIdx + "_" + di.rec_number + "_" + di.name);
		
		/*
		 * Add the value of this question unless
		 *   The form is not blank and the value is "other" and their are 1 or more dependent questions
		 *   In this case we assume that its only the values of the dependent questions that are needed
		 */
		if(generateBlank) {
			for(DisplayItem aChoice : di.choices) {
				ListItem item = new ListItem(GeneralUtilityMethods.unesc(aChoice.text), font);
			
				if(isSelectMultiple) {
					if(aChoice.isSet) {
						item.setListSymbol(new Chunk("\uf046", Symbols)); 
						list.add(item);	
					} else {
					
						item.setListSymbol(new Chunk("\uf096", Symbols)); 
						list.add(item);
					}
				
				} else {
					if(aChoice.isSet) {
						item.setListSymbol(new Chunk("\uf111", Symbols)); 
						list.add(item);

					} else {
						//item.setListSymbol(new Chunk("\241", Symbols)); 
						item.setListSymbol(new Chunk("\uf10c", Symbols)); 
						list.add(item);
					}
				}
			}
			
			cell.addElement(list);
			
		} else {
			stringValue = getSelectValue(isSelectMultiple, di, deps);
			cell.addElement(getPara(stringValue));
		}

	}
	
	/*
	 * Get the value of a select question
	 */
	String getSelectValue(boolean isSelectMultiple, DisplayItem di, ArrayList<String> deps) {
		StringBuffer sb = new StringBuffer("");
		
		for(DisplayItem aChoice : di.choices) {
			ListItem item = new ListItem(GeneralUtilityMethods.unesc(aChoice.text));
			
			if(isSelectMultiple) {
				if(aChoice.isSet) {
				
					if(deps == null || (aChoice.name != null && !aChoice.name.trim().toLowerCase().equals("other"))) {
						if(sb.length() > 0) {
							sb.append(", ");
						}
						sb.append(aChoice.text);
					}
					
				} 
			} else {
				if(aChoice.isSet) {
					
					if(deps == null || (aChoice.name != null && !aChoice.name.trim().toLowerCase().equals("other"))) {
						if(sb.length() > 0) {
							sb.append(", ");
						}
						sb.append(aChoice.text);
					}

				}
			}

			
		}
			
		return sb.toString();
		
	}
	
	/*
	 * Fill in user details for the output when their is no template
	 */
	private void fillNonTemplateUserDetails(Document document, User user, String basePath) throws IOException, DocumentException {
		
		String settings = user.settings;
		Type type = new TypeToken<UserSettings>(){}.getType();
		Gson gson=  new GsonBuilder().setDateFormat("yyyy-MM-dd").create();
		UserSettings us = gson.fromJson(settings, type);
		
		float indent = (float) 20.0;
		addValue(document, "Completed by:", (float) 0.0);
		if(user.signature != null && user.signature.trim().length() > 0) {
			String fileName = null;
			try {
				fileName = basePath + File.separator + user.signature;

					Image img = Image.getInstance(fileName);
					img.scaleToFit(200, 50);
					img.setIndentationLeft(indent);
					
				    document.add(img);
					
			} catch (Exception e) {
				log.info("Error: Failed to add image " + fileName + " to pdf");
			}
		}
		addValue(document, user.name, indent);
		addValue(document, user.company_name, indent);
		if(us != null) {
			addValue(document, us.title, indent);
			addValue(document, us.license, indent);
		}

	}
	
	/*
	 * Format a key value pair into a paragraph
	 */
	private void addKeyValuePair(Document document, String key, String value) throws DocumentException {
		Paragraph para = new Paragraph("", font);
		
		para.add(new Chunk(GeneralUtilityMethods.unesc(key), fontbold));
		para.add(new Chunk(GeneralUtilityMethods.unesc(value), font));
		
		document.add(para);
	}
	
	/*
	 * Format a single value into a paragraph
	 */
	private void addValue(Document document, String value, float indent) throws DocumentException {
		
		if(value != null && value.trim().length() > 0) {
			Paragraph para = new Paragraph("", font);	
			para.setIndentationLeft(indent);
			para.add(new Chunk(GeneralUtilityMethods.unesc(value), font));
			document.add(para);
		}
	}
}


