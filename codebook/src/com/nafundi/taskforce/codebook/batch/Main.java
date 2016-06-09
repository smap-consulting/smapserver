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

package com.nafundi.taskforce.codebook.batch;

import com.nafundi.taskforce.codebook.logic.CodebookEngine;
import com.nafundi.taskforce.codebook.logic.CodebookEngineSmap;
import com.nafundi.taskforce.codebook.logic.CodebookEntry;
import com.nafundi.taskforce.codebook.logic.CodebookMaker;
import com.nafundi.taskforce.codebook.logic.CodebookMakerSmap;

import javax.swing.*;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class Main {

    private static final String APP_NAME = "Codebook v1.1 - Smap 0.1";
    private JTextArea statusLog;

    private Main(String filePath, String language) {
    	try {
    		System.out.println("File path:" + filePath);
			makeCodebook(new File(filePath), language);
		} catch (Exception e) {
			e.printStackTrace();
		}
    }

    public static void main(String[] args) {

    	String language = null;
    	if(args.length > 0) {
    		if(args.length > 1) {
    			language = args[1];
    		} else {
    			language = "_default";
    		}
    		System.out.println(args[0] + " : " + language);
    		new Main(args[0], language);  
    	} else {
    		System.out.println("Error: please provide an xform to convert");
    	}

    }
  
    private void makeCodebook(File inputFile, String language) throws Exception {		// smap added language

        String filenameWithExtension = inputFile.getName();
        String inputFilename = filenameWithExtension.substring(0,
                filenameWithExtension.lastIndexOf('.'));
        String outputFolderpath = inputFile.getParentFile().getAbsolutePath();

        CodebookEngineSmap ce = new CodebookEngineSmap(inputFile.getAbsolutePath(), language);
        ArrayList<CodebookEntry> entry = ce.getEntry();

        CodebookMakerSmap maker = new CodebookMakerSmap(entry, language,
                inputFilename, outputFolderpath); 
 
        maker.writeFile();
       

    }

    private void appendToStatus(String text) {
    	if(statusLog != null) {
    		statusLog.setText(statusLog.getText() + text + "\n");
    	} else {
    		System.out.println(text);
    	}
    }


}
