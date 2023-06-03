package io.joern.jssrc2cpg

import better.files.File
import better.files.File.CopyOptions
import io.joern.dataflowengineoss.layers.dataflows.{OssDataFlow, OssDataFlowOptions}
import io.joern.dataflowengineoss.slicing.{JoernTI, SliceBasedTypeInferencePass}
import io.joern.jssrc2cpg.JsSrc2Cpg.postProcessingPasses
import io.joern.jssrc2cpg.passes._
import io.joern.jssrc2cpg.utils.{AstGenRunner, Report}
import io.joern.x2cpg.X2Cpg.withNewEmptyCpg
import io.joern.x2cpg.X2CpgFrontend
import io.joern.x2cpg.passes.callgraph.NaiveCallLinker
import io.joern.x2cpg.passes.frontend.XTypeRecoveryConfig
import io.joern.x2cpg.utils.HashUtil
import io.shiftleft.codepropertygraph.Cpg
import io.shiftleft.passes.CpgPassBase
import io.shiftleft.semanticcpg.layers.LayerCreatorContext

import java.util.concurrent.TimeUnit
import scala.util.Try

class JsSrc2Cpg extends X2CpgFrontend[Config] {

  private val report: Report = new Report()

  private def nanoToMinutesAndSeconds(nanoTime: Long): (Long, Long) = {
    val min = TimeUnit.MINUTES.convert(nanoTime, TimeUnit.NANOSECONDS)
    val sec =
      TimeUnit.SECONDS.convert(nanoTime - TimeUnit.NANOSECONDS.convert(min, TimeUnit.MINUTES), TimeUnit.NANOSECONDS)
    (min, sec)
  }

  def createCpg(config: Config): Try[Cpg] = {
    withNewEmptyCpg(config.outputPath, config) { (cpg, config) =>
      File.usingTemporaryDirectory("jssrc2cpgOut") { tmpDir =>
        val baseTimeStart                     = System.nanoTime()
        implicit val copyOptions: CopyOptions = CopyOptions(overwrite = true)
        val movedTypeDeclFiles =
          config.typeDeclarationDir.map(path =>
            File(path).list.map(f => f.copyToDirectory(File(config.inputPath))).toList
          )

        val astgenResult = new AstGenRunner(config).execute(tmpDir)
        val hash         = HashUtil.sha256(astgenResult.parsedFiles.map { case (_, file) => File(file).path })

        val astCreationPass = new AstCreationPass(cpg, astgenResult, config, report)
        astCreationPass.createAndApply()

        movedTypeDeclFiles.foreach(_.foreach(_.delete(swallowIOExceptions = true)))

        new TypeNodePass(astCreationPass.allUsedTypes(), cpg).createAndApply()
        new JsMetaDataPass(cpg, hash, config.inputPath).createAndApply()
        new BuiltinTypesPass(cpg).createAndApply()
        new DependenciesPass(cpg, config).createAndApply()
        new ConfigPass(cpg, config, report).createAndApply()
        new PrivateKeyFilePass(cpg, config, report).createAndApply()
        new ImportsPass(cpg).createAndApply()
        val (minutes, seconds) = nanoToMinutesAndSeconds(System.nanoTime() - baseTimeStart)
        println(s"Base JS pass ran for ${minutes}m and ${seconds}s")
        report.print()
      }
    }
  }

  // This method is intended for internal use only and may be removed at any time.
  def createCpgWithAllOverlays(config: Config): Try[Cpg] = {
    val maybeCpg = createCpgWithOverlays(config)
    maybeCpg.map { cpg =>
      new OssDataFlow(new OssDataFlowOptions()).run(new LayerCreatorContext(cpg))
      postProcessingPasses(cpg, Option(config)).foreach(_.createAndApply())
      cpg
    }
  }

}

object JsSrc2Cpg {

  def postProcessingPasses(cpg: Cpg, config: Option[Config] = None): List[CpgPassBase] = {
    val typeRecConfig = XTypeRecoveryConfig(enabledDummyTypes = !config.exists(_.disableDummyTypes))
    List(
      new JavaScriptInheritanceNamePass(cpg),
      new ConstClosurePass(cpg),
      new JavaScriptTypeRecoveryPass(cpg, typeRecConfig)
    ) ++
      (if (config.exists(_.joernti)) {
         List(
           new SliceBasedTypeInferencePass(cpg, Try(new JoernTI(spawnProcess = false)).toOption),
           new JavaScriptTypeRecoveryPass(cpg, typeRecConfig.copy(iterations = 1))
         )
       } else {
         List.empty
       }) ++
      List(new JavaScriptTypeHintCallLinker(cpg), new NaiveCallLinker(cpg))
  }

}
