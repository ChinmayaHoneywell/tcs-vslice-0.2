package org.tmt.tcs.mcs.MCSdeploy

import csw.framework.deploy.hostconfig.HostConfig

object McsHostConfigApp extends App {

  HostConfig.start("mcs-host-config-app", args)

}
