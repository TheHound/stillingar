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

package org.brekka.stillingar.xmlbeans;

import static java.lang.String.format;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.xmlbeans.XmlError;
import org.apache.xmlbeans.XmlException;
import org.apache.xmlbeans.XmlObject;
import org.apache.xmlbeans.XmlOptions;
import org.brekka.stillingar.core.ConfigurationException;
import org.brekka.stillingar.core.ConfigurationSource;
import org.brekka.stillingar.core.ConfigurationSourceLoader;
import org.brekka.stillingar.xmlbeans.conversion.ConversionManager;

/**
 * Loader of Apache XmlBean based snapshots.
 * 
 * @author Andrew Taylor
 */
public class XmlBeansSnapshotLoader implements ConfigurationSourceLoader {

    private final ConversionManager conversionManager;
	
	private Map<String, String> xpathNamespaces;
	
	private boolean validate = true;
	
	
	/**
     * 
     */
    public XmlBeansSnapshotLoader() {
        this(new ConversionManager());
    }
    
    public XmlBeansSnapshotLoader(ConversionManager conversionManager) {
        this.conversionManager = conversionManager;
    }
    
    /* (non-Javadoc)
     * @see org.brekka.stillingar.core.ConfigurationSourceLoader#parse(java.io.InputStream, java.nio.charset.Charset)
     */
    public ConfigurationSource parse(InputStream sourceStream, Charset encoding) throws ConfigurationException,
            IOException {
        ConfigurationSource configurationSource;
        try {
            XmlObject xmlBean = XmlObject.Factory.parse(sourceStream);
            if (this.validate) {
                validate(xmlBean);
            }
            configurationSource = new XmlBeansConfigurationSource(xmlBean, this.xpathNamespaces, conversionManager);
        } catch (XmlException e) {
            throw new ConfigurationException(format(
                    "Illegal XML"), e);
        }
        return configurationSource;
    }

	protected void validate(XmlObject bean) {
		List<XmlError> errors = new ArrayList<XmlError>();
		XmlOptions validateOptions = new XmlOptions();
		validateOptions.setErrorListener(errors);
		
		if (!bean.validate(validateOptions)) {
			throw new ConfigurationException(format(
					"Configuration XML does not validate. " +
					"Errors: %s", errors));
		}
	}

	
	public void setXpathNamespaces(Map<String, String> xpathNamespaces) {
		this.xpathNamespaces = xpathNamespaces;
	}
	
	public void setValidate(boolean validate) {
		this.validate = validate;
	}
}
