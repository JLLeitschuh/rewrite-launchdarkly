/*
 * Copyright 2023 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite.launchdarkly;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class RemoveBoolVariationTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new RemoveBoolVariation("flag-key-123abc", true, null))
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "launchdarkly-java-server-sdk-6"));
    }

    @Test
    @DocumentExample
    void enablePermanently() {
        rewriteRun(
          // language=java
          java(
            """
              import com.launchdarkly.sdk.LDContext;
              import com.launchdarkly.sdk.server.LDClient;
              class Foo {
                  private LDClient client = new LDClient("sdk-key-123abc");
                  void bar() {
                      LDContext context = null;
                      if (client.boolVariation("flag-key-123abc", context, false)) {
                          // Application code to show the feature
                          System.out.println("Feature is on");
                      }
                      else {
                        // The code to run if the feature is off
                          System.out.println("Feature is off");
                      }
                  }
              }
              """,
            """
              class Foo {
                  void bar() {
                      // Application code to show the feature
                      System.out.println("Feature is on");
                  }
              }
              """
          )
        );
    }

    @Test
    void enablePermanentlyNegated() {
        rewriteRun(
          // language=java
          java(
            """
              import com.launchdarkly.sdk.LDContext;
              import com.launchdarkly.sdk.server.LDClient;
              class Foo {
                  private LDClient client = new LDClient("sdk-key-123abc");
                  void bar() {
                      LDContext context = null;
                      if (!client.boolVariation("flag-key-123abc", context, false)) {
                        // The code to run if the feature is off
                          System.out.println("Feature is off");
                      }
                      else {
                          // Application code to show the feature
                          System.out.println("Feature is on");
                      }
                  }
              }
              """,
            """
              class Foo {
                  void bar() {
                      // Application code to show the feature
                      System.out.println("Feature is on");
                  }
              }
              """
          )
        );
    }

    @Test
    void disablePermanently() {
        rewriteRun(
          spec -> spec.recipe(new RemoveBoolVariation("flag-key-123abc", false, null)),
          // language=java
          java(
            """
              import com.launchdarkly.sdk.LDContext;
              import com.launchdarkly.sdk.server.LDClient;
              class Foo {
                  private LDClient client = new LDClient("sdk-key-123abc");
                  void bar() {
                      LDContext context = null;
                      if (client.boolVariation("flag-key-123abc", context, false)) {
                          // Application code to show the feature
                          System.out.println("Feature is on");
                      }
                      else {
                        // The code to run if the feature is off
                          System.out.println("Feature is off");
                      }
                  }
              }
              """,
            """
              class Foo {
                  void bar() {
                      // The code to run if the feature is off
                      System.out.println("Feature is off");
                  }
              }
              """
          )
        );
    }

    @Test
    void enablePermanentlyWithParameters() {
        rewriteRun(
          // language=java
          java(
            """
              import com.launchdarkly.sdk.LDContext;
              import com.launchdarkly.sdk.server.LDClient;
              class Foo {
                  // Unused parameters are not yet cleaned up
                  void bar(LDClient client, LDContext context) {
                      if (client.boolVariation("flag-key-123abc", context, false)) {
                          // Application code to show the feature
                          System.out.println("Feature is on");
                      }
                  }
              }
              """,
            """
              import com.launchdarkly.sdk.LDContext;
              import com.launchdarkly.sdk.server.LDClient;
              class Foo {
                  // Unused parameters are not yet cleaned up
                  void bar(LDClient client, LDContext context) {
                      // Application code to show the feature
                      System.out.println("Feature is on");
                  }
              }
              """
          )
        );
    }

    @Test
    void simplifyOnlyAffectsSourceFileWithFeatureFlag() {
        // language=java
        rewriteRun(
          java(
            """
              import com.launchdarkly.sdk.LDContext;
              import com.launchdarkly.sdk.server.LDClient;
              class Foo {
                  private LDClient client = new LDClient("sdk-key-123abc");
                  void foo() {
                      LDContext context = null;
                      if (client.boolVariation("flag-key-123abc", context, false)) {
                          // Application code to show the feature
                          System.out.println("Feature is on");
                      }
                  }
              }
              """,
            """
              class Foo {
                  void foo() {
                      // Application code to show the feature
                      System.out.println("Feature is on");
                  }
              }
              """
          ),
          java(
          """
            class Bar {
                void bar() {
                    if (true) {
                        // conditional retained; simplify only applies to the file with the feature flag check
                    }
                }
            }
            """)
        );
    }

    @Test
    void localVariablesNotInlined() {
        // language=java
        rewriteRun(
          java(
            """
              import com.launchdarkly.sdk.LDContext;
              import com.launchdarkly.sdk.server.LDClient;
              class Foo {
                  private LDClient client = new LDClient("sdk-key-123abc");
                  void bar() {
                      LDContext context = null;
                      // Local variables not yet inlined
                      boolean flagEnabled = client.boolVariation("flag-key-123abc", context, false);
                      if (flagEnabled) {
                          // Application code to show the feature
                          System.out.println("Feature is on");
                      }
                  }
              }
              """,
            """
              class Foo {
                  void bar() {
                      // Local variables not yet inlined
                      boolean flagEnabled = true;
                      if (flagEnabled) {
                          // Application code to show the feature
                          System.out.println("Feature is on");
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void customMethodPatternForWrapper() {
        rewriteRun(
          spec -> spec.recipe(new RemoveBoolVariation("flag-key-123abc", true, "com.acme.bank.CustomLaunchDarklyWrapper featureFlagEnabled(String, boolean)")),
          // language=java
          java(
            """
              package com.acme.bank;
              
              import com.launchdarkly.sdk.LDContext;
              import com.launchdarkly.sdk.server.LDClient;
              
              public class CustomLaunchDarklyWrapper {
                  private LDClient client = new LDClient("sdk-key-123abc");
                  public boolean featureFlagEnabled(String key, boolean fallback) {
                      LDContext context = null;
                      return client.boolVariation(key, context, false);
                  }
              }
              """
          ),
          // language=java
          java(
            """
              import com.acme.bank.CustomLaunchDarklyWrapper;
              class Foo {
                  private CustomLaunchDarklyWrapper wrapper = new CustomLaunchDarklyWrapper();
                  void bar() {
                      if (wrapper.featureFlagEnabled("flag-key-123abc", false)) {
                          // Application code to show the feature
                          System.out.println("Feature is on");
                      }
                      else {
                        // The code to run if the feature is off
                          System.out.println("Feature is off");
                      }
                  }
              }
              """,
            """
              class Foo {
                  void bar() {
                      // Application code to show the feature
                      System.out.println("Feature is on");
                  }
              }
              """
          )
        );
    }
}
