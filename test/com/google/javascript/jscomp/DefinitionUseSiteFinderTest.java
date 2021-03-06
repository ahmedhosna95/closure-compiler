/*
 * Copyright 2009 The Closure Compiler Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.javascript.jscomp;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multiset;
import com.google.common.collect.TreeMultiset;
import com.google.javascript.jscomp.DefinitionsRemover.Definition;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import java.util.Collection;
import java.util.Set;
import java.util.TreeSet;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for {@link DefinitionUseSiteFinder}
 *
 */
@RunWith(JUnit4.class)
public final class DefinitionUseSiteFinderTest extends CompilerTestCase {
  Set<String> found = new TreeSet<>();

  @Override
  protected int getNumRepetitions() {
    // run pass once.
    return 1;
  }

  @Override
  protected void tearDown() throws Exception {
    super.tearDown();
    found.clear();
  }

  @Test
  public void testDefineNumber() {
    checkDefinitionsInJs(
        "var a = 1",
        ImmutableSet.of("DEF NAME a -> NUMBER"));

    checkDefinitionsInJs(
        "a = 1",
        ImmutableSet.of("DEF NAME a -> NUMBER"));

    checkDefinitionsInJs(
        "a.b = 1",
        ImmutableSet.of("DEF GETPROP a.b -> NUMBER"));

    // getelem expressions are invisible to the definition gatherer.
    checkDefinitionsInJs(
        "a[\"b\"] = 1",
        ImmutableSet.<String>of());

    checkDefinitionsInJs(
        "f().b = 1",
        ImmutableSet.of("DEF GETPROP null -> NUMBER"));

    checkDefinitionsInJs(
        "({a : 1}); o.a",
        ImmutableSet.of("DEF STRING_KEY null -> NUMBER",
                        "USE GETPROP o.a -> [NUMBER]"));

    // TODO(johnlenz): Fix this.
    checkDefinitionsInJs(
      "({'a' : 1}); o['a']",
      ImmutableSet.of("DEF STRING_KEY null -> NUMBER"));

    checkDefinitionsInJs(
      "({1 : 1}); o[1]",
      ImmutableSet.of("DEF STRING_KEY null -> NUMBER"));

    checkDefinitionsInJs(
        "var a = {b : 1}; a.b",
        ImmutableSet.of("DEF NAME a -> <null>",
                        "DEF STRING_KEY null -> NUMBER",
                        "USE NAME a -> [<null>]",
                        "USE GETPROP a.b -> [NUMBER]"));
  }

  @Test
  public void testDefineGet() {
    // TODO(johnlenz): Add support for quoted properties
    checkDefinitionsInJs(
      "({get a() {}}); o.a",
      ImmutableSet.of("DEF GETTER_DEF null -> FUNCTION",
                      "USE GETPROP o.a -> [FUNCTION]"));
  }

  @Test
  public void testDefineSet() {
    // TODO(johnlenz): Add support for quoted properties
    checkDefinitionsInJs(
      "({set a(b) {}}); o.a",
      ImmutableSet.of("DEF NAME b -> <null>",
                      "DEF SETTER_DEF null -> FUNCTION",
                      "USE GETPROP o.a -> [FUNCTION]"));
  }

  @Test
  public void testDefineFunction() {
    checkDefinitionsInJs(
        "var a = function(){}",
        ImmutableSet.of("DEF NAME a -> FUNCTION"));

    checkDefinitionsInJs(
        "var a = function f(){}",
        ImmutableSet.of("DEF NAME f -> FUNCTION", "DEF NAME a -> FUNCTION"));

    checkDefinitionsInJs(
        "function a(){}",
        ImmutableSet.of("DEF NAME a -> FUNCTION"));

    checkDefinitionsInJs(
        "a = function(){}",
        ImmutableSet.of("DEF NAME a -> FUNCTION"));

    checkDefinitionsInJs(
        "a.b = function(){}",
        ImmutableSet.of("DEF GETPROP a.b -> FUNCTION"));

    // getelem expressions are invisible to the definition gatherer.
    checkDefinitionsInJs(
        "a[\"b\"] = function(){}",
        ImmutableSet.<String>of());

    checkDefinitionsInJs(
        "f().b = function(){}",
        ImmutableSet.of("DEF GETPROP null -> FUNCTION"));
  }

