/**
 * JLibs: Common Utilities for Java
 * Copyright (C) 2009  Santhosh Kumar T <santhosh.tekuri@gmail.com>
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 */

package jlibs.examples.xml.sax.dog;

import jlibs.core.util.LongTreeMap;
import jlibs.examples.xml.sax.dog.engines.SaxonEngine;
import jlibs.xml.DefaultNamespaceContext;
import jlibs.xml.sax.dog.*;
import jlibs.xml.sax.dog.expr.Evaluation;
import jlibs.xml.sax.dog.expr.EvaluationListener;
import jlibs.xml.sax.dog.expr.Expression;
import jlibs.xml.sax.dog.expr.InstantEvaluationListener;
import jlibs.xml.sax.dog.sniff.DOMBuilder;
import jlibs.xml.sax.dog.sniff.Event;
import jlibs.xml.xpath.DefaultXPathVariableResolver;
import org.w3c.dom.Node;

import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFunctionResolver;
import java.util.*;

/**
 * @author Santhosh Kumar T
 */
public class TestCase{
    public static boolean useSTAX = false;
    public static boolean useXMLBuilder = false;
    public static boolean useInstantResults = false;
    public static XPathEngine domEngine =
//            new JDKEngine(new com.sun.org.apache.xpath.internal.jaxp.XPathFactoryImpl());
//            new JDKEngine(new org.apache.xpath.jaxp.XPathFactoryImpl());
//            new JDKEngine(new net.sf.saxon.xpath.XPathFactoryImpl());
            new SaxonEngine();
//            new JaxenEngine();

    public String file;
    public List<XPathInfo> xpaths = new ArrayList<XPathInfo>();
    public DefaultNamespaceContext nsContext = new DefaultNamespaceContext();
    public DefaultNamespaceContext resultNSContext = new DefaultNamespaceContext();
    public DefaultXPathVariableResolver variableResolver = new DefaultXPathVariableResolver();
    public XPathFunctionResolver functionResolver = new TestFunctionResolver();

    public List<Object> jdkResult = new ArrayList<Object>(xpaths.size());
    public List<Object> usingDOM() throws Exception{
        return jdkResult = domEngine.evaluate(this, file);
    }

    public List<Object> dogResult;
    public List<Object> usingXMLDog() throws Exception{
        final XMLDog dog = new XMLDog(nsContext, variableResolver, functionResolver);
        Expression expressions[] = new Expression[xpaths.size()];
        for(int i=0; i<xpaths.size(); i++){
            XPathInfo xpathInfo = xpaths.get(i);
            expressions[i] = xpathInfo.forEach==null ? dog.addXPath(xpathInfo.xpath) : dog.addForEach(xpathInfo.forEach, xpathInfo.xpath);
        }

        Event event = dog.createEvent();
        if(useXMLBuilder)
            event.setXMLBuilder(new DOMBuilder());

        dogResult = new ArrayList<Object>(xpaths.size());
        final Object instantResults[] = new Object[dog.getDocumentXPathsCount()];
        EvaluationListener listener;
        if(useInstantResults){
            listener = new InstantEvaluationListener(){
                @Override
                public void onNodeHit(Expression expression, NodeItem nodeItem){
                    LongTreeMap<NodeItem> map = (LongTreeMap<NodeItem>)instantResults[expression.id];
                    if(map==null)
                        instantResults[expression.id] = map = new LongTreeMap<NodeItem>();
                    map.put(nodeItem.order, nodeItem);
                }

                @Override
                public void finished(Evaluation evaluation){
                    Object result = evaluation.getResult();
                    if(result==null){
                        if(instantResults[evaluation.expression.id]==null)
                            instantResults[evaluation.expression.id] = new LongTreeMap<NodeItem>();
                    }else
                        instantResults[evaluation.expression.id] = result;
                }
            };
            for(Expression expr: dog.getXPaths()){
                if(expr.scope()==Scope.DOCUMENT){
                    event.addListener(expr, listener);
                    dogResult.add(null);
                }else
                    dogResult.add(expr.getResult());
            }
            dog.sniff(event, file, useSTAX);
            int i=0;
            for(Expression expr: expressions){
                if(expr.scope()==Scope.DOCUMENT){
                    Object result = instantResults[expr.id];
                    if(result instanceof LongTreeMap)
                        result = new ArrayList(((LongTreeMap)result).values());
                    dogResult.set(i, result);
                }else if(useXMLBuilder){
                    if(expr.resultType==DataType.NODESET){
                        List<NodeItem> list = (List<NodeItem>)expr.getResult();
                        if(list.size()==1 && list.get(0).type==NodeType.DOCUMENT)
                            dogResult.set(i, Collections.singletonList(event.documentNodeItem()));
                    }
                }
                i++;
            }
        }else{
            XPathResults dogResults = new XPathResults(event, dog.getDocumentXPathsCount(), dog.getXPaths());
            dog.sniff(event, file, useSTAX);
            for(Expression expr: expressions)
                dogResult.add(dogResults.getResult(expr));
        }
        resultNSContext = (DefaultNamespaceContext)event.getNamespaceContext();
        return dogResult;
    }

