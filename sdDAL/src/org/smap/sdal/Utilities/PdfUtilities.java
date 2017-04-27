package org.smap.sdal.Utilities;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URLEncoder;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.smap.sdal.managers.PDFTableManager;

import com.itextpdf.text.BadElementException;
import com.itextpdf.text.DocumentException;
import com.itextpdf.text.Image;
import com.itextpdf.text.pdf.AcroFields;
import com.itextpdf.text.pdf.PushbuttonField;

public class PdfUtilities {

	private static Logger log =
			 Logger.getLogger(PDFTableManager.class.getName());
	
	public static void addImageTemplate(AcroFields pdfForm, String fieldName, String basePath, String value) throws IOException, DocumentException {
		PushbuttonField ad = pdfForm.getNewPushbuttonFromField(fieldName);
		if(ad != null) {
			ad.setLayout(PushbuttonField.LAYOUT_ICON_ONLY);
			ad.setProportionalIcon(true);
			try {
				ad.setImage(Image.getInstance(basePath + "/" + value));
			} catch (Exception e) {
				log.info("Error: Failed to add image " + basePath + "/" + value + " to pdf: " + e.getMessage());
			}
			pdfForm.replacePushbuttonField(fieldName, ad.getField());
			log.info("Adding image to: " + fieldName);
		} else {
			//log.info("Picture field: " + fieldName + " not found");
		}
	}
	
	public static void addImageTemplate(AcroFields pdfForm, String fieldName, Image img) throws IOException, DocumentException {
		log.info("xxxxxxx: Add map image to: " + fieldName);
		PushbuttonField ad = pdfForm.getNewPushbuttonFromField(fieldName);
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
		} else {
			//log.info("Picture field: " + fieldName + " not found");
		}
	}
	
	public static Image getMapImage(String map, String value, String location, String mapbox_key) throws BadElementException, MalformedURLException, IOException {
		
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
			// GeoJson data
			url.append("geojson(");

			String jsonValue = value;
			url.append(URLEncoder.encode(jsonValue, "UTF-8"));
			url.append(")/auto/");
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
		}
		
		if(getMap) {
			url.append("500x300.png?access_token=");
			url.append(mapbox_key);
			img = Image.getInstance(url.toString());
		} 
		
		return img;
	}
}
