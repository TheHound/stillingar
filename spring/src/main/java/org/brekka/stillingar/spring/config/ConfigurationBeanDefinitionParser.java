/*
 * Copyright 2011 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.brekka.stillingar.spring.config;

import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.brekka.stillingar.core.ConfigurationException;
import org.brekka.stillingar.core.properties.PropertiesConfigurationSourceLoader;
import org.brekka.stillingar.core.snapshot.SnapshotBasedConfigurationSource;
import org.brekka.stillingar.spring.ConfigurationBeanPostProcessor;
import org.brekka.stillingar.spring.ConfigurationPlaceholderConfigurer;
import org.brekka.stillingar.spring.DefaultConfigurationSourceFactoryBean;
import org.brekka.stillingar.spring.LoggingSnapshotEventHandler;
import org.brekka.stillingar.spring.resource.BasicResourceNameResolver;
import org.brekka.stillingar.spring.resource.FixedResourceSelector;
import org.brekka.stillingar.spring.resource.ScanningResourceSelector;
import org.brekka.stillingar.spring.resource.VersionedResourceNameResolver;
import org.brekka.stillingar.spring.resource.dir.EnvironmentVariableDirectory;
import org.brekka.stillingar.spring.resource.dir.HomeDirectory;
import org.brekka.stillingar.spring.resource.dir.PlatformDirectory;
import org.brekka.stillingar.spring.resource.dir.SystemPropertyDirectory;
import org.brekka.stillingar.spring.snapshot.ConfigurationSnapshotRefresher;
import org.brekka.stillingar.spring.snapshot.ResourceSnapshotManager;
import org.brekka.stillingar.spring.version.ApplicationVersionFromMaven;
import org.brekka.stillingar.spring.xmlbeans.ApplicationContextConverter;
import org.springframework.beans.factory.parsing.BeanComponentDefinition;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.ManagedList;
import org.springframework.beans.factory.support.ManagedMap;
import org.springframework.beans.factory.xml.AbstractSingleBeanDefinitionParser;
import org.springframework.beans.factory.xml.ParserContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.scheduling.concurrent.ScheduledExecutorFactoryBean;
import org.springframework.scheduling.concurrent.ScheduledExecutorTask;
import org.springframework.util.PropertyPlaceholderHelper;
import org.springframework.util.xml.SimpleNamespaceContext;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * TODO
 * 
 * @author Andrew Taylor
 */
public class ConfigurationBeanDefinitionParser extends AbstractSingleBeanDefinitionParser {

    private static final int MINIMUM_RELOAD_INTERVAL = 500;

    @Override
    protected Class<SnapshotBasedConfigurationSource> getBeanClass(Element element) {
        return SnapshotBasedConfigurationSource.class;
    }

    @Override
    protected void doParse(Element element, ParserContext parserContext, BeanDefinitionBuilder builder) {

        Engine engine = determineEngine(element);

        builder.addConstructorArgValue(prepareResourceManager(element, engine));
        builder.addConstructorArgValue(prepareDefaultConfigurationSource(element, engine));
        builder.addConstructorArgValue(prepareSnapshotEventHandler(element));
        builder.getRawBeanDefinition().setInitMethodName("init");

        // Other identifiable context beans
        prepareLoader(element, parserContext, engine);
        preparePlaceholderConfigurer(element, parserContext);
        preparePostProcessor(element, parserContext);
        prepareReloadMechanism(element, parserContext);
    }

    protected void preparePostProcessor(Element element, ParserContext parserContext) {
        String id = element.getAttribute("id");
        BeanDefinitionBuilder postProcessor = BeanDefinitionBuilder
                .genericBeanDefinition(ConfigurationBeanPostProcessor.class);
        postProcessor.addConstructorArgReference(id);
        parserContext.registerBeanComponent(new BeanComponentDefinition(postProcessor.getBeanDefinition(), id
                + "-postProcessor"));
    }

