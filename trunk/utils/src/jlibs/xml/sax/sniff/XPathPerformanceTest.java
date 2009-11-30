/**
 * JLibs: Common Utilities for Java
 * Copyright (C) 2009  Santhosh Kumar T
 * <p/>
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 * <p/>
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 */

package jlibs.xml.sax.sniff;

/**
 * @author Santhosh Kumar T
 */
public class XPathPerformanceTest{
    private TestSuite testSuite;

    public XPathPerformanceTest(TestSuite testSuite){
        this.testSuite = testSuite;
    }

    public void run() throws Exception{
        long dogTime = testSuite.usingXMLDog();
        long jdkTime = testSuite.usingJDK();
        System.out.format("Total %d xpaths are executed%n", testSuite.total);

        System.out.format("       jdk time : %d nanoseconds/%.2f seconds %n", jdkTime, jdkTime*1E-09);
        System.out.format("       dog time : %d nanoseconds/%.2f seconds %n", dogTime, dogTime*1E-09);
        double faster = (1.0*Math.max(dogTime, jdkTime)/Math.min(dogTime, jdkTime));
        System.out.format("        WINNER : %s (%.2fx faster) %n", dogTime<=jdkTime ? "XMLDog" : "XALAN", faster);
        long diff = Math.abs(dogTime - jdkTime);
        System.out.format("    Difference : %d nanoseconds/%.2f seconds %n", diff, diff*1E-09);
    }

    public static void main(String[] args) throws Exception{
        TestSuite testSuite = args.length==0 ? new TestSuite() : new TestSuite(args[0]);
        new XPathPerformanceTest(testSuite).run();
    }
}