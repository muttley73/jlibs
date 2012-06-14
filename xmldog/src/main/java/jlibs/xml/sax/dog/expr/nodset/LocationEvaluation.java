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

package jlibs.xml.sax.dog.expr.nodset;

import jlibs.core.util.LongTreeMap;
import jlibs.xml.sax.dog.DataType;
import jlibs.xml.sax.dog.NodeItem;
import jlibs.xml.sax.dog.Scope;
import jlibs.xml.sax.dog.expr.Evaluation;
import jlibs.xml.sax.dog.expr.Expression;
import jlibs.xml.sax.dog.expr.LinkableEvaluation;
import jlibs.xml.sax.dog.path.AxisListener;
import jlibs.xml.sax.dog.path.EventID;
import jlibs.xml.sax.dog.path.Step;
import jlibs.xml.sax.dog.sniff.Event;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Santhosh Kumar T
 */
public final class LocationEvaluation extends AxisListener<LocationExpression> implements NodeSetListener.Support{
    private final Event event;
    private final EventID eventID;
    private final int index;
    private final boolean lastStep;

    private final Step currentStep;
    private final boolean exactPosition;
    private Evaluation predicateEvaluation;
    private Boolean predicateResult = Boolean.TRUE;

    private PositionTracker positionTracker;
    private int predicateChain = -1;

    protected LocationEvaluation(LocationExpression expression, int stepIndex, Event event, EventID eventID){
        super(expression, event.order());
        this.event = event;
        this.eventID = eventID;
        this.index = stepIndex;
        lastStep = index==expression.locationPath.steps.length-1;

        if(expression instanceof Strings)
            stringEvaluations = new ArrayList<Evaluation>();

        currentStep = expression.locationPath.steps[stepIndex];
        exactPosition = currentStep.predicateSet.getPredicate() instanceof ExactPosition;
        if(currentStep.predicateSet.hasPosition)
            positionTracker = new PositionTracker(currentStep.predicateSet.headPositionalPredicate);
    }

    private LocationEvaluation(LocationExpression expression, int stepIndex, Event event, EventID eventID, Expression predicate, Evaluation predicateEvaluation){
        this(expression, stepIndex, event, eventID);
        predicateResult = null;
        if(predicateEvaluation==null)
            this.predicateEvaluation = event.addListener(predicate, this);
        else{
            this.predicateEvaluation = predicateEvaluation;
            predicateEvaluation.addListener(this);
        }
    }

    @Override
    public void start(){
        assert predicateResult!=Boolean.FALSE;
        assert !finished;

        if(event.hasInstantListener(expression)){
            if(listener instanceof LocationEvaluation)
                predicateChain = ((LocationEvaluation)listener).predicateChain;
            else
                predicateChain = 0;
            if(predicateEvaluation!=null)
                predicateChain++;
        }

        if(predicateEvaluation!=null){
            Expression predicate = predicateEvaluation.expression;
            if(predicate.scope()!=Scope.DOCUMENT)
                predicateEvaluation.start();
        }
        eventID.addListener(event, currentStep, this);
    }

    private LinkableEvaluation pendingEvaluationHead, pendingEvaluationTail;
    private List<Evaluation> stringEvaluations;

    @Override
    public void onHit(EventID eventID){
        assert !finished : "getting events even after finish";

        final LocationExpression expression = this.expression;
        if(!lastStep){
            if(eventID.isEmpty(expression.locationPath.steps[index+1].axis))
                return;
        }

        final Event event = this.event;

        if(positionTracker!=null){
            event.positionTrackerStack.addFirst(positionTracker);
            positionTracker.addEvaluation(event);
        }
        LinkableEvaluation childEval = null;

        Expression predicate = currentStep.predicateSet.getPredicate();
        Object predicateResult = predicate==null ? Boolean.TRUE : event.evaluate(predicate);
        if(predicateResult==Boolean.TRUE){
            if(lastStep)
                consume(event);
            else
                childEval = new LocationEvaluation(expression, index+1, event, eventID);
        }else if(predicateResult==null){
            Evaluation predicateEvaluation = event.evaluation;
            if(lastStep){
                childEval = new PredicateEvaluation(expression, event.order(), expression.getResultItem(event), event, predicate, predicateEvaluation);
                if(nodeSetListener !=null)
                    nodeSetListener.mayHit();
            }else
                childEval = new LocationEvaluation(expression, index+1, event, eventID, predicate, predicateEvaluation);
        }

        if(childEval!=null){
            if(childEval instanceof LocationEvaluation)
                ((LocationEvaluation)childEval).nodeSetListener = nodeSetListener;
            else
                ((PredicateEvaluation)childEval).nodeSetListener = nodeSetListener;
            
            childEval.addListener(this);
            if(pendingEvaluationTail!=null){
                pendingEvaluationTail.next = childEval;
                childEval.previous = pendingEvaluationTail;
                pendingEvaluationTail = childEval;
            }else
                pendingEvaluationHead = pendingEvaluationTail = childEval;
            childEval.start();
        }

        if(positionTracker!=null){
            positionTracker.startEvaluation();
            event.positionTrackerStack.pollFirst();
        }
        if(exactPosition && predicateResult==Boolean.TRUE){
            manuallyExpired = true;
            expired();
        }
    }