    /**
     * @param element
     * @param parserContext
     */
    protected void preparePlaceholderConfigurer(Element element, ParserContext parserContext) {
        Element placeholderElement = selectSingleChildElement(element, "property-placeholder", true);
        if (placeholderElement != null) {
            String id = element.getAttribute("id");
            String name = getName(element);
            String prefix = placeholderElement.getAttribute("prefix");
            String suffix = placeholderElement.getAttribute("suffix");

            BeanDefinitionBuilder placeholderConfigurer = BeanDefinitionBuilder
                    .genericBeanDefinition(ConfigurationPlaceholderConfigurer.class);
            placeholderConfigurer.addConstructorArgReference(id);

            if (prefix == null || prefix.isEmpty()) {
                prefix = "$" + name + "{";
            }
            if (suffix == null || suffix.isEmpty()) {
                suffix = "}";
            }
            BeanDefinitionBuilder placeholderHelper = BeanDefinitionBuilder
                    .genericBeanDefinition(PropertyPlaceholderHelper.class);
            placeholderHelper.addConstructorArgValue(prefix);
            placeholderHelper.addConstructorArgValue(suffix);

            placeholderConfigurer.addPropertyValue("placeholderHelper", placeholderHelper.getBeanDefinition());

            parserContext.registerBeanComponent(new BeanComponentDefinition(placeholderConfigurer.getBeanDefinition(),
                    id + "-placeholderConfigurer"));
        }
    }

    /**
     * @param element
     * @param parserContext
     */
    protected void prepareLoader(Element element, ParserContext parserContext, Engine engine) {
        String loaderReference = getLoaderReference(element);
        BeanDefinitionBuilder builder = BeanDefinitionBuilder.genericBeanDefinition(engine.getLoaderClassName());

        switch (engine) {
            case XMLBEANS:
                builder.addConstructorArgValue(prepareConversionManager());
                prepareXMLBeanNamespaces(element, builder);
                break;
            case JAXB:
                prepareJAXB(element, builder);
                break;
            case PROPS:
                // No extra handling for properties
                break;
            default:
                // No special requirements
                break;
        }

        parserContext.registerBeanComponent(new BeanComponentDefinition(builder.getBeanDefinition(), loaderReference));
    }

    /**
     * @param element
     * @param builder
     */
    protected void prepareJAXB(Element element, BeanDefinitionBuilder builder) {
        Element jaxbElement = selectSingleChildElement(element, "jaxb", false);
        
        // Path
        String contextPath = jaxbElement.getAttribute("context-path");
        builder.addConstructorArgValue(contextPath);
        
        // Schemas
        List<Element> schemaElementList = selectChildElements(jaxbElement, "schema");
        ManagedList<URL> schemaUrlList = new ManagedList<URL>(schemaElementList.size());
        for (Element schemaElement : schemaElementList) {
            String schemaPath = schemaElement.getTextContent();
            try {
                URL url = new URL(schemaPath);
                schemaUrlList.add(url);
            } catch (MalformedURLException e) {
                throw new ConfigurationException(String.format(
                        "Failed to parse schema location '%s'", schemaPath), e);
            }
        }
        builder.addConstructorArgValue(schemaUrlList);
        
        // Namespaces
        builder.addConstructorArgValue(prepareJAXBNamespaces(element));
    }

    /**
     * @param element
     * @return
     */
    protected AbstractBeanDefinition prepareJAXBNamespaces(Element element) {
        BeanDefinitionBuilder builder = BeanDefinitionBuilder.genericBeanDefinition(SimpleNamespaceContext.class);
        builder.addPropertyValue("bindings", toNamespaceMap(element));
        return builder.getBeanDefinition();
    }

    protected void prepareXMLBeanNamespaces(Element element, BeanDefinitionBuilder builder) {
        ManagedMap<String, String> namespaceMap = toNamespaceMap(element);
        if (!namespaceMap.isEmpty()) {
            builder.addPropertyValue("xpathNamespaces", namespaceMap);
        }
    }

    

