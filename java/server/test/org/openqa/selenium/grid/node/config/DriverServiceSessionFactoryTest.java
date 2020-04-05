// Licensed to the Software Freedom Conservancy (SFC) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The SFC licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.

package org.openqa.selenium.grid.node.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import org.junit.Before;
import org.junit.Test;
import org.openqa.selenium.Capabilities;
import org.openqa.selenium.ImmutableCapabilities;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.grid.data.CreateSessionRequest;
import org.openqa.selenium.grid.node.ActiveSession;
import org.openqa.selenium.remote.Dialect;
import org.openqa.selenium.remote.http.HttpClient;
import org.openqa.selenium.remote.service.DriverService;

import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.trace.Tracer;

import java.util.Optional;
import java.util.function.Predicate;

public class DriverServiceSessionFactoryTest {

  private Tracer tracer;
  private HttpClient.Factory clientFactory;
  private DriverService.Builder builder;
  private DriverService driverService;

  @Before
  public void setUp() {
    tracer = OpenTelemetry.getTracerProvider().get("default");
    clientFactory = HttpClient.Factory.createDefault();
    builder = mock(DriverService.Builder.class);
    driverService = mock(DriverService.class);
    when(builder.build()).thenReturn(driverService);
  }

  @Test
  public void itDelegatesCapabilitiesTestingToPredicate() {
    DriverServiceSessionFactory factory = factoryFor("chrome", builder);

    assertThat(factory.test(toPayload("chrome"))).isTrue();
    assertThat(factory.test(toPayload("firefox"))).isFalse();

    verifyNoInteractions(builder);
  }

  @Test
  public void shouldNotInstantiateSessionIfNoDialectSpecifiedInARequest() {
    DriverServiceSessionFactory factory = factoryFor("chrome", builder);

    Optional<ActiveSession> session = factory.apply(new CreateSessionRequest(
        ImmutableSet.of(), toPayload("chrome"), ImmutableMap.of()));

    assertThat(session).isEmpty();
    verifyNoInteractions(builder);
  }

  @Test
  public void shouldNotInstantiateSessionIfCapabilitiesDoNotMatch() {
    DriverServiceSessionFactory factory = factoryFor("chrome", builder);

    Optional<ActiveSession> session = factory.apply(new CreateSessionRequest(
        ImmutableSet.of(Dialect.W3C), toPayload("firefox"), ImmutableMap.of()));

    assertThat(session).isEmpty();
    verifyNoInteractions(builder);
  }

  @Test
  public void shouldNotInstantiateSessionIfBuilderCanNotBuildService() {
    when(builder.build()).thenThrow(new WebDriverException());

    DriverServiceSessionFactory factory = factoryFor("chrome", builder);

    Optional<ActiveSession> session = factory.apply(new CreateSessionRequest(
        ImmutableSet.of(Dialect.W3C), toPayload("chrome"), ImmutableMap.of()));

    assertThat(session).isEmpty();
    verify(builder, times(1)).build();
    verifyNoMoreInteractions(builder);
  }

  private DriverServiceSessionFactory factoryFor(String browser, DriverService.Builder builder) {
    Predicate<Capabilities> predicate = c -> c.getBrowserName().equals(browser);
    return new DriverServiceSessionFactory(tracer, clientFactory, predicate, builder);
  }

  private Capabilities toPayload(String browserName) {
    return new ImmutableCapabilities("browserName", browserName);
  }
}
