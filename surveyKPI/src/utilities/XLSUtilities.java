package utilities;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.URL;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.io.IOUtils;
import org.apache.poi.common.usermodel.HyperlinkType;
import org.apache.poi.hssf.usermodel.HSSFDataFormat;
import org.apache.poi.hssf.usermodel.HSSFDateUtil;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.ClientAnchor;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.Drawing;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Hyperlink;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Picture;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Workbook;
import org.javarosa.core.model.condition.IFunctionHandler;
import org.javarosa.core.model.data.IAnswerData;
import org.javarosa.core.model.instance.InstanceInitializationFactory;
import org.javarosa.core.model.instance.TreeElement;
import org.javarosa.core.model.utils.IPreloadHandler;
import org.javarosa.form.api.FormEntryModel;
import org.javarosa.model.xform.XFormsModule;
import org.javarosa.xform.util.XFormUtils;
import org.smap.model.SurveyTemplate;
import org.smap.sdal.Utilities.ApplicationException;
import org.smap.sdal.Utilities.GeneralUtilityMethods;
import org.smap.server.utilities.GetXForm;

public class XLSUtilities {

	private static Logger log =
			Logger.getLogger(XLSUtilities.class.getName());
	/**
	 * create a library of cell styles
	 */
	public static Map<String, CellStyle> createStyles(Workbook wb){

		Map<String, CellStyle> styles = new HashMap<String, CellStyle>();
		DataFormat format = wb.createDataFormat();

		/*
		 * Create fonts
		 */
		Font largeFont = wb.createFont();
		largeFont.setBold(true);
		largeFont.setFontHeightInPoints((short) 14);

		Font boldFont = wb.createFont();
		boldFont.setBold(true);

		Font linkFont = wb.createFont();
		linkFont.setUnderline(Font.U_SINGLE);
		linkFont.setColor(IndexedColors.BLUE.getIndex());
		
		Font redFont = wb.createFont();
		redFont.setColor(IndexedColors.RED.getIndex());
		
		Font blueFont = wb.createFont();
		blueFont.setColor(IndexedColors.BLUE.getIndex());

		/*
		 * Create styles
		 * Styles for XLS Form
		 */
		CellStyle style = wb.createCellStyle();
		style.setFont(boldFont);
		styles.put("header", style);
		
		style = wb.createCellStyle();
		style.setFont(boldFont);
		style.setFillForegroundColor(IndexedColors.LIGHT_GREEN.getIndex());
		style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
		styles.put("wide", style);

		style = wb.createCellStyle();
		style.setFont(boldFont);
		style.setFillForegroundColor(IndexedColors.CORNFLOWER_BLUE.getIndex());
		style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
		styles.put("header2", style);
		styles.put("header_tasks", style);
		
		style = wb.createCellStyle();
		style.setFont(boldFont);
		style.setFillForegroundColor(IndexedColors.LIGHT_YELLOW.getIndex());
		style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
		styles.put("header_assignments", style);

		style = wb.createCellStyle();
		style.setFont(redFont);
		styles.put("errorStyle", style);
		
		style = wb.createCellStyle();
		style.setFillForegroundColor(IndexedColors.LIGHT_GREEN.getIndex());
		style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
		styles.put("good", style);
		
		style = wb.createCellStyle();
		style.setFillForegroundColor(IndexedColors.ROSE.getIndex());
		style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
		styles.put("bad", style);
		
		style = wb.createCellStyle();
		style.setWrapText(true);
		styles.put("label", style);

		style = wb.createCellStyle();
		style.setFillForegroundColor(IndexedColors.CORNFLOWER_BLUE.getIndex());
		style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
		styles.put("begin repeat", style);

		style = wb.createCellStyle();
		style.setFillForegroundColor(IndexedColors.LIGHT_YELLOW.getIndex());
		style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
		styles.put("begin group", style);
		styles.put("header_assignments", style);

		style = wb.createCellStyle();
		style.setFillForegroundColor(IndexedColors.LIGHT_GREEN.getIndex());
		style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
		styles.put("is_required", style);

		style = wb.createCellStyle();
		style.setFillForegroundColor(IndexedColors.CORAL.getIndex());
		style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
		styles.put("not_required", style);


		/*
		 * Other styles
		 */
		style = wb.createCellStyle();
		style.setWrapText(true);
		styles.put("default", style);
		
		style = wb.createCellStyle();
		style.setWrapText(true);
		style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
		style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
		styles.put("default_grey", style);

		style = wb.createCellStyle();
		style.setDataFormat(format.getFormat("yyyy-mm-dd h:mm"));	
		styles.put("datetime", style);

		style = wb.createCellStyle();
		style.setDataFormat(format.getFormat("yyyy-mm-dd"));	
		styles.put("date", style);

		style = wb.createCellStyle();
		style.setDataFormat(HSSFDataFormat.getBuiltinFormat("0.00"));
		styles.put("numeric", style);

		style = wb.createCellStyle();
		style.setFont(linkFont);
		styles.put("link", style);

		/*
		 * LQAS Styles
		 */
		style = wb.createCellStyle();
		style.setFont(largeFont);
		style.setAlignment(HorizontalAlignment.LEFT);
		styles.put("title", style);

		style = wb.createCellStyle();	
		style.setWrapText(true);
		style.setAlignment(HorizontalAlignment.LEFT);
		styles.put("no_border", style);

		// Remaining styles are all derived from a common base style
		style = getBaseStyle(wb);
		style = wb.createCellStyle();
		style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.index);
		style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
		style.setFont(boldFont);
		styles.put("group", style);