    protected void prepareReloadMechanism(Element element, ParserContext parserContext) {
        String id = element.getAttribute("id");
        String attribute = element.getAttribute("reload-interval");
        if (attribute != null && !attribute.isEmpty()) {
            int reloadInterval = 0;
            try {
                reloadInterval = Integer.valueOf(attribute);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("The attribute reload-interval is invalid", e);
            }
            if (reloadInterval >= MINIMUM_RELOAD_INTERVAL) {
                // Update task
                BeanDefinitionBuilder updateTask = BeanDefinitionBuilder
                        .genericBeanDefinition(ConfigurationSnapshotRefresher.class);
                updateTask.addConstructorArgReference(id);

                // Scheduled executor
                BeanDefinitionBuilder scheduledExecutorTask = BeanDefinitionBuilder
                        .genericBeanDefinition(ScheduledExecutorTask.class);
                scheduledExecutorTask.addConstructorArgValue(updateTask.getBeanDefinition());
                scheduledExecutorTask.addPropertyValue("period", reloadInterval);
                scheduledExecutorTask.addPropertyValue("delay", reloadInterval);

                ManagedList<Object> taskList = new ManagedList<Object>();
                taskList.add(scheduledExecutorTask.getBeanDefinition());

                // Scheduler factory bean
                BeanDefinitionBuilder scheduledExecutorFactoryBean = BeanDefinitionBuilder
                        .genericBeanDefinition(ScheduledExecutorFactoryBean.class);
                scheduledExecutorFactoryBean.addPropertyValue("scheduledExecutorTasks", taskList);
                scheduledExecutorFactoryBean.addPropertyValue("threadNamePrefix", id + "-reloader");
                parserContext.registerBeanComponent(new BeanComponentDefinition(scheduledExecutorFactoryBean
                        .getBeanDefinition(), id + "-Scheduler"));
            }
        }
    }

    /**
     * @param element
     * @return
     */
    protected AbstractBeanDefinition prepareResourceManager(Element element, Engine engine) {
        BeanDefinitionBuilder builder = BeanDefinitionBuilder.genericBeanDefinition(ResourceSnapshotManager.class);
        builder.addConstructorArgValue(prepareResourceSelector(element, engine));
        builder.addConstructorArgReference(getLoaderReference(element));
        return builder.getBeanDefinition();
    }

    /**
     * @param element
     * @return
     */
    protected AbstractBeanDefinition prepareResourceSelector(Element element, Engine engine) {
        String path = element.getAttribute("path");
        BeanDefinitionBuilder builder;
        if (path != null && !path.isEmpty()) {
            builder = BeanDefinitionBuilder.genericBeanDefinition(FixedResourceSelector.class);
            builder.addConstructorArgValue(prepareFileSystemResource(path));
        } else {
            builder = BeanDefinitionBuilder.genericBeanDefinition(ScanningResourceSelector.class);
            builder.addConstructorArgValue(prepareBaseDirectoryList(element));
            builder.addConstructorArgValue(prepareResourceNameResolver(element, engine));
        }
        return builder.getBeanDefinition();
    }

    /**
     * @param element
     * @return
     */
    protected AbstractBeanDefinition prepareResourceNameResolver(Element element, Engine engine) {
        Element selectorElement = selectSingleChildElement(element, "selector", true);
        BeanDefinitionBuilder builder = null;
        String prefix = getName(element);
        String extension = engine.getDefaultExtension();
        if (selectorElement != null) {
            Element naming = selectSingleChildElement(selectorElement, "naming", true);
            if (naming != null) {
                prefix = attribute(naming, "prefix", prefix);
                extension = attribute(naming, "extension", extension);
            }

            Element versionMavenElement = selectSingleChildElement(selectorElement, "version-maven", true);
            if (versionMavenElement != null) {
                builder = buildMavenVersionedResourceNameResolver(versionMavenElement, prefix, extension);
            }
        }

        // Failsafe
        if (builder == null) {
            builder = buildBasicResourceNameResolver(prefix, extension);
        }
        return builder.getBeanDefinition();
    }

