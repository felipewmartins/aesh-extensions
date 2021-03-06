/*
 * JBoss, Home of Professional Open Source
 * Copyright 2014 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @authors tag. All rights reserved.
 * See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.aesh.extensions.more.aesh;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import org.jboss.aesh.cl.Arguments;
import org.jboss.aesh.cl.CommandDefinition;
import org.jboss.aesh.console.Buffer;
import org.jboss.aesh.console.Config;
import org.jboss.aesh.console.command.Command;
import org.jboss.aesh.console.command.CommandOperation;
import org.jboss.aesh.console.command.CommandResult;
import org.jboss.aesh.console.command.invocation.CommandInvocation;
import org.jboss.aesh.console.man.FileParser;
import org.jboss.aesh.console.man.TerminalPage;
import org.jboss.aesh.console.operator.ControlOperator;
import org.jboss.aesh.extensions.page.SimpleFileParser;
import org.jboss.aesh.io.Resource;
import org.jboss.aesh.terminal.Key;
import org.jboss.aesh.util.ANSI;

/**
 * @author <a href="mailto:stale.pedersen@jboss.org">Ståle W. Pedersen</a>
 */
@CommandDefinition(name="more", description = "is more less?")
public class More implements Command<CommandInvocation> {

    private int rows;
    private int topVisibleRow;
    private int prevTopVisibleRow;
    private StringBuilder number;
    private MorePage page;
    private SimpleFileParser loader;
    private CommandInvocation commandInvocation;
    private ControlOperator operator;

    @Arguments
    private List<Resource> arguments;

    public More() {
        number = new StringBuilder();
    }

    public void setFile(File page) throws IOException {
        loader.setFile(page);
    }

    public void setFile(String filename) throws IOException {
        loader.setFile(new File(filename));
    }

    public void setInput(String input) throws IOException {
        loader.readPageAsString(input);
    }

    public void setInput(InputStream inputStream, String fileName) {
        loader.setFile(inputStream, fileName);
    }

    protected void afterAttach() throws IOException {
        rows = commandInvocation.getShell().getSize().getHeight();
        int columns = commandInvocation.getShell().getSize().getWidth();
        page = new MorePage(loader, columns);

        if(operator.isRedirectionOut() || operator.isPipe()) {
            int count=0;
            for(String line : this.page.getLines()) {
                commandInvocation.getShell().out().print(line);
                count++;
                if(count < this.page.size())
                    commandInvocation.getShell().out().print(Config.getLineSeparator());
            }

            page.clear();
            loader = new SimpleFileParser();
        }
        else {
            if(!page.hasData()) {
                //display help
            }
            else
                display(Background.INVERSE);

            processOperation();
        }
    }

    protected void afterDetach() throws IOException {
        clearNumber();
        topVisibleRow = prevTopVisibleRow = 0;
        if(!operator.isRedirectionOut()) {
            commandInvocation.getShell().out().print(Buffer.printAnsi("K"));
            commandInvocation.getShell().out().print(Buffer.printAnsi("1G"));
            commandInvocation.getShell().out().flush();
        }
        page.clear();
        loader = new SimpleFileParser();
    }

    public void processOperation() throws IOException {
        boolean attach = true;
        try {
            while(attach) {
                CommandOperation operation = commandInvocation.getInput();
                if(operation.getInputKey() == Key.q) {
                    attach = false;
                }
                else if( operation.getInputKey() == Key.ENTER) {
                    topVisibleRow = topVisibleRow + getNumber();
                    if(topVisibleRow > (page.size()-rows)) {
                        topVisibleRow = page.size()-rows;
                        if(topVisibleRow < 0)
                            topVisibleRow = 0;
                        display(Background.INVERSE);
                        attach = false;
                    }
                    else
                        display(Background.INVERSE);
                    clearNumber();
                }
                // ctrl-f ||  space
                else if(operation.getInputKey() == Key.CTRL_F ||
                        operation.getInputKey() == Key.SPACE) {
                    topVisibleRow = topVisibleRow + rows*getNumber();
                    if(topVisibleRow > (page.size()-rows)) {
                        topVisibleRow = page.size()-rows;
                        if(topVisibleRow < 0)
                            topVisibleRow = 0;
                        display(Background.INVERSE);
                        attach = false;
                    }
                    else
                        display(Background.INVERSE);
                    clearNumber();
                }
                else if(operation.getInputKey() == Key.CTRL_B) { // ctrl-b
                    topVisibleRow = topVisibleRow - rows*getNumber();
                    if(topVisibleRow < 0)
                        topVisibleRow = 0;
                    display(Background.INVERSE);
                    clearNumber();
                }
                else if(Character.isDigit(operation.getInput()[0])) {
                    number.append(Character.getNumericValue(operation.getInput()[0]));
                }
            }
        }
        catch (InterruptedException ie) {
        }
        afterDetach();
    }

