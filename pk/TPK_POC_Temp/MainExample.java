import example2.*;


public class MainExample implements Runnable {
	//
	// Callback which is register with the C++ code and call from the fast loop
	//
	public class DemandsCallback extends IDemandsCB {
		
		double ci = 32.5;
		double ciz = 90 - ci;
		double phir = Math.PI * ci/180;
		double tci = Math.tan(ci);
		double cci = Math.cos(ci);
		double PI2 = Math.PI * 2;
		
		public void newDemands(double mountAz, double mountEl, double m3R, double m3T,
		double encAz, double encEl, double rma, double x1, double y1) {

System.out.printf("mount : %.2f, %.2f,  M3: %.2f, %.2f,  Enc : %.2f, %.2f, RMA: %.2f, Guide: %.2f, %.2f\n", 
					mountAz,  mountEl,  m3R,  m3T, encAz, encEl, rma, x1, y1);

			//
			// Convert eAz, eEl into base & cap coordinates
			//
			double azShift, base1, cap1, base2, cap2;
			if ((encEl > PI2) || (encEl < 0)) encEl = 0;
			if ((encAz > PI2) || (encAz < 0)) encAz = 0;
			
			cap1 = Math.acos(Math.tan(encEl - ciz)/tci);
			cap2 = PI2 - cap1;
			
			if (encEl == PI2) azShift = 0;
			else azShift = Math.atan(Math.sin(cap1)/cci*(1 - Math.cos(cap1)));
			
			if ((encAz + azShift) > PI2) base1 = (encAz+azShift) - PI2;
			else base1 =  encAz+azShift;
			
			if (encAz < azShift) base2 = PI2 + encAz - azShift;
			else base2 = encAz-azShift;
			
			base1 = 180 * base1 / Math.PI;
			cap1 = 180 * cap1 / Math.PI;
			// base 1 & 2 and cap 1 & 2 can be used for CW, CCW and shortest path
			// for now just base 1 and cap 1 are used
			System.out.printf("mount : %.2f, %.2f,  M3: %.2f, %.2f,  Enc : %.2f, %.2f,  Base: %.2f  Cap: %.2f  RMA: %.2f, Guide: %.2f, %.2f\n", 
					mountAz,  mountEl,  m3R,  m3T, encAz, encEl, base1,  cap1, rma, x1, y1);
			//System.out.printf("%.2f, %.2f, %.2f, %.2f, %.2f, %.2f, %.2f, %.2f, %.2f\n", 
					//mountAz,  mountEl,  m3R,  m3T, base1,  cap1, rma, x1, y1);
		}
	}

	@Override
	public void run() {
		DemandsCallback cb= new DemandsCallback();
		poc=new TpkPoc();
		poc._register(cb);
		//
		// This needs to be on a separate Java thread (which it is) if we want to take any input from 
		// the Java side since the C++ init code will go into a forever loop to keep the scans alive
		//
		poc.init();		
	}
	
	//
	// New target from Ra, Dec in degrees. Target applies to Mount and Enclosure
	//
	void newTarget(double ra, double dec) {
		poc.newTarget(ra, dec);
	}

	//
	// New mount offset. Ra, Dec offset values are in arcseconds
	//
	void offset(double raO, double decO) {
		poc.offset(raO, decO);
	}
	
	public static void main(String argv[]) throws InterruptedException {
		System.loadLibrary("example");

		MainExample main = new MainExample(); 

		new Thread(main).start();
		
		//
		// This allows commands such as newTarger etc to be accepted and processed
		//
		while (true) {
			Thread.sleep(100, 0);
			//main.newTarget(185.79, 6.753333);
			//main.offset(5, -5);
		}
	}
	
	private TpkPoc poc;
		

}