    /**
     * @param versionMavenElement
     * @param prefix
     * @param extension
     * @return
     */
    protected BeanDefinitionBuilder buildMavenVersionedResourceNameResolver(Element versionMavenElement, String prefix,
            String extension) {
        BeanDefinitionBuilder builder = BeanDefinitionBuilder
                .genericBeanDefinition(VersionedResourceNameResolver.class);
        builder.addConstructorArgValue(prefix);
        builder.addConstructorArgValue(prepareApplicationVersionFromMaven(versionMavenElement));
        builder.addPropertyValue("extension", extension);
        return builder;
    }

    /**
     * @param versionMavenElement
     * @return
     */
    protected AbstractBeanDefinition prepareApplicationVersionFromMaven(Element versionMavenElement) {
        BeanDefinitionBuilder builder = BeanDefinitionBuilder.genericBeanDefinition(ApplicationVersionFromMaven.class);
        builder.addConstructorArgValue(versionMavenElement.getAttribute("groupId"));
        builder.addConstructorArgValue(versionMavenElement.getAttribute("artifactId"));
        builder.addConstructorArgValue(getClass().getClassLoader());
        return builder.getBeanDefinition();
    }



    /**
     * @param prefix
     * @param extension
     * @return
     */
    protected BeanDefinitionBuilder buildBasicResourceNameResolver(String prefix, Object extension) {
        BeanDefinitionBuilder builder = BeanDefinitionBuilder.genericBeanDefinition(BasicResourceNameResolver.class);
        builder.addConstructorArgValue(prefix);
        builder.addPropertyValue("extension", extension);
        return builder;
    }


    /**
     * @param element
     * @return
     */
    protected ManagedList<Object> prepareBaseDirectoryList(Element element) {
        ManagedList<Object> list = new ManagedList<Object>();
        Element locationElement = selectSingleChildElement(element, "location", true);
        if (locationElement != null) {
            List<Element> selectChildElements = selectChildElements(locationElement, "*");
            for (Element location : selectChildElements) {
                String tag = location.getLocalName();
                if ("environment-variable".equals(tag)) {
                    list.add(prepareLocation(location.getTextContent(), EnvironmentVariableDirectory.class));
                } else if ("system-property".equals(tag)) {
                    list.add(prepareLocation(location.getTextContent(), SystemPropertyDirectory.class));
                } else if ("home".equals(tag)) {
                    list.add(prepareHomeLocation(element, location.getAttribute("path")));
                } else if ("platform".equals(tag)) {
                    list.add(preparePlatformLocation(location.getTextContent()));
                } else {
                    throw new IllegalArgumentException(String.format("Unknown location type '%s'", tag));
                }
            }
        } else {
            // home and platforms
            list.add(prepareHomeLocation(element, null));
            PlatformDirectory[] values = PlatformDirectory.values();
            for (PlatformDirectory platformDirectory : values) {
                list.add(platformDirectory);
            }
        }
        return list;
    }

    /**
     * @param textContent
     * @return
     */
    protected PlatformDirectory preparePlatformLocation(String type) {
        return PlatformDirectory.valueOf(type);
    }

    /**
     * @param attribute
     * @param class1
     * @return
     */
    protected AbstractBeanDefinition prepareHomeLocation(Element element, String path) {
        BeanDefinitionBuilder builder = BeanDefinitionBuilder.genericBeanDefinition(HomeDirectory.class);
        if (path == null) {
            path = ".config/" + getName(element);
        }
        builder.addPropertyValue("path", path);
        return builder.getBeanDefinition();
    }

