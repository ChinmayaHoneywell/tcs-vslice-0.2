package org.tmt.tcs.mcs.MCSdeploy

import csw.framework.deploy.containercmd.ContainerCmd

object McsContainerCmdApp extends App {

  ContainerCmd.start("mcs-container-cmd-app", args)

}