    /*-------------------------------------------------[ Stages ]---------------------------------------------------*/

    private boolean expired = false;

    @Override
    public void expired(){
        assert !expired;
        expired = true;

        if(positionTracker!=null)
            positionTracker.expired();
        if(pendingEvaluationHead==null)
            resultPrepared();
    }

    private boolean resultPrepared = false;
    private void resultPrepared(){
        if(!resultPrepared){
            manuallyExpired = true;
            resultPrepared = true;

            for(LinkableEvaluation pendingEval=pendingEvaluationHead; pendingEval!=null; pendingEval=pendingEval.next)
                pendingEval.removeListener(this);
            pendingEvaluationHead = pendingEvaluationTail = null;
        }
        if(predicateResult!=null && (index!=0 || (stringEvaluations==null || stringEvaluations.size()==0)))
            finished();
        else if(result.size()==0 && predicateResult==null){ // when result is empty, there is no need to wait for predicateEvaluation to finish
            Expression predicate = predicateEvaluation.expression;
            if(predicate.scope()!=Scope.DOCUMENT)
                predicateEvaluation.removeListener(this);
            else
                event.removeListener(predicate, this);
            finished();
        }
    }

    private boolean finished = false;
    private void finished(){
        if(!finished){
            finished = true;
            for(LinkableEvaluation pendingEval=pendingEvaluationHead; pendingEval!=null; pendingEval=pendingEval.next)
                pendingEval.removeListener(this);
            pendingEvaluationHead = pendingEvaluationTail = null;
            fireFinished();
        }
    }

    @Override
    protected void fireFinished(){
        if(index==0 && nodeSetListener !=null)
            nodeSetListener.finished();
        super.fireFinished();
        if(stringEvaluations!=null){
            for(Evaluation stringEval: stringEvaluations)
                stringEval.removeListener(this);
        }
    }

    @Override
    protected void dispose(){
        if(nodeSetListener !=null){
            for(LongTreeMap.Entry<Object> entry = result.firstEntry(); entry!=null ; entry = entry.next())
                nodeSetListener.discard(entry.getKey());
        }
        manuallyExpired = true;
        for(LinkableEvaluation pendingEval=pendingEvaluationHead; pendingEval!=null; pendingEval=pendingEval.next)
            pendingEval.removeListener(this);
        pendingEvaluationHead = pendingEvaluationTail = null;
        if(predicateResult==null)
            predicateEvaluation.removeListener(this);
    }

    /*-------------------------------------------------[ Result Management ]---------------------------------------------------*/

    private LongTreeMap<Object> result = new LongTreeMap<Object>();

    private void consumedResult(){
        int resultSize = result.size();
        if(resultSize>0 && !expression.many){
            if(resultSize>1)
                result.deleteEntry(result.lastEntry());
            if(expression.first){
                if(pendingEvaluationHead==null || result.firstEntry().getKey()<=pendingEvaluationHead.order)
                    resultPrepared();
                else if(!expired){
                    manuallyExpired = true;
                    expired = true;
                }
            }else
                resultPrepared();
        }else if(expired && pendingEvaluationHead==null)
            resultPrepared();
    }

    private void consume(Event event){
        assert lastStep;
        Object resultItem = expression.getResultItem(event);
        if(resultItem instanceof Evaluation){
            Evaluation eval = (Evaluation)resultItem;
            stringEvaluations.add(eval);
            eval.addListener(this);
            eval.start();
        }else if(predicateChain==0){
            event.onInstantResult(expression, (NodeItem)resultItem);
            resultItem = Event.DUMMY_VALUE;
        }
        assert resultItem!=null : "ResultItem should be non-null";
        result.put(event.order(), resultItem);
        consumedResult();
        if(nodeSetListener !=null)
            nodeSetListener.mayHit();
    }

    private void consumeChildEvaluation(long order, Object resultItem){
        boolean prepareResult = false;
        if(expression.resultType==DataType.NUMBER){
            if(resultItem instanceof Double && ((Double)resultItem).isNaN()){
                result.clear();
                prepareResult = true;
            }
        }

        if(predicateChain==0){
            event.onInstantResult(expression, (NodeItem)resultItem);
            resultItem = Event.DUMMY_VALUE;
        }
        result.put(order, resultItem);
        consumedResult();

        if(prepareResult)
            resultPrepared();
    }

