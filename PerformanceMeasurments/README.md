									Demand Propogation AverageTime :- EventServ
ClientApp to Assembly	1538994045490 - 1538994045486 = 4ms	1538995679307 - 1538995679305 = 3ms	   1538996229234 - 1538996229232 = 2ms  ==> 3ms
Assembly to HCD		1538994045490 - 1538994045490 = 2ms	1538995679309 - 1538995679307 = 2ms	   1538996229235 - 1538996229234 = 1ms  ==> 1.6ms
HCD to RealSimulator	1538994045493 - 1538994045490 = 3ms	1538995679310 -  1538995679309 = 1ms	   1538996229238 - 1538996229235 = 3ms  ==> 2.33ms

ClientApp to HCD  	6ms	5ms	3ms	==> 4.66ms (viaEventService)
ClientApp to RSim 	9ms	6ms	6ms	==>  7ms
Assembly to RSim	5ms	3ms	3.9ms	==>  3.96ms	