    public void translateDOMResult(int test){
        Object obj = jdkResult.get(test);
        List<?> result = domEngine.translate(obj, resultNSContext);
        jdkResult.set(test, result);

        xpaths.get(test).hasAttributes = has(result, NodeItem.ATTRIBUTE);
        xpaths.get(test).hasNamespaces = has(result, NodeItem.NAMESPACE);
    }

    private boolean has(List list, int nodeItemType){
        for(Object item: list){
            if(item instanceof NodeItem){
                NodeItem nodeItem = (NodeItem)item;
                if(nodeItem.type==nodeItemType)
                    return true;
            }else if(has((List)item, nodeItemType))
                return true;
        }
        return false;
    }

    private List<Integer> translated = new ArrayList<Integer>();
    public Object jdkResults(int i){
        if(!translated.contains(i) && xpaths.get(i).resultType.equals(XPathConstants.NODESET)){
            translateDOMResult(i);
            translated.add(i);
        }
        return jdkResult.get(i);
    }

    public Object dogResults(int i){
        return dogResult.get(i);
    }

    Comparator<NodeItem> attrComparator = new Comparator<NodeItem>(){
        @Override
        public int compare(NodeItem item1, NodeItem item2){
            return item1.location.compareTo(item2.location);
        }
    };

    Comparator<NodeItem> nsComparator = new Comparator<NodeItem>(){
        @Override
        public int compare(NodeItem item1, NodeItem item2){
            String location1 = item1.location.substring(item1.location.lastIndexOf('/'));
            String location2 = item2.location.substring(item2.location.lastIndexOf('/'));
            return location1.compareTo(location2);
        }
    };

    @SuppressWarnings({"unchecked"})
    public boolean passed(int i){
        Object jdkResults = jdkResults(i);
        Object dogResults = dogResults(i);

        if(jdkResults instanceof TreeSet)
            jdkResult.set(i, jdkResults = new ArrayList((TreeSet)jdkResults));
        if(dogResults instanceof TreeSet)
            dogResult.set(i, dogResults = new ArrayList((TreeSet)dogResults));

        if(xpaths.get(i).hasAttributes){
            sort((List)jdkResults, attrComparator);
            sort((List)dogResults, attrComparator);
        }else if(xpaths.get(i).hasNamespaces){
            sort((List)jdkResults, nsComparator);
            sort((List)dogResults, nsComparator);
        }

        if(jdkResults instanceof List)
            return equals((List<NodeItem>)jdkResults, (List<NodeItem>)dogResults);
        else
            return jdkResults.equals(dogResults);
    }

    @SuppressWarnings({"unchecked"})
    private void sort(List list, Comparator comparator){
        if(list.size()==0)
            return;
        if(list.get(0) instanceof List){
            for(Object item: list)
                sort((List)item, comparator);
        }else
            Collections.sort(list, comparator);
    }

    @SuppressWarnings({"unchecked"})
    private boolean equals(List<NodeItem> jdkList, List<NodeItem> dogList){
        if(jdkList.size()!=dogList.size())
            return false;

        Iterator jdkIter = jdkList.iterator();
        Iterator dogIter = dogList.iterator();
        while(jdkIter.hasNext()){
            Object jdkItem = jdkIter.next();
            Object dogItem = dogIter.next();

            if(jdkItem instanceof List){
                if(!equals((List)jdkItem, (List)dogItem))
                    return false;
            }else if(jdkItem instanceof NodeItem){
                NodeItem jdkNodeItem = (NodeItem)jdkItem;
                NodeItem dogNodeItem = (NodeItem)dogItem;
                String jdkLocation = jdkNodeItem.location;
                String dogLocation = dogNodeItem.location;
                if(dogNodeItem.type==NodeItem.NAMESPACE){
                    jdkLocation = jdkLocation.substring(jdkLocation.lastIndexOf('/'));
                    dogLocation = dogLocation.substring(dogLocation.lastIndexOf('/'));
                }
                if(!jdkLocation.equals(dogLocation))
                    return false;

                if(useXMLBuilder && !domEngine.equals(jdkNodeItem.xml, (Node)dogNodeItem.xml))
                    return false;
            }else{
                if(!jdkItem.equals(dogItem))
                    return false;
            }
        }

        return true;
    }

    /*-------------------------------------------------[ Printing ]---------------------------------------------------*/

    @SuppressWarnings({"unchecked"})
    public static int printResults(Object result){
        if(result instanceof Collection)
            return printResults((Collection)result);
        else{
            System.out.println(result);
            return -1;
        }
    }

    public static int printResults(Collection results){
        boolean first = true;
        for(Object item: results){
            if(first)
               first = false;
            else
                System.out.print(", ");
            System.out.print(item);
        }
        System.out.println();
        return results.size();
    }

    public void printResults(int i){
        System.out.println("            file : "+file);
        System.out.println("           xpath : "+xpaths.get(i));
        System.out.print("    "+TestCase.domEngine.getName()+" result : ");
        int count = printResults(jdkResults(i));
        if(count!=-1)
            System.out.println(" "+TestCase.domEngine.getName()+" hit-count : "+count);
        jdkResult.set(i, null);

        System.out.print("   XMLDog result : ");
        count = printResults(dogResults(i));
        if(count!=-1)
            System.out.println("XMLDog hit-count : "+count);
        dogResult.set(i, null);

        System.out.println("-------------------------------------------------");
    }
}