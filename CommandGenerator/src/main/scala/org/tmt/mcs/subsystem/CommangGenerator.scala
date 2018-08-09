package org.tmt.mcs.subsystem

import org.zeromq.ZMQ

object CommandGenerator extends App{
  println("Welcome to the Command Generator for MCS  subsystem ")


  val zmqContext : ZMQ.Context = ZMQ.context(1)

  //val commandProcessor : CommandProcessor = CommandProcessor.create(zmqContext)
  val pullSocketPort : Int = 55578
  val pushSocketPort : Int = 55579

  val addr : String = "tcp://localhost:"
  val pullSocketAddr = addr + pullSocketPort
  val pushSocketAddr  = addr + pushSocketPort
  val commandSender  : CommandSender = CommandSender.create(zmqContext)
  commandSender.initalize(pullSocketAddr,pushSocketAddr)

  commandSender.sendCommand("Startup")
  commandSender.sendCommand("Datum")
  commandSender.sendCommand("Follow")
  commandSender.sendCommand("Point")
  commandSender.sendCommand("PointDemand")
  //commandSender.sendCommand("Shutdown")




}
