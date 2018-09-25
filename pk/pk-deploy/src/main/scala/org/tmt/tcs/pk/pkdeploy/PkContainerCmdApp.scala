package org.tmt.tcs.pk.pkdeploy

import csw.framework.deploy.containercmd.ContainerCmd

object PkContainerCmdApp extends App {

  ContainerCmd.start("pk-container-cmd-app", args)

}