  @Test
  public void testFunctionArgumentsBasic() {
    checkDefinitionsInJs(
        "function f(a){return a}",
        ImmutableSet.of("DEF NAME a -> <null>",
                        "USE NAME a -> [<null>]",
                        "DEF NAME f -> FUNCTION"));

    checkDefinitionsInJs(
        "var a = 1; function f(a){return a}",
        ImmutableSet.of("DEF NAME a -> NUMBER",
                        "DEF NAME a -> <null>",
                        "USE NAME a -> [<null>, NUMBER]",
                        "DEF NAME f -> FUNCTION"));
  }

  private static final String DEF = "var f = function(arg1, arg2){}";
  private static final String USE = "f(1, 2)";

  @Test
  public void testFunctionArgumentsInExterns() {

    // function arguments are definitions when they appear in source.
    checkDefinitionsInJs(
        DEF + ";" + USE,
        ImmutableSet.of("DEF NAME f -> FUNCTION",
                        "DEF NAME arg1 -> <null>",
                        "DEF NAME arg2 -> <null>",
                        "USE NAME f -> [FUNCTION]"));

    // function arguments are NOT definitions when they appear in externs.
    checkDefinitions(
        DEF, USE,
        ImmutableSet.of("DEF NAME f -> EXTERN FUNCTION",
                        "USE NAME f -> [EXTERN FUNCTION]"));
  }

  @Test
  public void testMultipleDefinition() {
    checkDefinitionsInJs(
        "a = 1; a = 2; a",
        ImmutableSet.of("DEF NAME a -> NUMBER",
                        "USE NAME a -> [NUMBER x 2]"));

    checkDefinitionsInJs(
        "a = 1; a = 'a'; a",
        ImmutableSet.of("DEF NAME a -> NUMBER",
                        "DEF NAME a -> STRING",
                        "USE NAME a -> [NUMBER, STRING]"));

    checkDefinitionsInJs(
        "a = 1; b = 2; a = b; a",
        ImmutableSet.of("DEF NAME a -> <null>",
                        "DEF NAME a -> NUMBER",
                        "DEF NAME b -> NUMBER",
                        "USE NAME a -> [<null>, NUMBER]",
                        "USE NAME b -> [NUMBER]"));

    checkDefinitionsInJs(
        "a = 1; b = 2; c = b; c = a; c",
        ImmutableSet.of("DEF NAME a -> NUMBER",
                        "DEF NAME b -> NUMBER",
                        "DEF NAME c -> <null>",
                        "USE NAME a -> [NUMBER]",
                        "USE NAME b -> [NUMBER]",
                        "USE NAME c -> [<null> x 2]"));

    checkDefinitionsInJs(
        "function f(){} f()",
        ImmutableSet.of("DEF NAME f -> FUNCTION",
                        "USE NAME f -> [FUNCTION]"));

    checkDefinitionsInJs(
        "function f(){} f.call(null)",
        ImmutableSet.of("DEF NAME f -> FUNCTION",
                        "USE NAME f -> [FUNCTION]",
                        "USE GETPROP f.call -> [FUNCTION]"));

    checkDefinitionsInJs(
        "function f(){} f.apply(null, [])",
        ImmutableSet.of("DEF NAME f -> FUNCTION",
                        "USE NAME f -> [FUNCTION]",
                        "USE GETPROP f.apply -> [FUNCTION]"));

    checkDefinitionsInJs(
        "function f(){} f.foobar()",
        ImmutableSet.of("DEF NAME f -> FUNCTION",
                        "USE NAME f -> [FUNCTION]"));

    checkDefinitionsInJs(
        "function f(){} f(); f.call(null)",
        ImmutableSet.of("DEF NAME f -> FUNCTION",
                        "USE NAME f -> [FUNCTION]",
                        "USE GETPROP f.call -> [FUNCTION]"));

  }

