/* sbt -- Simple Build Tool
 * Copyright 2010 Mark Harrah
 */
package sbt
package inc

import xsbti.api.{ Source, Compilation }
import xsbti.{ Position, Problem, Severity }
import xsbti.compile.{ CompileOrder, Output => APIOutput, SingleOutput, MultipleOutput }
import xsbti.DependencyContext._
import MultipleOutput.OutputGroup
import java.io.File
import sbinary._
import DefaultProtocol._
import DefaultProtocol.tuple2Format
import Logger.{ m2o, position, problem }
import Relations.{ Source => RSource, ClassDependencies }

@deprecated("Replaced by TextAnalysisFormat. OK to remove in 0.14.", since = "0.13.1")
object AnalysisFormats {
  type RFF = Relation[File, File]
  type RFS = Relation[File, String]

  import System.{ currentTimeMillis => now }
  val start = now
  def time(label: String) =
    {
      val end = now
      println(label + ": " + (end - start) + " ms")
    }

  def debug[T](label: String, f: Format[T]): Format[T] = new Format[T] {
    def reads(in: Input): T =
      {
        time(label + ".read.start")
        val r = f.reads(in)
        time(label + ".read.end")
        r
      }
    def writes(out: Output, t: T): Unit = {
      time(label + ".write.start")
      f.writes(out, t)
      time(label + ".write.end")
    }
  }

  implicit def analysisFormat(implicit stampsF: Format[Stamps], apisF: Format[APIs], relationsF: Format[Relations],
    infosF: Format[SourceInfos], compilationsF: Format[Compilations]): Format[Analysis] =
    asProduct5(Analysis.Empty.copy _)(a => (a.stamps, a.apis, a.relations, a.infos, a.compilations))(stampsF, apisF, relationsF, infosF, compilationsF)

  implicit def infosFormat(implicit infoF: Format[Map[File, SourceInfo]]): Format[SourceInfos] =
    wrap[SourceInfos, Map[File, SourceInfo]](_.allInfos, SourceInfos.make _)

  implicit def infoFormat: Format[SourceInfo] =
    wrap[SourceInfo, (Seq[Problem], Seq[Problem])](si => (si.reportedProblems, si.unreportedProblems), { case (a, b) => SourceInfos.makeInfo(a, b) })

  implicit def problemFormat: Format[Problem] = asProduct4(problem _)(p => (p.category, p.position, p.message, p.severity))

  implicit def compilationsFormat: Format[Compilations] = {
    implicit val compilationSeqF = seqFormat(xsbt.api.CompilationFormat)
    wrap[Compilations, Seq[Compilation]](_.allCompilations, Compilations.make _)
  }

  implicit def positionFormat: Format[Position] =
    asProduct7(position _)(p => (m2o(p.line), p.lineContent, m2o(p.offset), m2o(p.pointer), m2o(p.pointerSpace), m2o(p.sourcePath), m2o(p.sourceFile)))

  implicit val fileOptionFormat: Format[Option[File]] = optionsAreFormat[File](fileFormat)
  implicit val integerFormat: Format[Integer] = wrap[Integer, Int](_.toInt, Integer.valueOf)
  implicit val severityFormat: Format[Severity] =
    wrap[Severity, Byte](_.ordinal.toByte, b => Severity.values.apply(b.toInt))

  implicit def setupFormat(implicit outputF: Format[APIOutput], optionF: Format[CompileOptions], compilerVersion: Format[String], orderF: Format[CompileOrder], nameHashingF: Format[Boolean]): Format[CompileSetup] =
    asProduct5[CompileSetup, APIOutput, CompileOptions, String, CompileOrder, Boolean]((a, b, c, d, e) => new CompileSetup(a, b, c, d, e))(s => (s.output, s.options, s.compilerVersion, s.order, s.nameHashing))(outputF, optionF, compilerVersion, orderF, nameHashingF)

  implicit def relationsSourceFormat(implicit internalFormat: Format[Relation[File, File]], externalFormat: Format[Relation[File, String]]): Format[RSource] =
    asProduct2[RSource, RFF, RFS]((a, b) => Relations.makeSource(a, b))(rs => (rs.internal, rs.external))

  implicit def relationFormat[A, B](implicit af: Format[Map[A, Set[B]]], bf: Format[Map[B, Set[A]]]): Format[Relation[A, B]] =
    asProduct2[Relation[A, B], Map[A, Set[B]], Map[B, Set[A]]](Relation.make _)(r => (r.forwardMap, r.reverseMap))(af, bf)

  implicit val sourceFormat: Format[Source] = xsbt.api.SourceFormat

  implicit def fileFormat: Format[File] = wrap[File, String](_.getAbsolutePath, s => new File(s))
  // can't require Format[Seq[String]] because its complexity is higher than Format[CompileOptions]
  implicit def optsFormat(implicit strF: Format[String]): Format[CompileOptions] =
    wrap[CompileOptions, (Seq[String], Seq[String])](co => (co.options, co.javacOptions), os => new CompileOptions(os._1, os._2))

  implicit val orderFormat: Format[CompileOrder] =
    {
      val values = CompileOrder.values
      wrap[CompileOrder, Int](_.ordinal, values)
    }
  implicit def seqFormat[T](implicit optionFormat: Format[T]): Format[Seq[T]] = viaSeq[Seq[T], T](x => x)

  implicit def hashStampFormat: Format[Hash] = wrap[Hash, Array[Byte]](_.value, new Hash(_))
  implicit def lastModFormat: Format[LastModified] = wrap[LastModified, Long](_.value, new LastModified(_))
  implicit def existsFormat: Format[Exists] = wrap[Exists, Boolean](_.value, new Exists(_))
}
