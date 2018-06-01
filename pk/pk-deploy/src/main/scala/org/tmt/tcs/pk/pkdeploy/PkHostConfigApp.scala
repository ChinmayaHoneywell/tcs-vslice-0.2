package org.tmt.tcs.pk.pkdeploy

import csw.framework.deploy.hostconfig.HostConfig

object PkHostConfigApp extends App {

  HostConfig.start("pk-host-config-app", args)

}
