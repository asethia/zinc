/*
 * Zinc - The incremental compiler for Scala.
 * Copyright Lightbend, Inc. and Mark Harrah
 *
 * Licensed under Apache License 2.0
 * (http://www.apache.org/licenses/LICENSE-2.0).
 *
 * See the NOTICE file distributed with this work for
 * additional information regarding copyright ownership.
 */

package sbt.inc

import java.nio.file.Path

import sbt.internal.inc._
import sbt.internal.scripted.ScriptedTest
import sbt.io.syntax._
import sbt.io.{ AllPassFilter, IO, NameFilter }

object ScriptedMain {
  private val DisableBuffering = "--no-buffer"
  private val CompileToJar = "--to-jar"
  private val Flags = Set(DisableBuffering, CompileToJar)

  def main(args: Array[String]): Unit = {
    val compileToJar = args.contains(CompileToJar)
    val disableBuffering = args.contains(DisableBuffering)
    val argsToParse = args.filterNot(Flags.contains)

    val sourceDir = BuildInfo.sourceDirectory.toPath.resolve("sbt-test")
    val tests = detectScriptedTests(sourceDir.toFile)
    val parsed = argsToParse.toList.flatMap(arg => parseScripted(tests, sourceDir, arg))
    runScripted(sourceDir, parsed, buffer = !disableBuffering, compileToJar)
  }

  private def detectScriptedTests(scriptedBase: File): Map[String, Set[String]] = {
    val scriptedFiles: NameFilter = ("test": NameFilter) | "pending"
    val pairs = (scriptedBase * AllPassFilter * AllPassFilter * scriptedFiles).get.map { f =>
      val p = f.getParentFile
      (p.getParentFile.getName, p.getName)
    }

    pairs.groupBy(_._1).mapValues(_.map(_._2).toSet)
  }

  private def parseScripted(
      testsMapping: Map[String, Set[String]],
      scriptedBase: Path,
      toParse: String
  ): Option[ScriptedTest] = {
    toParse.split("/").map(_.trim) match {
      case Array("") | Array("*") => None
      case Array("*", target)     => Some(ScriptedTest("*", target))
      case Array(directory, target) =>
        val directoryPath = scriptedBase.resolve(directory).toAbsolutePath
        testsMapping.get(directory) match {
          case Some(tests) if tests.isEmpty          => fail(s"No tests in ${directoryPath}")
          case Some(_) if target == "*"              => Some(ScriptedTest(directory, target))
          case Some(tests) if tests.contains(target) => Some(ScriptedTest(directory, target))
          case Some(_)                               => fail(s"Missing test directory ${directoryPath.resolve(target)}")
          case None                                  => fail(s"Missing parent directory ${directoryPath}")
        }
      case _ => fail("Expected only one '/' in the target scripted test(s).")
    }
  }

  def runScripted(
      baseDir: Path,
      tests: Seq[ScriptedTest],
      buffer: Boolean,
      compileToJar: Boolean
  ): Unit = {
    println(s"About to run tests: ${tests.mkString("\n * ", "\n * ", "\n")}")
    // Force Log4J to not use a thread context classloader otherwise it throws a CCE
    sys.props(org.apache.logging.log4j.util.LoaderUtil.IGNORE_TCCL_PROPERTY) = "true"
    IO.withTemporaryDirectory { tempDir =>
      // Create a global temporary directory to store the bridge et al
      val handlers = new IncScriptedHandlers(tempDir.toPath, compileToJar)
      ScriptedRunnerImpl.run(baseDir, buffer, tests, handlers, 4)
    }
  }

  private def fail(msg: String): Nothing = {
    println(msg)
    sys.exit(1)
  }
}
