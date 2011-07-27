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

import org.apache.xmlbeans.XmlBoolean;
import org.apache.xmlbeans.XmlObject;

/**
 * @author Andrew Taylor
 */
public class BooleanConverter extends AbstractTypeConverter<Boolean> {

    
    public Class<Boolean> targetType() {
        return Boolean.class;
    }
    
    @Override
    public Class<?> primitiveType() {
        return Boolean.TYPE;
    }
    
    public Boolean convert(XmlObject xmlValue) {
        Boolean value;
        if (xmlValue instanceof XmlBoolean) {
            value = Boolean.valueOf(((XmlBoolean) xmlValue).getBooleanValue());
        } else {
            throw noConversionAvailable(xmlValue);
        }
        return value;
    }
}