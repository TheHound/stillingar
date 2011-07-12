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

package org.brekka.stillingar.xmlbeans.conversion;

import org.apache.xmlbeans.XmlAnySimpleType;
import org.apache.xmlbeans.XmlObject;

/**
 * @author Andrew Taylor
 */
public class StringConverter extends AbstractTypeConverter<String> {

    public Class<String> targetType() {
        return String.class;
    }    
    
    public String convert(XmlObject xmlValue) {
        String value;
        if (xmlValue instanceof XmlAnySimpleType) {
            value = ((XmlAnySimpleType) xmlValue).getStringValue();
        } else {
            value = xmlValue.xmlText();
        }
        return value;
    }
    
}
