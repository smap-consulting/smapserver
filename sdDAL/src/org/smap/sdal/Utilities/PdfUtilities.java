package org.smap.sdal.Utilities;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URLEncoder;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.smap.sdal.managers.LogManager;
import org.smap.sdal.managers.PDFTableManager;

import com.itextpdf.text.Anchor;
import com.itextpdf.text.BadElementException;
import com.itextpdf.text.DocumentException;
import com.itextpdf.text.Element;
import com.itextpdf.text.Font;
import com.itextpdf.text.Image;
import com.itextpdf.text.Phrase;
import com.itextpdf.text.Rectangle;
import com.itextpdf.text.pdf.AcroFields;
import com.itextpdf.text.pdf.AcroFields.FieldPosition;
import com.itextpdf.text.pdf.ColumnText;
import com.itextpdf.text.pdf.PdfStamper;
import com.itextpdf.text.pdf.PushbuttonField;

public class PdfUtilities {

	private static Logger log =
			 Logger.getLogger(PDFTableManager.class.getName());
	
	private static LogManager lm = new LogManager();		// Application log
	
	public static void addImageTemplate(AcroFields pdfForm, String fieldName, String basePath, 
			String value, String serverRoot, PdfStamper stamper, Font symbols_font) throws IOException, DocumentException {
		PushbuttonField ad = pdfForm.getNewPushbuttonFromField(fieldName);
		if(ad != null) {
			ad.setLayout(PushbuttonField.LAYOUT_ICON_ONLY);
			ad.setProportionalIcon(true);
			try {
				ad.setImage(Image.getInstance(basePath + "/" + value));
				pdfForm.replacePushbuttonField(fieldName, ad.getField());
			} catch (Exception e) {
				log.info("Error: Failed to add image " + basePath + "/" + value + " to pdf: " + e.getMessage());
			}
			
			log.info("Adding image to: " + fieldName);
		} else {
	
			String imageUrl = serverRoot + value;

			List<FieldPosition> posList = pdfForm.getFieldPositions(fieldName);
			if(posList == null) {
				log.info("Field not found for: " + fieldName);
			} else {
				Rectangle targetPosition = posList.get(0).position;
				int page = pdfForm.getFieldPositions(fieldName).get(0).page;
			    Anchor url = new Anchor("\uf08e", symbols_font);
			    url.setReference(imageUrl);
			    ColumnText data = new ColumnText(stamper.getOverContent(page));
			
			    data.setSimpleColumn(url, targetPosition.getLeft(), targetPosition.getBottom(), targetPosition.getRight(), targetPosition.getTop(), 
			    		(targetPosition.getHeight() + symbols_font.getSize()) / 2, Element.ALIGN_CENTER);
			    data.go();
			}
		}
	}
	
	public static void addMapImageTemplate(AcroFields pdfForm, PushbuttonField ad, String fieldName, Image img) throws IOException, DocumentException {
		
		if(ad != null) {
			ad.setLayout(PushbuttonField.LAYOUT_ICON_ONLY);
			ad.setProportionalIcon(true);
			try {
				ad.setImage(img);
			} catch (Exception e) {
				log.info("Error: Failed to add image to pdf: " + e.getMessage());
			}
			pdfForm.replacePushbuttonField(fieldName, ad.getField());
			log.info("Adding image to: " + fieldName);
		} 
	}
	
	public static Image getMapImage(Connection sd, 
			String map, 
			String value, 
			String location, 
			String zoom,
			String mapbox_key,
			int sId,
			String user,
			String markerColor) throws BadElementException, MalformedURLException, IOException, SQLException {
		
		Image img = null;
		
		StringBuffer url = new StringBuffer();
		boolean getMap = false;
		url.append("https://api.mapbox.com/v4/");
		if(map != null) {
			url.append(map);
		} else {
			url.append("mapbox.streets");	// default map
		}
		url.append("/");
		
		if(value != null && value.trim().length() > 0) {
			
			// GeoJson data - add styling
			value = "{\"type\":\"FeatureCollection\",\"features\":[{\"type\":\"Feature\",\"geometry\":" + 
					value + 
					",\"properties\":{";
			
			// properties
			if(markerColor == null) {
				markerColor = "f00";
			}
			value += "\"marker-color\":\"#" + markerColor + "\"";		// Add marker color
			
			value += "}}]}";
			// End add styling
			
			url.append("geojson(");

			String jsonValue = value;
			url.append(URLEncoder.encode(jsonValue, "UTF-8"));
			url.append(")/");
			if(zoom != null && zoom.trim().length() > 0) {
				url.append(GeneralUtilityMethods.getGeoJsonCentroid(value) + "," + zoom);
			} else if(location != null) {
				url.append(location);
			} else {
				url.append("auto");
			}
			url.append("/");
			getMap = true;
		} else {
			// Attempt to get default map boundary from appearance
			if(location != null) {
				url.append(location);
				url.append("/");
				getMap = true;
			}					
		}
		
		if(getMap && mapbox_key == null) {
			log.info("Mapbox key not specified.  PDF Map not created");
		} else if(getMap) {
			url.append("500x300.png?access_token=");
			url.append(mapbox_key);
			try {
				log.info("Mapbox API call: " + url);
				img = Image.getInstance(url.toString());
				lm.writeLog(sd, sId, user, "Mapbox Request", url.toString());
			} catch (Exception e) {
				log.log(Level.SEVERE, "Exception", e);
			}
		} 
		
		return img;
	}
}