    private void display(Background background) throws IOException {
        //commandInvocation.clear();
        commandInvocation.getShell().out().print(Buffer.printAnsi("0G"));
        commandInvocation.getShell().out().print(Buffer.printAnsi("2K"));
        if(prevTopVisibleRow == 0 && topVisibleRow == 0) {
            for(int i=topVisibleRow; i < (topVisibleRow+rows); i++) {
                if(i < page.size()) {
                    commandInvocation.getShell().out().print(page.getLine(i));
                    commandInvocation.getShell().out().print(Config.getLineSeparator());
                }
            }
        }
        else if(prevTopVisibleRow < topVisibleRow) {

            for(int i=prevTopVisibleRow; i < topVisibleRow; i++) {
                commandInvocation.getShell().out().print(page.getLine(i + rows));
                commandInvocation.getShell().out().print(Config.getLineSeparator());

            }
            prevTopVisibleRow = topVisibleRow;

        }
        else if(prevTopVisibleRow > topVisibleRow) {
            for(int i=topVisibleRow; i < (topVisibleRow+rows); i++) {
                if(i < page.size()) {
                    commandInvocation.getShell().out().print(page.getLine(i));
                    commandInvocation.getShell().out().print(Config.getLineSeparator());
                }
            }
            prevTopVisibleRow = topVisibleRow;
        }
        displayBottom(background);
    }

    private void displayBottom(Background background) throws IOException {
        if(background == Background.INVERSE) {
            commandInvocation.getShell().out().print(ANSI.INVERT_BACKGROUND);
            commandInvocation.getShell().out().print("--More--(");
            commandInvocation.getShell().out().print(getPercentDisplayed()+"%)");

            commandInvocation.getShell().out().print(ANSI.NORMAL_BACKGROUND);
            commandInvocation.getShell().out().flush();
        }
    }

    private String getPercentDisplayed() {
        double row = topVisibleRow  + rows;
        if(row > this.page.size())
            row  = this.page.size();
        return String.valueOf((int) ((row / this.page.size()) * 100));
    }

    public void displayHelp() throws IOException {
        commandInvocation.getShell().out().println(Config.getLineSeparator()
                +"Usage: more [options] file...");
    }

    private int getNumber() {
        if(number.length() > 0)
            return Integer.parseInt(number.toString());
        else
            return 1;
    }

    private void clearNumber() {
        number = new StringBuilder();
    }

    @Override
    public CommandResult execute(CommandInvocation commandInvocation) throws IOException {
        this.commandInvocation = commandInvocation;
        this.operator = commandInvocation.getControlOperator();
        loader = new SimpleFileParser();

        if(commandInvocation.getShell().in().getStdIn().available() > 0) {
            java.util.Scanner s = new java.util.Scanner(commandInvocation.getShell().in().getStdIn()).useDelimiter("\\A");
            String fileContent = s.hasNext() ? s.next() : "";
            setInput(fileContent);
            afterAttach();
        }
        else if(arguments != null && arguments.size() > 0) {
            Resource f = arguments.get(0);
            f = f.resolve(commandInvocation.getAeshContext().getCurrentWorkingDirectory()).get(0);
            if(f.isLeaf()) {
                setInput(f.read(), f.getName());
                afterAttach();
            }
            else if(f.isDirectory()) {
                commandInvocation.getShell().err().println(f.getAbsolutePath()+": is a directory");
            }
            else {
                commandInvocation.getShell().err().println(f.getAbsolutePath() + ": No such file or directory");
            }
        }

        return CommandResult.SUCCESS;
    }

    private static enum Background {
        NORMAL,
        INVERSE
    }

    private class MorePage extends TerminalPage {

        public MorePage(FileParser fileParser, int columns) throws IOException {
            super(fileParser, columns);
        }

    }
}
