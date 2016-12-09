package utilities;

import java.util.HashMap;
import java.util.Map;

import org.apache.poi.hssf.usermodel.HSSFCellStyle;
import org.apache.poi.hssf.util.HSSFColor;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Workbook;

public class XLSUtilities {

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
        largeFont.setBoldweight(Font.BOLDWEIGHT_BOLD);
        largeFont.setFontHeightInPoints((short) 14);
        
        Font boldFont = wb.createFont();
        boldFont.setBoldweight(Font.BOLDWEIGHT_BOLD);
        
		Font linkFont = wb.createFont();
		linkFont.setUnderline(Font.U_SINGLE);
	    linkFont.setColor(IndexedColors.BLUE.getIndex());
        
        /*
         * Create styles
         * Styles for XLS Form
         */
        CellStyle style = wb.createCellStyle();
        style.setFont(boldFont);
        styles.put("header", style);

        style = wb.createCellStyle();
        style.setWrapText(true);
        styles.put("label", style);
        
        style = wb.createCellStyle();
        style.setFillForegroundColor(IndexedColors.CORNFLOWER_BLUE.getIndex());
        style.setFillPattern(CellStyle.SOLID_FOREGROUND);
        styles.put("begin repeat", style);
        
        style = wb.createCellStyle();
        style.setFillForegroundColor(IndexedColors.DARK_YELLOW.getIndex());
        style.setFillPattern(CellStyle.SOLID_FOREGROUND);
        styles.put("begin group", style);
        
        style = wb.createCellStyle();
        style.setFillForegroundColor(IndexedColors.LIGHT_GREEN.getIndex());
        style.setFillPattern(CellStyle.SOLID_FOREGROUND);
        styles.put("is_required", style);
        
        style = wb.createCellStyle();
        style.setFillForegroundColor(IndexedColors.CORAL.getIndex());
        style.setFillPattern(CellStyle.SOLID_FOREGROUND);
        styles.put("not_required", style);

        
        /*
         * Other styles
         */
        style = wb.createCellStyle();
        style.setWrapText(true);
        styles.put("default", style);
        
		style = wb.createCellStyle();
		style.setDataFormat(format.getFormat("yyyy-mm-dd h:mm"));	
		styles.put("datetime", style);
		
		style = wb.createCellStyle();
	    style.setFont(linkFont);
	    styles.put("link", style);
        
        /*
         * LQAS Styles
         */
        style = wb.createCellStyle();
        style.setFont(largeFont);
        style.setAlignment(CellStyle.ALIGN_LEFT);
        styles.put("title", style);

        style = wb.createCellStyle();	
        style.setWrapText(true);
        style.setAlignment(CellStyle.ALIGN_LEFT);
        styles.put("no_border", style);
        
        // Remaining styles are all derived from a common base style
        style = getBaseStyle(wb);
        style = wb.createCellStyle();
        style.setFillForegroundColor(HSSFColor.GREY_25_PERCENT.index);
        style.setFillPattern(CellStyle.SOLID_FOREGROUND);
        style.setFont(boldFont);
        styles.put("group", style);
        
        style = getBaseStyle(wb);
        style.setAlignment(CellStyle.ALIGN_CENTER);
        styles.put("data", style);
        
        style = getBaseStyle(wb);
        style.setFillForegroundColor(HSSFColor.LIGHT_YELLOW.index);
        style.setFillPattern(CellStyle.SOLID_FOREGROUND);
        style.setAlignment(CellStyle.ALIGN_CENTER);
        styles.put("source", style);
        
        style = getBaseStyle(wb);
        style.setFillForegroundColor(HSSFColor.LAVENDER.index);
        style.setFillPattern(CellStyle.SOLID_FOREGROUND);
        style.setAlignment(CellStyle.ALIGN_CENTER);
        styles.put("raw_source", style);
        
        style = getBaseStyle(wb);
        style.setAlignment(CellStyle.ALIGN_CENTER);
        style.setFont(boldFont);  
        styles.put("data_header", style);

        return styles;
    }
    
    private static CellStyle getBaseStyle(Workbook wb) {
    	
    	CellStyle style = wb.createCellStyle();
    	
        style.setWrapText(true);
        style.setAlignment(CellStyle.ALIGN_LEFT);
        style.setBorderBottom(HSSFCellStyle.BORDER_THIN);
        style.setBorderTop(HSSFCellStyle.BORDER_THIN);
        style.setBorderRight(HSSFCellStyle.BORDER_THIN);
        style.setBorderLeft(HSSFCellStyle.BORDER_THIN);
        
        return style;
    }
}
