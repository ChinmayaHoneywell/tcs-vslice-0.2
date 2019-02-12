package org.tmt.tcs.mcs.MCSdeploy
import scala.concurrent.ExecutionContext.Implicits.global

object MCSMainApp10 extends App {
  println("Please enter SimulationMode: SimpleSimulator or RealSimulator")
  val simulationMode = scala.io.StdIn.readLine()
  println(s"SimulatorMode selected is : $simulationMode")
  val mcsDeployer: MCSDeployHelper10 = MCSDeployHelper10.create(simulationMode)
  try {
    val resp0 = mcsDeployer.sendSimulationModeCommand()
    val resp1 = mcsDeployer.sendStartupCommand()
  } catch {
    case e: Exception =>
      e.printStackTrace()
  }

}