    /**
     * @param textContent
     * @param class1
     * @return
     */
    protected AbstractBeanDefinition prepareLocation(String value, Class<?> baseDirectoryClass) {
        BeanDefinitionBuilder builder = BeanDefinitionBuilder.genericBeanDefinition(baseDirectoryClass);
        builder.addConstructorArgValue(value);
        return builder.getBeanDefinition();
    }

    /**
     * @param element
     * @return
     */
    protected AbstractBeanDefinition prepareSnapshotEventHandler(Element element) {
        BeanDefinitionBuilder builder = BeanDefinitionBuilder.genericBeanDefinition(LoggingSnapshotEventHandler.class);
        builder.addConstructorArgValue(getName(element));
        return builder.getBeanDefinition();
    }

    /**
     * @param element
     * @return
     */
    protected AbstractBeanDefinition prepareDefaultConfigurationSource(Element element, Engine engine) {
        Element defaultsElement = selectSingleChildElement(element, "defaults", true);
        String defaultsPath = null;
        String encoding = null;
        if (defaultsElement != null) {
            defaultsPath = defaultsElement.getAttribute("path");
            encoding = defaultsElement.getAttribute("encoding");
        }
        if (defaultsPath == null) {
            String guessPath = String.format("stillingar/%s.%s", getName(element), engine.getDefaultExtension());
            URL resource = getClass().getClassLoader().getResource(guessPath);
            if (resource != null) {
                defaultsPath = guessPath;
            }
        }
        if (defaultsPath != null) {
            BeanDefinitionBuilder builder = BeanDefinitionBuilder
                    .genericBeanDefinition(DefaultConfigurationSourceFactoryBean.class);
            
            builder.addConstructorArgValue(prepareClassPathResource(defaultsPath));
            builder.addConstructorArgReference(getLoaderReference(element));
            builder.addConstructorArgValue(encoding == null ? null : Charset.forName(encoding));
            return builder.getBeanDefinition();
        }
        return null;
    }

    /**
     * @param defaultsPath
     * @return
     */
    protected AbstractBeanDefinition prepareClassPathResource(String path) {
        BeanDefinitionBuilder builder = BeanDefinitionBuilder.genericBeanDefinition(ClassPathResource.class);
        builder.addConstructorArgValue(path);
        return builder.getBeanDefinition();
    }

    protected AbstractBeanDefinition prepareFileSystemResource(String path) {
        BeanDefinitionBuilder builder = BeanDefinitionBuilder.genericBeanDefinition(FileSystemResource.class);
        builder.addConstructorArgValue(path);
        return builder.getBeanDefinition();
    }


    
    protected AbstractBeanDefinition prepareConversionManager() {
        BeanDefinitionBuilder builder = BeanDefinitionBuilder.genericBeanDefinition("org.brekka.stillingar.xmlbeans.conversion.ConversionManager");
        ManagedList<Object> converters = new ManagedList<Object>();
        List<String> converterShortNames = Arrays.asList("BigDecimalConverter", "BigIntegerConverter",
                "BooleanConverter", "ByteConverter", "ByteArrayConverter", "CalendarConverter", "DateConverter",
                "DoubleConverter", "ElementConverter", "FloatConverter", "IntegerConverter", "LongConverter",
                "ShortConverter", "StringConverter", "URIConverter", "DocumentConverter");
        for (String shortName : converterShortNames) {
            BeanDefinitionBuilder converterBldr = BeanDefinitionBuilder.genericBeanDefinition(
                    "org.brekka.stillingar.xmlbeans.conversion." + shortName);
            converters.add(converterBldr.getBeanDefinition());
        }
        
        converters.add(BeanDefinitionBuilder.genericBeanDefinition(ApplicationContextConverter.class).getBeanDefinition());
        builder.addConstructorArgValue(converters);
        return builder.getBeanDefinition();
    }
    
