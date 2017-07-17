/*
 * Copyright (c)  2016, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.extension.siddhi.gpl.execution.nlp;

import org.apache.log4j.Logger;
import org.wso2.extension.siddhi.gpl.execution.nlp.dictionary.Dictionary;
import org.wso2.extension.siddhi.gpl.execution.nlp.utility.Constants;
import org.wso2.siddhi.annotation.Example;
import org.wso2.siddhi.annotation.Extension;
import org.wso2.siddhi.annotation.Parameter;
import org.wso2.siddhi.annotation.ReturnAttribute;
import org.wso2.siddhi.annotation.util.DataType;
import org.wso2.siddhi.core.config.SiddhiAppContext;
import org.wso2.siddhi.core.event.ComplexEventChunk;
import org.wso2.siddhi.core.event.stream.StreamEvent;
import org.wso2.siddhi.core.event.stream.StreamEventCloner;
import org.wso2.siddhi.core.event.stream.populater.ComplexEventPopulater;
import org.wso2.siddhi.core.exception.SiddhiAppCreationException;
import org.wso2.siddhi.core.executor.ConstantExpressionExecutor;
import org.wso2.siddhi.core.executor.ExpressionExecutor;
import org.wso2.siddhi.core.executor.VariableExpressionExecutor;
import org.wso2.siddhi.core.query.processor.Processor;
import org.wso2.siddhi.core.query.processor.stream.StreamProcessor;
import org.wso2.siddhi.core.util.config.ConfigReader;
import org.wso2.siddhi.query.api.definition.AbstractDefinition;
import org.wso2.siddhi.query.api.definition.Attribute;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Implementation for NameEntityTypeViaDictionaryStreamProcessor.
 */
@Extension(
        name = "findNameEntityTypeViaDictionary",
        namespace = "nlp",
        description = "Find the entities in the text which has been defined in the dictionary.",
        parameters = {
                @Parameter(
                        name = "entity.type",
                        description = "User given string constant as entity Type. Possible Values : PERSON, " +
                                "LOCATION, ORGANIZATION, MONEY, PERCENT, DATE or TIME",
                        type = {DataType.STRING}
                ),
                @Parameter(
                        name = "dictionary.file.path",
                        description = "path to the dictionary which expected entities for the entity types " +
                                "and the dictionary should be in the following form,\n" +
                                "<dictionary>" +
                                "<entity id=\"PERSON\">" +
                                "<entry>Bill</entry>" +
                                "<entry>Addison</entry>" +
                                "</entity>" +
                                "</dictionary>",
                        type = {DataType.STRING}
                ),
                @Parameter(
                        name = "text",
                        description = "A string or the stream attribute which the text stream resides.",
                        type = {DataType.STRING}
                )
        },
        returnAttributes = {
                @ReturnAttribute(
                        name = "match",
                        description = "Event returns a single match. If multiple matches are found multiple events " +
                                "are returned each containing a single match.",
                        type = {DataType.STRING}
                )
        },
        examples = {
                @Example(
                        syntax = "nlp:findNameEntityTypeViaDictionary(\"PERSON\",\"dictionary.xml\",text)",
                        description = "If the text attribute contains \"Bill Gates donates £31million to fight " +
                                "Ebola\", and the dictionary consists of the above entries , the result will be " +
                                "\"Bill\"."
                )
        }
)
public class NameEntityTypeViaDictionaryStreamProcessor extends StreamProcessor {

    private static Logger logger = Logger.getLogger(NameEntityTypeViaDictionaryStreamProcessor.class);

    private Constants.EntityType entityType;
    private Dictionary dictionary;