  @Test
  public void testDropStubDefinitions() {
    String externs =
        lines(
            "obj.prototype.stub;",
            "/**",
            " * @param {string} s id.",
            " * @return {string}",
            " * @nosideeffects",
            " */",
            "obj.prototype.stub = function(s) {};");

    checkDefinitionsInExterns(
        externs, ImmutableSet.of("DEF GETPROP obj.prototype.stub -> EXTERN FUNCTION"));
  }

  @Test
  public void testNoDropStub1() {
    String externs =
        lines(
            "var name;",
            "/**",
            " * @param {string} s id.",
            " * @return {string}",
            " * @nosideeffects",
            " */",
            "var name = function(s) {};");

    checkDefinitionsInExterns(
        externs, ImmutableSet.of("DEF NAME name -> EXTERN <null>",
                                 "DEF NAME name -> EXTERN FUNCTION"));
  }

  @Test
  public void testNoDropStub2() {
    String externs =
        lines(
            "f().name;", // These are not recongnized as stub definitions
            "/**",
            " * @param {string} s id.",
            " * @return {string}",
            " * @nosideeffects",
            " */",
            "f().name;");

    checkDefinitionsInExterns(externs, ImmutableSet.<String>of());
  }

  @Test
  public void testDefinitionInExterns() {
    String externs = "var a = 1";

    checkDefinitionsInExterns(
        externs,
        ImmutableSet.of("DEF NAME a -> EXTERN NUMBER"));

    checkDefinitions(
        externs,
        "var b = 1",
        ImmutableSet.of("DEF NAME a -> EXTERN NUMBER", "DEF NAME b -> NUMBER"));

    checkDefinitions(
        externs,
        "a = \"foo\"; a",
        ImmutableSet.of("DEF NAME a -> EXTERN NUMBER",
                        "DEF NAME a -> STRING",
                        "USE NAME a -> [EXTERN NUMBER, STRING]"));

    checkDefinitionsInExterns(
        "var a = {}; a.b = 10",
        ImmutableSet.of("DEF GETPROP a.b -> EXTERN NUMBER",
                        "DEF NAME a -> EXTERN <null>"));

    checkDefinitionsInExterns(
        "var a = {}; a.b",
        ImmutableSet.of("DEF GETPROP a.b -> EXTERN <null>",
                        "DEF NAME a -> EXTERN <null>"));

    checkDefinitions(
        "var a = {}",
        "a.b = 1",
        ImmutableSet.of("DEF GETPROP a.b -> NUMBER",
                        "DEF NAME a -> EXTERN <null>",
                        "USE NAME a -> [EXTERN <null>]"));

    checkDefinitions(
        "var a = {}",
        "a.b",
        ImmutableSet.of("DEF NAME a -> EXTERN <null>",
                        "USE NAME a -> [EXTERN <null>]"));

    checkDefinitionsInExterns(
        externs,
        ImmutableSet.of("DEF NAME a -> EXTERN NUMBER"));
  }

  @Test
  public void testRecordDefinitionInExterns() {
    checkDefinitionsInExterns(
        "var ns = {};" +
        "/** @type {number} */ ns.NUM;",
        ImmutableSet.of("DEF NAME ns -> EXTERN <null>",
                        "DEF GETPROP ns.NUM -> EXTERN <null>"));

    checkDefinitionsInExterns(
        "var ns = {};" +
        "/** @type {function(T,T):number} @template T */ ns.COMPARATOR;",
        ImmutableSet.of("DEF NAME ns -> EXTERN <null>",
                        "DEF GETPROP ns.COMPARATOR -> EXTERN <null>"));

    checkDefinitionsInExterns(
        "/** @type {{ prop1 : number, prop2 : string}} */" +
        "var ns;",
        ImmutableSet.of("DEF NAME ns -> EXTERN <null>",
                        "DEF STRING_KEY null -> EXTERN <null>",
                        "DEF STRING_KEY null -> EXTERN <null>"));

    checkDefinitionsInExterns(
        "/** @typedef {{ prop1 : number, prop2 : string}} */" +
        "var ns;",
        ImmutableSet.of("DEF NAME ns -> EXTERN <null>",
                        "DEF STRING_KEY null -> EXTERN <null>",
                        "DEF STRING_KEY null -> EXTERN <null>"));
  }

