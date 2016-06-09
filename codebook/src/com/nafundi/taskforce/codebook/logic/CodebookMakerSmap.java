/*
 * Copyright (C) 2013 Nafundi
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package com.nafundi.taskforce.codebook.logic;

import com.googlecode.jatl.Html;
import com.lowagie.text.DocumentException;
import com.lowagie.text.pdf.BaseFont;
import org.w3c.dom.Document;
import org.w3c.tidy.Tidy;
import org.xhtmlrenderer.pdf.ITextRenderer;

import javax.swing.*;
import java.io.*;
import java.util.ArrayList;

/**
 * Used to generate a Codebook in PDF
 *
 * @author Yaw Anokwa (yanokwa@nafundi.com)
 */

public class CodebookMakerSmap  {

    private final ArrayList<CodebookEntry> codebookEntries;
    private final String locale;
    private final String inputFilename;
    private final String outputFolderPath;

    public CodebookMakerSmap(ArrayList<CodebookEntry> codebookEntries, String locale, String inputFilename, String outputFolderPath) {
        this.codebookEntries = codebookEntries;
        this.locale = locale;
        this.inputFilename = inputFilename;
        this.outputFolderPath = outputFolderPath;
    }

    String getLocale() {
        return locale;
    }

    public Integer writeFile() throws Exception {

        String errorMsg = "";

        // string writer
        StringWriter writer = new StringWriter();
        new Html(writer) {{
            body();
            h4().text(inputFilename + " (" + locale + ")").end();
            table().classAttr("table table-bordered table-condensed");
            thead().tr();
            th().h6().text("Variable Name").end().end();
            th().h6().text("Question Text").end().end();
            th().h6().text("Saved Value").end().end();
            end().end();
            tbody();
            for (int i = 0; i < codebookEntries.size(); i++) {
                if (i % 2 == 0) {
                    // fix for no background color in export to pdf
                    tr().classAttr("gray");
                } else {
                    tr();
                }
                CodebookEntry entry = codebookEntries.get(i);
                td().text(entry.getVariable()).end();

                String question = entry.getQuestion();
                String value = entry.getValue();

                // select question
                
                if (question.contains("|")) {
                    td().text(question.replace("|", "")).end();
                    td();
                    table().classAttr("table table-bordered table-condensed");
                    String values[] = value.split("\n");
                    for (int j = 0; j < values.length; j++) {
                    	if(values[j].contains("\t")) {
	                        tr();
	                        //value
	                        td().text(values[j].split("\t")[1]).end();
	                        //label
	                        td().text(values[j].split("\t")[0]).end();
	                        end();
                    	} else {
                    		// Just option text without a value merge with the next value
                    		if(j + 1 < values.length) {
                    			values[j+1] = values[j] + values[j+1];
                    		}
                    	}
                    }
                    end();
                    end();
                } else {
                    td().text(question).end();
                    td().text(value).end();
                }

                end();
            }
            done();
        }};

        // build html string

        // bootstrap css with only headings, body type, and tables
        // add custom tr.gray tag to fix bug in html to pdf export
        // change font family to be Arial Unicode and to include embedding
        String htmlHeader = "<!DOCTYPE html><html><head><meta charset=\"utf-8\"/>\n" +
                "<style type=\"text/css\">.clearfix{*zoom:1;}.clearfix:before,.clearfix:after{display:table;content:\"\";line-height:0;}.clearfix:after{clear:both;}.hide-text{font:0/0 a;color:transparent;text-shadow:none;background-color:transparent;border:0;}.input-block-level{display:block;width:100%;min-height:30px;-webkit-box-sizing:border-box;-moz-box-sizing:border-box;box-sizing:border-box;}body{margin:0;font-family:\"Arial Unicode MS\",\"Arial Unicode\", Arial, sans-serif;-fs-pdf-font-embed:embed;-fs-pdf-font-encoding: Identity-H;font-size:11px;line-height:20px;color:#333333;background-color:#ffffff;}a{color:#0088cc;text-decoration:none;}a:hover,a:focus{color:#005580;text-decoration:underline;}.img-rounded{-webkit-border-radius:6px;-moz-border-radius:6px;border-radius:6px;}.img-polaroid{padding:4px;background-color:#fff;border:1px solid #ccc;border:1px solid rgba(0, 0, 0, 0.2);-webkit-box-shadow:0 1px 3px rgba(0, 0, 0, 0.1);-moz-box-shadow:0 1px 3px rgba(0, 0, 0, 0.1);box-shadow:0 1px 3px rgba(0, 0, 0, 0.1);}.img-circle{-webkit-border-radius:500px;-moz-border-radius:500px;border-radius:500px;}p{margin:0 0 10px;}.lead{margin-bottom:20px;font-size:21px;font-weight:200;line-height:30px;}small{font-size:85%;}strong{font-weight:bold;}em{font-style:italic;}cite{font-style:normal;}.muted{color:#999999;}a.muted:hover,a.muted:focus{color:#808080;}.text-warning{color:#c09853;}a.text-warning:hover,a.text-warning:focus{color:#a47e3c;}.text-publishError{color:#b94a48;}a.text-publishError:hover,a.text-publishError:focus{color:#953b39;}.text-info{color:#3a87ad;}a.text-info:hover,a.text-info:focus{color:#2d6987;}.text-success{color:#468847;}a.text-success:hover,a.text-success:focus{color:#356635;}.text-left{text-align:left;}.text-right{text-align:right;}.text-center{text-align:center;}h1,h2,h3,h4,h5,h6{margin:10px 0;font-family:inherit;font-weight:bold;line-height:20px;color:inherit;text-rendering:optimizelegibility;}h1 small,h2 small,h3 small,h4 small,h5 small,h6 small{font-weight:normal;line-height:1;color:#999999;}h1,h2,h3{line-height:40px;}h1{font-size:38.5px;}h2{font-size:31.5px;}h3{font-size:24.5px;}h4{font-size:17.5px;}h5{font-size:14px;}h6{font-size:11.9px;}h1 small{font-size:24.5px;}h2 small{font-size:17.5px;}h3 small{font-size:14px;}h4 small{font-size:14px;}.page-header{padding-bottom:9px;margin:20px 0 30px;border-bottom:1px solid #eeeeee;}ul,ol{padding:0;margin:0 0 10px 25px;}ul ul,ul ol,ol ol,ol ul{margin-bottom:0;}li{line-height:20px;}ul.unstyled,ol.unstyled{margin-left:0;list-style:none;}ul.inline,ol.inline{margin-left:0;list-style:none;}ul.inline>li,ol.inline>li{display:inline-block;*display:inline;*zoom:1;padding-left:5px;padding-right:5px;}dl{margin-bottom:20px;}dt,dd{line-height:20px;}dt{font-weight:bold;}dd{margin-left:10px;}.dl-horizontal{*zoom:1;}.dl-horizontal:before,.dl-horizontal:after{display:table;content:\"\";line-height:0;}.dl-horizontal:after{clear:both;}.dl-horizontal dt{float:left;width:160px;clear:left;text-align:right;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;}.dl-horizontal dd{margin-left:180px;}hr{margin:20px 0;border:0;border-top:1px solid #eeeeee;border-bottom:1px solid #ffffff;}abbr[title],abbr[data-original-title]{cursor:help;border-bottom:1px dotted #999999;}abbr.initialism{font-size:90%;text-transform:uppercase;}blockquote{padding:0 0 0 15px;margin:0 0 20px;border-left:5px solid #eeeeee;}blockquote p{margin-bottom:0;font-size:17.5px;font-weight:300;line-height:1.25;}blockquote small{display:block;line-height:20px;color:#999999;}blockquote small:before{content:'\\2014 \\00A0';}blockquote.pull-right{float:right;padding-right:15px;padding-left:0;border-right:5px solid #eeeeee;border-left:0;}blockquote.pull-right p,blockquote.pull-right small{text-align:right;}blockquote.pull-right small:before{content:'';}blockquote.pull-right small:after{content:'\\00A0 \\2014';}q:before,q:after,blockquote:before,blockquote:after{content:\"\";}address{display:block;margin-bottom:20px;font-style:normal;line-height:20px;}table{max-width:100%;background-color:transparent;border-collapse:collapse;border-spacing:0;}.table{width:100%;margin-bottom:20px;}.table th,.table td{padding:8px;line-height:20px;text-align:left;vertical-align:top;border-top:1px solid #dddddd;}.table th{font-weight:bold;}.table thead th{vertical-align:bottom;}.table caption+thead tr:first-child th,.table caption+thead tr:first-child td,.table colgroup+thead tr:first-child th,.table colgroup+thead tr:first-child td,.table thead:first-child tr:first-child th,.table thead:first-child tr:first-child td{border-top:0;}.table tbody+tbody{border-top:2px solid #dddddd;}.table .table{background-color:#ffffff;}.table-condensed th,.table-condensed td{padding:4px 5px;}.table-bordered{border:1px solid #dddddd;border-collapse:separate;*border-collapse:collapse;border-left:0;-webkit-border-radius:4px;-moz-border-radius:4px;border-radius:4px;}.table-bordered th,.table-bordered td{border-left:1px solid #dddddd;}.table-bordered caption+thead tr:first-child th,.table-bordered caption+tbody tr:first-child th,.table-bordered caption+tbody tr:first-child td,.table-bordered colgroup+thead tr:first-child th,.table-bordered colgroup+tbody tr:first-child th,.table-bordered colgroup+tbody tr:first-child td,.table-bordered thead:first-child tr:first-child th,.table-bordered tbody:first-child tr:first-child th,.table-bordered tbody:first-child tr:first-child td{border-top:0;}.table-bordered thead:first-child tr:first-child>th:first-child,.table-bordered tbody:first-child tr:first-child>td:first-child,.table-bordered tbody:first-child tr:first-child>th:first-child{-webkit-border-top-left-radius:4px;-moz-border-radius-topleft:4px;border-top-left-radius:4px;}.table-bordered thead:first-child tr:first-child>th:last-child,.table-bordered tbody:first-child tr:first-child>td:last-child,.table-bordered tbody:first-child tr:first-child>th:last-child{-webkit-border-top-right-radius:4px;-moz-border-radius-topright:4px;border-top-right-radius:4px;}.table-bordered thead:last-child tr:last-child>th:first-child,.table-bordered tbody:last-child tr:last-child>td:first-child,.table-bordered tbody:last-child tr:last-child>th:first-child,.table-bordered tfoot:last-child tr:last-child>td:first-child,.table-bordered tfoot:last-child tr:last-child>th:first-child{-webkit-border-bottom-left-radius:4px;-moz-border-radius-bottomleft:4px;border-bottom-left-radius:4px;}.table-bordered thead:last-child tr:last-child>th:last-child,.table-bordered tbody:last-child tr:last-child>td:last-child,.table-bordered tbody:last-child tr:last-child>th:last-child,.table-bordered tfoot:last-child tr:last-child>td:last-child,.table-bordered tfoot:last-child tr:last-child>th:last-child{-webkit-border-bottom-right-radius:4px;-moz-border-radius-bottomright:4px;border-bottom-right-radius:4px;}.table-bordered tfoot+tbody:last-child tr:last-child td:first-child{-webkit-border-bottom-left-radius:0;-moz-border-radius-bottomleft:0;border-bottom-left-radius:0;}.table-bordered tfoot+tbody:last-child tr:last-child td:last-child{-webkit-border-bottom-right-radius:0;-moz-border-radius-bottomright:0;border-bottom-right-radius:0;}.table-bordered caption+thead tr:first-child th:first-child,.table-bordered caption+tbody tr:first-child td:first-child,.table-bordered colgroup+thead tr:first-child th:first-child,.table-bordered colgroup+tbody tr:first-child td:first-child{-webkit-border-top-left-radius:4px;-moz-border-radius-topleft:4px;border-top-left-radius:4px;}.table-bordered caption+thead tr:first-child th:last-child,.table-bordered caption+tbody tr:first-child td:last-child,.table-bordered colgroup+thead tr:first-child th:last-child,.table-bordered colgroup+tbody tr:first-child td:last-child{-webkit-border-top-right-radius:4px;-moz-border-radius-topright:4px;border-top-right-radius:4px;}.table-striped tbody>tr:nth-child(odd)>td,.table-striped tbody>tr:nth-child(odd)>th{background-color:#f9f9f9;}.table-hover tbody tr:hover>td,.table-hover tbody tr:hover>th{background-color:#f5f5f5;}table td[class*=\"span\"],table th[class*=\"span\"],.row-fluid table td[class*=\"span\"],.row-fluid table th[class*=\"span\"]{display:table-cell;float:none;margin-left:0;}.table td.span1,.table th.span1{float:none;width:44px;margin-left:0;}.table td.span2,.table th.span2{float:none;width:124px;margin-left:0;}.table td.span3,.table th.span3{float:none;width:204px;margin-left:0;}.table td.span4,.table th.span4{float:none;width:284px;margin-left:0;}.table td.span5,.table th.span5{float:none;width:364px;margin-left:0;}.table td.span6,.table th.span6{float:none;width:444px;margin-left:0;}.table td.span7,.table th.span7{float:none;width:524px;margin-left:0;}.table td.span8,.table th.span8{float:none;width:604px;margin-left:0;}.table td.span9,.table th.span9{float:none;width:684px;margin-left:0;}.table td.span10,.table th.span10{float:none;width:764px;margin-left:0;}.table td.span11,.table th.span11{float:none;width:844px;margin-left:0;}.table td.span12,.table th.span12{float:none;width:924px;margin-left:0;}.table tbody tr.success>td{background-color:#dff0d8;}.table tbody tr.publishError>td{background-color:#f2dede;}.table tbody tr.warning>td{background-color:#fcf8e3;}.table tbody tr.info>td{background-color:#d9edf7;}.table-hover tbody tr.success:hover>td{background-color:#d0e9c6;}.table-hover tbody tr.publishError:hover>td{background-color:#ebcccc;}.table-hover tbody tr.warning:hover>td{background-color:#faf2cc;}.table-hover tbody tr.info:hover>td{background-color:#c4e3f3;}.table tbody tr.gray>td{background-color:#f9f9f9;}.hidden {visibility:hidden;}\t</style>\n" +
                "</head>";
        String htmlFooter = "\n</html>";
        String htmlDocument = htmlHeader + writer.getBuffer().toString() + htmlFooter;

        // make sure html entities aren't mangled in document building process
        Tidy tidy = new Tidy();
        tidy.setQuiet(true);
        tidy.setXmlTags(false);
        tidy.setShowWarnings(false);
        tidy.setInputEncoding("UTF-8");
        tidy.setOutputEncoding("UTF-8");
        tidy.setXHTML(true);
        Document document = tidy.parseDOM(new ByteArrayInputStream(htmlDocument.getBytes("UTF-8")), null);

        // create render of document
        // ITextRender is not thread-safe
        synchronized (this) {
            ITextRenderer renderer = new ITextRenderer();

            if (isWindows()) {
                renderer.getFontResolver().addFont("C:\\WINDOWS\\Fonts\\ARIALUNI.TTF", BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
            } else if (isMac()) {
                renderer.getFontResolver().addFont("/Library/Fonts/Arial Unicode.ttf", BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
            } else if (isUnix()) {
                //renderer.getFontResolver().addFont("/usr/share/fonts/truetype/ARIALUNI.TTF", BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
                renderer.getFontResolver().addFont("/usr/share/fonts/truetype/ttf-dejavu/DejaVuSans.ttf", BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
            } else {
                System.out.println("Warning: No Arial Unicode found. Non-Latin characters may not display properly.");
            }

            renderer.setDocument(document, null);
            renderer.layout();

            // write out document as pdf
            OutputStream outputStream = null;
            try {
                outputStream = new FileOutputStream(outputFolderPath + File.separator + inputFilename + ".pdf");
            } catch (FileNotFoundException e) {
                errorMsg = e.getMessage();
                e.printStackTrace();
            }
            try {
                renderer.createPDF(outputStream);
            } catch (DocumentException e) {
                errorMsg = e.getMessage();
                e.printStackTrace();
            }
            try {
                if (outputStream != null) {
                    outputStream.close();
                }
            } catch (IOException e) {
                errorMsg = e.getMessage();
                e.printStackTrace();
            }

        }

        if (!"".equals(errorMsg)) {
            publishError(errorMsg);
            return -1;
        }

        System.out.println("Finished making " + getLocale() + " codebook");
        return 0;
    }

    private void publishError(String errorMessage) {
    	System.out.println("Error: Failed to make " + getLocale() + " codebook because " + errorMessage + ".");
    }

    private String getOS() {
        return System.getProperty("os.name").toLowerCase();
    }

    public boolean isWindows() {
        return (getOS().indexOf("win") >= 0);
    }

    public boolean isMac() {
        return (getOS().indexOf("mac") >= 0);
    }

    public boolean isUnix() {
        return (getOS().indexOf("nix") >= 0 || getOS().indexOf("nux") >= 0 || getOS().indexOf("aix") > 0);
    }

}
