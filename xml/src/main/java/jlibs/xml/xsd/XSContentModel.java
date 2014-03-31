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

package jlibs.xml.xsd;

import jlibs.core.graph.Path;
import jlibs.core.graph.Processor;
import jlibs.core.graph.WalkerUtil;
import jlibs.core.graph.visitors.ReflectionVisitor;
import jlibs.core.graph.walkers.PreorderWalker;
import jlibs.xml.sax.XMLDocument;
import org.apache.xerces.xs.XSComplexTypeDefinition;
import org.apache.xerces.xs.XSElementDeclaration;
import org.apache.xerces.xs.XSModelGroup;
import org.apache.xerces.xs.XSParticle;

/**
 * @author Santhosh Kumar T
 */
public class XSContentModel extends ReflectionVisitor<Object, Processor<Object>>{
    private StringBuilder buff = new StringBuilder();
    private XMLDocument doc;
    
    @Override
    protected Processor<Object> getDefault(Object elem){
        return null;
    }

    protected Processor process(XSModelGroup modelGroup){
        return modelGroupProcessor;
    }

    protected Processor process(XSElementDeclaration elem){
        return elemProcessor;
    }

    private void appendCompositor(Path path){
        if(buff.length()>0 && buff.charAt(buff.length()-1)!='('){
            XSModelGroup modelGroup = (XSModelGroup)path.getParentPath(XSModelGroup.class).getElement();
            switch(modelGroup.getCompositor()){
                case XSModelGroup.COMPOSITOR_SEQUENCE:
                    buff.append(" , ");
                    break;
                case XSModelGroup.COMPOSITOR_ALL:
                    buff.append(" ; ");
                    break;
                case XSModelGroup.COMPOSITOR_CHOICE:
                    buff.append(" | ");
                    break;
            }
        }
    }
    
    private void appendCardinality(Path path){
        path = path.getParentPath(XSParticle.class);
        if(path!=null){
            XSParticle particle = (XSParticle)path.getElement();
            if(particle.getMinOccurs()==0 && particle.getMaxOccursUnbounded())
                buff.append("*");
            else if(particle.getMinOccurs()==1 && particle.getMaxOccursUnbounded())
                buff.append("+");
            else if(particle.getMaxOccursUnbounded())
                buff.append(particle.getMinOccurs()).append("+");
            else if(particle.getMinOccurs()==0 && particle.getMaxOccurs()==1)
                buff.append("?");
            else if(particle.getMinOccurs()!=1 && particle.getMaxOccurs()!=-1)
                buff.append("[").append(particle.getMinOccurs()).append(",").append(particle.getMaxOccurs()).append("]");
        }
    }

    private Processor<XSModelGroup> modelGroupProcessor = new Processor<XSModelGroup>(){
        @Override
        public boolean preProcess(XSModelGroup modelGroup, Path path){
            appendCompositor(path);
            buff.append('(');
            return true;
        }

        @Override
        public void postProcess(XSModelGroup modelGroup, Path path){
            buff.append(')');
            appendCardinality(path);
        }
    };

    private Processor<XSElementDeclaration> elemProcessor = new Processor<XSElementDeclaration>(){
        @Override
        public boolean preProcess(XSElementDeclaration elem, Path path){
            appendCompositor(path);
            String uri = elem.getNamespace()==null ? "" : elem.getNamespace();
            doc.declarePrefix(uri);
            buff.append(doc.toQName(uri, elem.getName()));
            appendCardinality(path);
            return false;
        }

        @Override
        public void postProcess(XSElementDeclaration elem, Path path){}
    };

    @SuppressWarnings("unchecked")
    public String toString(XSComplexTypeDefinition complexType, XMLDocument doc){
        buff.setLength(0);
        this.doc = doc;
        WalkerUtil.walk(new PreorderWalker(complexType, new XSNavigator()), this);
        this.doc = null;
        return buff.toString();
    }
}
