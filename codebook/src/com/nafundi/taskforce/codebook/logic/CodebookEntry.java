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

public class CodebookEntry {

    private String variable;
    // private ArrayList<String> question;
    // private ArrayList<String> value;
    private String question;
    private String value;

    public CodebookEntry() {
        variable = null;
        question = null;
        value = null;
    }

    public String getVariable() {
        return variable;
    }

    public void setVariable(String col1) {
        this.variable = col1;
    }

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    // public ArrayList<String> getQuestion() {
    // return question;
    // }
    // public void setQuestion(ArrayList<String> col2) {
    // this.question = col2;
    // }
    // public ArrayList<String> getValue() {
    // return value;
    // }
    // public void setValue(ArrayList<String> col3) {
    // this.value = col3;
    // }
    //
    // public String getVariableText() {
    // return variable;
    // }
    //
    // public String getQuestionText() {
    // StringBuilder sb = new StringBuilder();
    // for (String s : question) {
    // sb.append(s + "\n");
    // }
    // return sb.toString();
    // }
    //
    // public String getValueText() {
    // StringBuilder sb = new StringBuilder();
    // for (String s : value) {
    // sb.append(s + "\n");
    // }
    // return sb.toString();
    // }
    //

}
