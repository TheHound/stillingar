/*
 * Copyright 2012 the original author or authors.
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

package org.brekka.stillingar.example;

import static org.junit.Assert.assertEquals;

import org.brekka.stillingar.example.support.MessageOfTheDay;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.AbstractJUnit4SpringContextTests;

@ContextConfiguration
@DirtiesContext
public class SimpleExampleTest extends AbstractJUnit4SpringContextTests {

    @Autowired
    private MessageOfTheDay messageOfTheDay;
    
	@Test
	public void test() throws Exception {
	    assertEquals("There is currently no message of the day", messageOfTheDay.getMessage());
	}
	
}