    private void consumeChildEvaluation(LongTreeMap<Object> childResult){
        boolean prepareResult = false;
        int size = childResult.size();
        if(size==1 && expression.resultType==DataType.NUMBER){
            Object resultItem = childResult.firstEntry().value;
            if(resultItem instanceof Double && ((Double)resultItem).isNaN()){
                result.clear();
                prepareResult = true;
            }
        }

        if(size>0){
            if(predicateChain==0)
                fireInstantResult(childResult);
            if(result.size()>0){
                if(nodeSetListener !=null){
                    for(LongTreeMap.Entry<Object> entry = childResult.firstEntry(); entry!=null ; entry = entry.next()){
                        if(result.put(entry.getKey(), entry.value)!=null)
                            nodeSetListener.discard(entry.getKey());
                    }
                }else
                    result.putAll(childResult);
            }else
                result = childResult;
        }
        consumedResult();

        if(prepareResult)
            resultPrepared();
    }

    private void remove(LinkableEvaluation eval){
        LinkableEvaluation prev = eval.previous;
        LinkableEvaluation next = eval.next;

        if(prev!=null)
            prev.next = next;
        else
            pendingEvaluationHead = next;

        if(next!=null)
            next.previous = prev;
        else
            pendingEvaluationTail = prev;
    }

    private void fireInstantResult(LongTreeMap<Object> result){
        LongTreeMap.Entry<Object> entry = result.firstEntry();
        while(entry!=null){
            if(entry.value instanceof NodeItem){
                event.onInstantResult(expression, (NodeItem)entry.value);
                entry.value = Event.DUMMY_VALUE;
            }
            entry = entry.next();
        }
        result.clear(); // to avoid memory leak
    }

    private void decreasePredicateChain(){
        predicateChain--;
        for(LinkableEvaluation pendingEval=pendingEvaluationHead; pendingEval!=null; pendingEval=pendingEval.next){
            if(pendingEval instanceof LocationEvaluation)
                ((LocationEvaluation)pendingEval).decreasePredicateChain();
        }
        if(predicateChain==0)
            fireInstantResult(result);
    }

    @Override
    public final void finished(Evaluation evaluation){
        assert !finished : "can't consume evaluation result after finish";

        if(evaluation==predicateEvaluation){
            predicateResult = (Boolean)evaluation.getResult();
            assert predicateResult!=null : "evaluation result should be non-null";
            if(predicateResult==Boolean.FALSE){
                if(nodeSetListener !=null){
                    for(LongTreeMap.Entry<Object> entry = result.firstEntry(); entry!=null ; entry = entry.next())
                        nodeSetListener.discard(entry.getKey());
                }
                result.clear();
                if(stringEvaluations!=null){
                    for(Evaluation stringEval: stringEvaluations)
                        stringEval.removeListener(this);
                    stringEvaluations = null;
                }
                resultPrepared();
            }else{
                if(predicateChain!=-1)
                    decreasePredicateChain();
                if(resultPrepared)
                    finished();
            }
        }else if(evaluation instanceof PredicateEvaluation){
            PredicateEvaluation predicateEvaluation = (PredicateEvaluation)evaluation;
            remove(predicateEvaluation);

            if(predicateEvaluation.result!=null){
                Object resultItem = predicateEvaluation.result;
                if(resultItem instanceof Evaluation){
                    Evaluation stringEval = (Evaluation)resultItem;
                    stringEvaluations.add(stringEval);
                    stringEval.addListener(this);
                }
                consumeChildEvaluation(predicateEvaluation.order, resultItem);
            }else{
                if(nodeSetListener !=null)
                    nodeSetListener.discard(predicateEvaluation.order);
                consumedResult();
            }
        }else if(evaluation instanceof LocationEvaluation){
            LocationEvaluation locEval = (LocationEvaluation)evaluation;
            remove(locEval);

            if(locEval.stringEvaluations!=null){
                for(Evaluation stringEval: locEval.stringEvaluations)
                    stringEval.addListener(this);
                stringEvaluations.addAll(locEval.stringEvaluations);
            }
            boolean wasExpired = expired;
            consumeChildEvaluation(locEval.result);
            if(!wasExpired && expired){
                assert !finished;
                LinkableEvaluation eval = locEval.next;
                while(eval!=null){
                    eval.removeListener(this);
                    remove(eval);
                    eval = eval.next;
                }
                if(pendingEvaluationHead==null)
                    resultPrepared();
            }
        }else{
            stringEvaluations.remove(evaluation);
            consumeChildEvaluation(evaluation.order, evaluation.getResult());
        }
    }

    private Object finalResult;
    public Object getResult(){
        if(index==0 && predicateChain==0)
            return null;

        if(finalResult==null)
            finalResult = expression.getResult(result);
        return finalResult;
    }

    public NodeSetListener nodeSetListener;

    @Override
    public void setNodeSetListener(NodeSetListener nodeSetListener){
        this.nodeSetListener = nodeSetListener;
    }
}