    @Override
    protected List<Attribute> init(AbstractDefinition inputDefinition,
                                   ExpressionExecutor[] attributeExpressionExecutors,
                                   ConfigReader configReader, SiddhiAppContext siddhiAppContext) {

        if (attributeExpressionLength < 3) {
            throw new SiddhiAppCreationException("Query expects at least three parameters. Received only " +
                    attributeExpressionLength + ".\nUsage: #nlp:findNameEntityTypeViaDictionary(entityType:string, " +
                    "dictionaryFilePath:string, text:string-variable)");
        }

        String entityTypeParam;
        try {
            if (attributeExpressionExecutors[0] instanceof ConstantExpressionExecutor) {
                entityTypeParam = (String) attributeExpressionExecutors[0].execute(null);
            } else {
                throw new SiddhiAppCreationException("First parameter should be a constant.");
            }
        } catch (ClassCastException e) {
            throw new SiddhiAppCreationException("First parameter should be of type string. Found " +
                    attributeExpressionExecutors[0].getReturnType() +
                    ".\nUsage: findNameEntityTypeViaDictionary(entityType:string, " +
                    "dictionaryFilePath:string, text:string-variable");
        }

        try {
            this.entityType = Constants.EntityType.valueOf(entityTypeParam.toUpperCase(Locale.ENGLISH));
        } catch (IllegalArgumentException e) {
            throw new SiddhiAppCreationException("First parameter should be one of " + Arrays.deepToString(Constants
                    .EntityType.values()) + ". Found " + entityTypeParam);
        }

        String dictionaryFilePath;
        try {
            if (attributeExpressionExecutors[1] instanceof ConstantExpressionExecutor) {
                dictionaryFilePath = (String) attributeExpressionExecutors[1].execute(null);
            } else {
                throw new SiddhiAppCreationException("Second parameter should be a constant.");
            }
        } catch (ClassCastException e) {
            throw new SiddhiAppCreationException("Second parameter should be of type string. Found " +
                    attributeExpressionExecutors[0].getReturnType() +
                    ".\nUsage: findNameEntityTypeViaDictionary(entityType:string, " +
                    "dictionaryFilePath:string, text:string-variable");
        }

        try {
            dictionary = new Dictionary(entityType, dictionaryFilePath);
        } catch (Exception e) {
            throw new SiddhiAppCreationException("Failed to initialize dictionary.", e);
        }

        if (!(attributeExpressionExecutors[2] instanceof VariableExpressionExecutor)) {
            throw new SiddhiAppCreationException("Third parameter should be a variable. Found " +
                    attributeExpressionExecutors[2].getReturnType() +
                    ".\nUsage: findNameEntityTypeViaDictionary(entityType:string, " +
                    "dictionaryFilePath:string, text:string-variable)");
        }

        if (logger.isDebugEnabled()) {
            logger.debug(String.format("Query parameters initialized. EntityType: %s DictionaryFilePath: %s " +
                            "Stream Parameters: %s", entityTypeParam, dictionaryFilePath,
                    inputDefinition.getAttributeList()));
        }

        ArrayList<Attribute> attributes = new ArrayList<Attribute>(1);
        attributes.add(new Attribute("match", Attribute.Type.STRING));
        return attributes;
    }


    @Override
    protected void process(ComplexEventChunk<StreamEvent> streamEventChunk, Processor nextProcessor,
                           StreamEventCloner streamEventCloner, ComplexEventPopulater complexEventPopulater) {
        synchronized (this) {
            while (streamEventChunk.hasNext()) {
                StreamEvent streamEvent = streamEventChunk.next();
                if (logger.isDebugEnabled()) {
                    logger.debug(String.format("Event received. Entity Type:%s DictionaryFilePath:%s Event:%s",
                            entityType.name(), dictionary.getXmlFilePath(), streamEvent));
                }

                String text = attributeExpressionExecutors[2].execute(streamEvent).toString();
                ArrayList<String> dictionaryEntries = dictionary.getEntries(entityType);

                if (dictionaryEntries.size() == 0) {
                    streamEventChunk.remove();
                } else if (dictionaryEntries.size() == 1) {
                    complexEventPopulater.populateComplexEvent(streamEvent, new Object[]{dictionaryEntries.get(0)});
                } else {
                    for (String entry : dictionaryEntries) {
                        if (text.contains(entry)) {
                            StreamEvent newStreamEvent = streamEventCloner.copyStreamEvent(streamEvent);
                            complexEventPopulater.populateComplexEvent(newStreamEvent, new Object[]{entry});
                            streamEventChunk.insertBeforeCurrent(newStreamEvent);
                        }
                    }
                    streamEventChunk.remove();
                }
            }
        }
        nextProcessor.process(streamEventChunk);
    }


    @Override
    public void start() {

    }

    @Override
    public void stop() {

    }

    @Override
    public Map<String, Object> currentState() {
        return new HashMap<>();
    }

    @Override
    public void restoreState(Map<String, Object> state) {

    }

}