    /**
     * @param element
     * @return
     */
    private static ManagedMap<String, String> toNamespaceMap(Element element) {
        ManagedMap<String, String> namespaceMap = new ManagedMap<String, String>();
        List<Element> namespaceElements = selectChildElements(element, "namespace");
        for (Element namespaceElement : namespaceElements) {
            String prefix = namespaceElement.getAttribute("prefix");
            String url = namespaceElement.getAttribute("url");
            namespaceMap.put(prefix, url);
        }
        return namespaceMap;
    }
    
    /**
     * @param element
     * @return
     */
    private static Engine determineEngine(Element element) {
        String engine = element.getAttribute("engine");
        engine = engine.toUpperCase();
        return Engine.valueOf(engine);
    }
    
    /**
     * @param naming
     * @param string
     * @param prefix
     * @return
     */
    protected static String attribute(Element elem, String attributeName, String defaultValue) {
        String value = elem.getAttribute(attributeName);
        if (value == null) {
            value = defaultValue;
        }
        return value;
    }
    
    /**
     * @param element
     * @return
     */
    protected static String getLoaderReference(Element element) {
        String id = element.getAttribute("id");
        return id + "-loader";
    }

    protected static String getName(Element element) {
        // Optional application name, will use the id if not specified.
        String name = element.getAttribute("name");
        if (name == null) {
            name = element.getAttribute("id");
        }
        return name;
    }

    /**
     * @param element
     * @param string
     * @return
     */
    protected static Element selectSingleChildElement(Element element, String tagName, boolean optional) {
        Element singleChild = null;
        NodeList children = element.getElementsByTagNameNS("*", tagName);
        if (children.getLength() == 1) {
            Node node = children.item(0);
            if (node instanceof Element) {
                singleChild = (Element) node;
            } else {
                throw new IllegalArgumentException(String.format(
                        "Expected child node '%s' of element '%s' to be itself an instance of Element, "
                                + "it is instead '%s'", tagName, element.getTagName(), node.getClass().getName()));
            }
        } else if (children.getLength() == 0) {
            if (!optional) {
                throw new IllegalArgumentException(String.format(
                        "Failed to find a single child element named '%s' for parent element '%s'", tagName,
                        element.getTagName()));
            }
        } else {
            throw new IllegalArgumentException(String.format(
                    "Expected element '%s' to have a single child element named '%s', found however %d elements",
                    element.getTagName(), tagName, children.getLength()));
        }
        return singleChild;
    }
    
    protected static List<Element> selectChildElements(Element element, String tagName) {
        NodeList children = element.getElementsByTagNameNS("*", tagName);
        List<Element> elementList = new ArrayList<Element>(children.getLength());
        for (int i = 0; i < children.getLength(); i++) {
            Node item = children.item(i);
            if (item instanceof Element) {
                elementList.add((Element) item);
            } else {
                throw new IllegalArgumentException(String.format(
                        "The child node '%s' of element '%s' at index %d is not an instance of Element, "
                                + "it is instead '%s'", tagName, element.getTagName(), 
                                i, item.getClass().getName()));
            }
            
        }
        return elementList;
    }

    enum Engine {
        PROPS(PropertiesConfigurationSourceLoader.class.getName(), "properties"), 
        
        XMLBEANS("org.brekka.stillingar.xmlbeans.XmlBeansSnapshotLoader", "xml"), 
        
        JAXB("org.brekka.stillingar.jaxb.JAXBSnapshotLoader", "xml")
        
        ;

        private final String loaderClassName;
        private final String defaultExtension;

        private Engine(String loaderClassName, String defaultExtension) {
            this.loaderClassName = loaderClassName;
            this.defaultExtension = defaultExtension;
        }

        /**
         * @return the defaultExtension
         */
        public String getDefaultExtension() {
            return defaultExtension;
        }

        /**
         * @return the loaderClassName
         */
        public String getLoaderClassName() {
            return loaderClassName;
        }
    }
}