		style = getBaseStyle(wb);
		style.setAlignment(HorizontalAlignment.CENTER);
		styles.put("data", style);

		style = getBaseStyle(wb);
		style.setFillForegroundColor(IndexedColors.LIGHT_YELLOW.index);
		style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
		style.setAlignment(HorizontalAlignment.CENTER);
		styles.put("source", style);

		style = getBaseStyle(wb);
		style.setFillForegroundColor(IndexedColors.LAVENDER.index);
		style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
		style.setAlignment(HorizontalAlignment.CENTER);
		styles.put("raw_source", style);

		style = getBaseStyle(wb);
		style.setAlignment(HorizontalAlignment.CENTER);
		style.setFont(boldFont);  
		styles.put("data_header", style);
		
		style = getBaseStyle(wb);
		style.setFont(boldFont);  
		styles.put("bold", style);

		return styles;
	}

	private static CellStyle getBaseStyle(Workbook wb) {

		CellStyle style = wb.createCellStyle();

		style.setWrapText(true);
		style.setAlignment(HorizontalAlignment.LEFT);
		style.setBorderBottom(BorderStyle.THIN);
		style.setBorderTop(BorderStyle.THIN);
		style.setBorderRight(BorderStyle.THIN);
		style.setBorderLeft(BorderStyle.THIN);

		return style;
	}

	/*
	 * Merge multiple choice values into a single value
	 */
	public static String updateMultipleChoiceValue(String dbValue, String choiceName, String currentValue) {
		boolean isSet = false;
		String newValue = currentValue;

		if(dbValue.equals("1")) {
			isSet = true;
		}

		if(isSet) {
			if(currentValue == null) {
				newValue = choiceName;
			} else {
				newValue += " " + choiceName;
			}
		}

		return newValue;
	}

	/*
	 * Get a hashmap of column name and column index
	 */
	public static HashMap<String, Integer> getHeader(Row row, ResourceBundle localisation, int rowNumber, String sheet) throws ApplicationException {
		HashMap<String, Integer> header = new HashMap<String, Integer> ();

		int lastCellNum = row.getLastCellNum();
		Cell cell = null;
		String name = null;

		for(int i = 0; i <= lastCellNum; i++) {
			cell = row.getCell(i);
			if(cell != null) {
				name = cell.getStringCellValue();
				if(name != null && name.trim().length() > 0) {
					if(name.toLowerCase().equals("list name") ||
							name.toLowerCase().equals("name")
							|| name.toLowerCase().equals("label")) {
						name = name.toLowerCase();	// Automatically set columns that need to be lower case to lower case		
					}
					Integer exists = header.get(name);
					if(exists == null) {
						header.put(name, i);
					} else {
						throw getApplicationException(localisation, "tu_dh", rowNumber, sheet, name, null, null);
					}
				}
			}
		}

		return header;
	}

	/*
	 * Get the value of a cell at the specified column
	 */
	public static String getColumn(Row row, String name, HashMap<String, Integer> header, int lastCellNum, String def) throws ApplicationException {

		Integer cellIndex;
		int idx;
		String value = null;

		cellIndex = header.get(name);
		if(cellIndex != null) {
			idx = cellIndex;
			if(idx <= lastCellNum) {
				Cell c = row.getCell(idx);
				if(c != null) {
					value = getCellValue(c);


				}
			}
		} 

		if(value == null) {		// Set to default value if null
			value = def;
		}

		return value;
	}

	/*
	 * Get the text value of a cell and return null if the cell is empty
	 */
	public static String getTextColumn(Row row, String name, HashMap<String, Integer> header, int lastCellNum, String default_value) throws ApplicationException {

		String value = null;
		Integer cellIndex;
		int idx;

		cellIndex = header.get(name);
		if(cellIndex != null && cellIndex < lastCellNum) {
			idx = cellIndex;
			Cell c = row.getCell(idx);
			if(c != null) {
				value = getCellValue(c);
				if(value != null) {
					value = value.replaceAll("\u00A0", " ");		// Replace non breaking space with space
					value = value.trim();  	// Remove trailing whitespace, its not visible to users
					if(value.length() == 0) {
						value = null;
					}
				}
			}
		} 
		
		if(value == null) {
			value = default_value;
		}

		return value;
	}

	/*
	 * Get a cell value as String from XLS
	 */
	private static String getCellValue(Cell c) throws ApplicationException {
		String value = null;
		double dValue = 0.0;
		Date dateValue = null;
		boolean bValue = false;
		SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd hh:mm");

		if(c.getCellType() == CellType.NUMERIC) {
			if (HSSFDateUtil.isCellDateFormatted(c)) {
				dateValue = c.getDateCellValue();
				value = dateFormat.format(dateValue);
			} else {
				dValue = c.getNumericCellValue();
				value = String.valueOf(dValue);
				if(value != null && value.endsWith(".0")) {
					value = value.substring(0, value.lastIndexOf('.'));
				}
			}
		} else if(c.getCellType() == CellType.STRING) {
			value = c.getStringCellValue();
		} else if(c.getCellType() == CellType.BOOLEAN) {
			bValue = c.getBooleanCellValue();
			value = String.valueOf(bValue);
		} else if(c.getCellType() == CellType.BLANK) {
			value = c.getStringCellValue();	// !! Not sure what this is about
		} else {
			throw(new ApplicationException("Error: Unknown cell type: " + c.getCellType() + 
					" in sheet "  + c.getSheet().getSheetName() +
					" in row " + (c.getRowIndex() + 1) + 
					", column " + (c.getColumnIndex() + 1)));
		}

		return value;
	}

	public static ApplicationException getApplicationException(
			ResourceBundle localisation, 
			String code, 
			int row, 
			String sheet, 
			String param1, 
			String param2,
			String param3) {

		StringBuffer buf = null;
		if(row >= 0) {
			buf = new StringBuffer(localisation.getString("tu_rn")).append(" ");
		} else {
			buf = new StringBuffer("");
		}
		buf.append(localisation.getString(code));
		
		String msg = buf.toString();
		msg = msg.replace("%sheet", sheet);
		msg = msg.replaceAll("%row", String.valueOf(row));

		if(param1 != null) {
			msg = msg.replace("%s1", param1);
		}
		
		msg = msg.replace("%s2", sheet);
		
		if(param2 != null) {
			msg = msg.replace("%s3", param2);
		}
		
		if(param3 != null) {
			msg = msg.replace("%s4", param3);
		}
		

		return new ApplicationException(msg);
	}
	
	public static void setCellValue(Workbook wb, 
			Sheet sheet,
			Cell cell, 
			Map<String, CellStyle> styles,
			String value, 
			String type,
			boolean embedImages, 
			String basePath,
			int row,
			int col,
			boolean isXLSX) {
		
		if(value != null && value.startsWith("POINT")) {
			CreationHelper createHelper = wb.getCreationHelper();
			String coords [] = GeneralUtilityMethods.getLonLat(value);
			if(coords.length > 1) {
				value = "http://www.openstreetmap.org/?mlat=" +
						coords[1] +
						"&mlon=" +
						coords[0] +
						"&zoom=14";
				cell.setCellStyle(styles.get("link"));
				Hyperlink url = createHelper.createHyperlink(HyperlinkType.URL);
				url.setAddress(value);
				cell.setHyperlink(url);
				/*
				if(isXLSX) {
					XSSFHyperlink url = (XSSFHyperlink)createHelper.createHyperlink(Hyperlink.LINK_URL);
					url.setAddress(value);
					cell.setHyperlink(url);
				} else {
					HSSFHyperlink url = new HSSFHyperlink(HSSFHyperlink.LINK_URL);
					url.setAddress(value);
					cell.setHyperlink(url);
				}
				*/
			} 
			cell.setCellValue(value);
			
		} else if(value != null && (value.startsWith("https://") || value.startsWith("http://"))) {

			CreationHelper createHelper = wb.getCreationHelper();
			if(embedImages) {
				if(value.endsWith(".jpg") || value.endsWith(".png")) {
					//int idx = value.indexOf("attachments");
					//int idxName = value.lastIndexOf('/');
					//if(idx > 0 && idxName > 0) {
						//String fileName = value.substring(idxName);
						//String stem = basePath + "/" + value.substring(idx, idxName);
						//String imageName = stem + "/thumbs" + fileName + ".jpg";
						try {
							//InputStream inputStream = new FileInputStream(imageName);
							InputStream inputStream = new URL(value).openStream();
							byte[] imageBytes = IOUtils.toByteArray(inputStream);
							int pictureureIdx = wb.addPicture(imageBytes, Workbook.PICTURE_TYPE_JPEG);
							inputStream.close();

							ClientAnchor anchor = createHelper.createClientAnchor();
							anchor.setCol1(col);
							anchor.setRow1(row - 1);
							anchor.setCol2(col + 1);
							anchor.setRow2(row);
							anchor.setAnchorType(ClientAnchor.AnchorType.MOVE_AND_RESIZE); 
							//sheet.setColumnWidth(i, 20 * 256);
							Drawing drawing = sheet.createDrawingPatriarch();
							drawing.createPicture(anchor, pictureureIdx);
							//pict.resize();
						} catch (Exception e) {
							log.info("Error: Missing image file: " + value);
						}
					//}
				}
			} 

			cell.setCellStyle(styles.get("link"));
			Hyperlink url = createHelper.createHyperlink(HyperlinkType.URL);
			try {
				url.setAddress(value);
				cell.setHyperlink(url);
			} catch (Exception e) {
				log.info("Error: " + e.getMessage() + " setting hypperlink address " + value);
			}
			
			cell.setCellValue(value);

		} else {

			/*
			 * Write the value as double or string
			 */
			boolean cellWritten = false; 

			if(type != null) {
				if(type.equals("decimal") || 
						type.equals("int") && 
						value != null) {
					try {
						double vDouble = Double.parseDouble(value);
	
						cell.setCellStyle(styles.get("default"));
						cell.setCellValue(vDouble);
						cellWritten = true;
					} catch (Exception e) {
						// Ignore
					}
				} else if(type.equals("dateTime")) {
					DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
					try {
						java.util.Date date = dateFormat.parse(value);
						cell.setCellStyle(styles.get("datetime"));
						cell.setCellValue(date);
						cellWritten = true;
					} catch (Exception e) {
						// Ignore
					}
				} else if(type.equals("date")) {
					DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
					try {
						java.util.Date date = dateFormat.parse(value);
						cell.setCellStyle(styles.get("date"));
						cell.setCellValue(date);
						cellWritten = true;
					} catch (Exception e) {
						// Ignore
					}
				}
			}

			if(!cellWritten) {
				cell.setCellStyle(styles.get("default"));
				cell.setCellValue(value);
			}
		}        
	}
	

    /*
     * Validate a survey stored in the database using the javarosa api
     * Will throw an exception on errors
     */
    public static void javaRosaSurveyValidation(ResourceBundle localisation, int sId, String user, String tz) throws Exception {
		
    		class FakePreloadHandler implements IPreloadHandler {

            String preloadHandled;


            public FakePreloadHandler(String preloadHandled) {
                this.preloadHandled = preloadHandled;
            }


            public boolean handlePostProcess(TreeElement arg0, String arg1) {
                // TODO Auto-generated method stub
                return false;
            }


            public IAnswerData handlePreload(String arg0) {
                // TODO Auto-generated method stub
                return null;
            }


            public String preloadHandled() {
                // TODO Auto-generated method stub
                return preloadHandled;
            }

        }
		
		new XFormsModule().registerModule();
		
		SurveyTemplate template = new SurveyTemplate(localisation);
		template.readDatabase(sId, false);
		GetXForm xForm = new GetXForm(localisation, user, tz);

		String xmlForm = xForm.get(template, false, true, false, user);
		InputStream is = new ByteArrayInputStream(xmlForm.getBytes());
		try {
			org.javarosa.core.model.FormDef fd = XFormUtils.getFormFromInputStream(is);
			FormEntryModel fem = new FormEntryModel(fd);
			
			// make sure properties get loaded
	        fd.getPreloader().addPreloadHandler(new FakePreloadHandler("property"));

	        // update evaluation context for function handlers
	        fd.getEvaluationContext().addFunctionHandler(new IFunctionHandler() {

	            public String getName() {
	                return "pulldata";
	            }

	            public List<Class[]> getPrototypes() {
	                return new ArrayList<Class[]>();
	            }

	            public boolean rawArgs() {
	                return true;
	            }

	            public boolean realTime() {
	                return false;
	            }

				@Override
				public Object eval(Object[] arg0, org.javarosa.core.model.condition.EvaluationContext arg1) {
					// TODO Auto-generated method stub
					return arg0[0];
				}});
	        
			fd.initialize(true, new InstanceInitializationFactory());
		} catch(Exception e) {
			log.log(Level.SEVERE, e.getMessage(), e);
			log.info(xmlForm);
			throw e;
		}

	}
    
	
}