  @Test
  public void testUnitializedDefinitionInExterns() {
    checkDefinitionsInExterns(
        "/** @type {number} */ var HYBRID;",
        ImmutableSet.of("DEF NAME HYBRID -> EXTERN <null>"));
  }

  @Test
  public void testObjectLitInExterns() {
    checkDefinitions(
        "var goog = {};" +
        "/** @type {number} */ goog.HYBRID;" +
        "/** @enum */ goog.Enum = {HYBRID: 0, ROADMAP: 1};",
        "goog.HYBRID; goog.Enum.ROADMAP;",
        ImmutableSet.of(
            "DEF GETPROP goog.Enum -> EXTERN <null>",
            "DEF GETPROP goog.HYBRID -> EXTERN <null>",
            "DEF NAME goog -> EXTERN <null>",
            "DEF STRING_KEY null -> EXTERN NUMBER",
            "USE GETPROP goog.Enum -> [EXTERN <null>]",
            "USE GETPROP goog.Enum.ROADMAP -> [EXTERN NUMBER]",
            "USE GETPROP goog.HYBRID -> [EXTERN <null>, EXTERN NUMBER]",
            "USE NAME goog -> [EXTERN <null>]"));
  }

  @Test
  public void testCallInExterns() {
    String externs = lines(
            "var goog = {};",
            "/** @constructor */",
            "goog.Response = function() {};",
            "goog.Response.prototype.get;",
            "goog.Response.prototype.get().get;");
    checkDefinitionsInExterns(
        externs,
        ImmutableSet.of(
            "DEF NAME goog -> EXTERN <null>",
            "DEF GETPROP goog.Response -> EXTERN FUNCTION",
            "DEF GETPROP goog.Response.prototype.get -> EXTERN <null>"));
  }

  @Test
  public void testDoubleNamedFunction() {
    String source = lines(
        "A.f = function f_d() { f_d(); };",
        "A.f();");
    checkDefinitionsInJs(
        source,
        ImmutableSet.of(
            "DEF GETPROP A.f -> FUNCTION",
            "DEF NAME f_d -> FUNCTION",
            "USE GETPROP A.f -> [FUNCTION]",
            "USE NAME f_d -> [FUNCTION]"));
  }

  @Test
  public void testGetChangesAndDeletions_changeDoesntOverrideDelete() {
    Compiler compiler = new Compiler();
    DefinitionUseSiteFinder definitionsFinder = new DefinitionUseSiteFinder(compiler);
    definitionsFinder.process(IR.root(), IR.root());

    Node script =
        compiler.parseSyntheticCode(
            lines(
                "function foo() {",
                "  foo.propOfFoo = 'asdf';",
                "}",
                "function bar() {",
                "  bar.propOfBar = 'asdf';",
                "}"));
    Node root = IR.root(script);
    Node externs = IR.root(IR.script());
    IR.root(externs, root); // Create global root.
    Node functionFoo = script.getFirstChild();
    Node functionBar = script.getSecondChild();

    // Verify original baseline.
    buildFound(definitionsFinder, found);
    assertThat(found).isEmpty();

    // Verify the fully processed state.
    compiler.getChangedScopeNodesForPass("definitionsFinder");
    compiler.getDeletedScopeNodesForPass("definitionsFinder");
    definitionsFinder = new DefinitionUseSiteFinder(compiler);
    definitionsFinder.process(externs, root);
    buildFound(definitionsFinder, found);
    assertThat(found)
        .containsExactly(
            "DEF NAME foo -> FUNCTION",
            "DEF GETPROP foo.propOfFoo -> STRING",
            "USE NAME foo -> [FUNCTION]",
            "DEF NAME bar -> FUNCTION",
            "DEF GETPROP bar.propOfBar -> STRING",
            "USE NAME bar -> [FUNCTION]");

    // Change nothing and re-verify state.
    definitionsFinder.rebuildScopeRoots(
        compiler.getChangedScopeNodesForPass("definitionsFinder"),
        compiler.getDeletedScopeNodesForPass("definitionsFinder"));
    buildFound(definitionsFinder, found);
    assertThat(found)
        .containsExactly(
            "DEF NAME foo -> FUNCTION",
            "DEF GETPROP foo.propOfFoo -> STRING",
            "USE NAME foo -> [FUNCTION]",
            "DEF NAME bar -> FUNCTION",
            "DEF GETPROP bar.propOfBar -> STRING",
            "USE NAME bar -> [FUNCTION]");

    // Verify state after deleting function "foo".
    compiler.reportFunctionDeleted(functionFoo);
    definitionsFinder.rebuildScopeRoots(
        compiler.getChangedScopeNodesForPass("definitionsFinder"),
        compiler.getDeletedScopeNodesForPass("definitionsFinder"));
    buildFound(definitionsFinder, found);
    assertThat(found)
        .containsExactly(
            "DEF NAME bar -> FUNCTION",
            "DEF GETPROP bar.propOfBar -> STRING",
            "USE NAME bar -> [FUNCTION]");

    // Verify state after changing the contents of function "bar"
    functionBar.getLastChild().removeFirstChild();
    compiler.reportChangeToChangeScope(functionBar);
    definitionsFinder.rebuildScopeRoots(
        compiler.getChangedScopeNodesForPass("definitionsFinder"),
        compiler.getDeletedScopeNodesForPass("definitionsFinder"));
    buildFound(definitionsFinder, found);
    assertThat(found).containsExactly("DEF NAME bar -> FUNCTION");
  }

