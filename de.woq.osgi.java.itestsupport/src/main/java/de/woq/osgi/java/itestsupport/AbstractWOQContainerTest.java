/*
 * Copyright 2013, WoQ - Way of Quality UG(mbH)
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package de.woq.osgi.java.itestsupport;

import javax.inject.Inject;
import org.ops4j.pax.exam.Option;
import org.osgi.framework.BundleContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static org.ops4j.pax.exam.CoreOptions.frameworkStartLevel;
import static org.ops4j.pax.exam.CoreOptions.options;
import static org.ops4j.pax.exam.CoreOptions.systemProperty;

public class AbstractWOQContainerTest {

  private final static Logger LOGGER = LoggerFactory.getLogger(AbstractWOQContainerTest.class);

  @Inject
  protected BundleContext bundleContext;

  protected Option[] containerConfiguration() throws Exception {
    return options(
      new CompositeBundleListProvider(
        "classpath:junit.composite",
        "classpath:woq-common.composite"
      ).getBundles(),
      systemProperty("config.updateInterval").value("1000"),
      systemProperty("woq.home").value("target/test-classes"),
      frameworkStartLevel(100)
    );
  }

  public BundleContext getBundleContext() {
    return bundleContext;
  }
}