  void checkDefinitionsInExterns(String externs, Set<String> expected) {
    checkDefinitions(externs, "", expected);
  }

  void checkDefinitionsInJs(String js, Set<String> expected) {
    checkDefinitions("", js, expected);
  }

  void checkDefinitions(String externs, String source, Set<String> expected) {
    testSame(externs(externs), srcs(source));
    assertEquals(expected, found);
    found.clear();
  }

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new DefinitionEnumerator(compiler);
  }

  private static void buildFound(DefinitionUseSiteFinder definitionFinder, Set<String> found) {
    found.clear();

    for (DefinitionSite defSite : definitionFinder.getDefinitionSites()) {
      Node node = defSite.node;
      Definition definition = defSite.definition;
      StringBuilder sb = new StringBuilder();
      sb.append("DEF ");
      sb.append(node.getToken());
      sb.append(" ");
      sb.append(node.getQualifiedName());
      sb.append(" -> ");

      if (definition.isExtern()) {
        sb.append("EXTERN ");
      }

      Node rValue = definition.getRValue();
      if (rValue != null) {
        sb.append(rValue.getToken());
      } else {
        sb.append("<null>");
      }

      found.add(sb.toString());
    }

    for (UseSite useSite : definitionFinder.getUseSitesByName().values()) {
      Node node = useSite.node;
      Collection<Definition> defs = definitionFinder.getDefinitionsReferencedAt(node);

      if (defs != null) {
        StringBuilder sb = new StringBuilder();
        sb.append("USE ");
        sb.append(node.getToken());
        sb.append(" ");
        sb.append(node.getQualifiedName());
        sb.append(" -> ");
        Multiset<String> defstrs = TreeMultiset.create();
        for (Definition def : defs) {
          String defstr;

          Node rValue = def.getRValue();
          if (rValue != null) {
            defstr = rValue.getToken().toString();
          } else {
            defstr = "<null>";
          }

          if (def.isExtern()) {
            defstr = "EXTERN " + defstr;
          }

          defstrs.add(defstr);
        }

        sb.append(defstrs);
        found.add(sb.toString());
      }
    }
  }

  /**
   * Run DefinitionUseSiteFinder, then gather a set of what's found.
   */
  private class DefinitionEnumerator
      extends AbstractPostOrderCallback implements CompilerPass {
    private final DefinitionUseSiteFinder passUnderTest;
    private final Compiler compiler;

    DefinitionEnumerator(Compiler compiler) {
      this.passUnderTest = new DefinitionUseSiteFinder(compiler);
      this.compiler = compiler;
    }

    @Override
    public void process(Node externs, Node root) {
      passUnderTest.process(externs, root);
      NodeTraversal.traverse(compiler, externs, this);
      NodeTraversal.traverse(compiler, root, this);

      buildFound(passUnderTest, found);
    }

    @Override
    public void visit(NodeTraversal traversal, Node node, Node parent) {}
  }
}
